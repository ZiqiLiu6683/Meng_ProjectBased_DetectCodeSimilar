package com.ziqi.codesim.region.semantic;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ziqi.codesim.region.grow.AlignedPair;
import com.ziqi.codesim.region.grow.RegionGroup;
import com.ziqi.codesim.region.model.SemanticGraph;
import com.ziqi.codesim.region.model.SemanticNode;
import com.ziqi.codesim.semantic.backend.AnalysisException;
import com.ziqi.codesim.semantic.backend.wala.WalaClassHierarchies;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Phase B, region-facing: proves whether the two sides of a Phase A region group compute the same
 * outputs for matched inputs. Turns a POSSIBLE_T4 structural region into a CONFIRMED Type-4 when the
 * SMT proof succeeds. First slice: same-method regions of integer arithmetic (cross-method regions,
 * or regions with in-region calls/loops, return UNKNOWN).
 *
 * <p>Uses {@link RegionSummarizer} to express each region's outputs over its inputs, matches the two
 * sides' outputs (via aligned node SSA values) and inputs (via aligned instruction operand
 * positions), then SMT-checks each aligned output pair with matched inputs shared.
 */
public final class RegionEquivalenceVerifier {

    private final RegionSummarizer summarizer = new RegionSummarizer();
    private final SmtEquivalenceChecker checker = new SmtEquivalenceChecker();

    /** Method IR + def-use, indexed by method signature. */
    public record MethodIr(IR ir, DefUse defUse) {
    }

    /** Build the IR index for one file's compiled classes (reused across many region verifications). */
    public static Map<String, MethodIr> methodIrs(Path classesDir) throws AnalysisException {
        ClassHierarchy hierarchy = WalaClassHierarchies.build(classesDir);
        AnalysisCacheImpl cache = new AnalysisCacheImpl();
        Map<String, MethodIr> index = new HashMap<>();
        for (IClass clazz : hierarchy) {
            if (!ClassLoaderReference.Application.equals(clazz.getClassLoader().getReference())) {
                continue;
            }
            for (IMethod method : clazz.getDeclaredMethods()) {
                if (method.isAbstract() || method.isNative() || method.isSynthetic()) {
                    continue;
                }
                IR ir = cache.getIR(method);
                if (ir == null || ir.isEmptyIR()) {
                    continue;
                }
                index.put(method.getSignature(), new MethodIr(ir, new DefUse(ir)));
            }
        }
        return index;
    }

    public EquivalenceVerdict verify(RegionGroup region, SemanticGraph leftGraph, SemanticGraph rightGraph,
                                     Map<String, MethodIr> leftIrs, Map<String, MethodIr> rightIrs) {
        if (region.leftMethods().size() != 1 || region.rightMethods().size() != 1) {
            return EquivalenceVerdict.UNKNOWN; // cross-method region: deferred
        }
        MethodIr left = leftIrs.get(region.leftMethods().iterator().next());
        MethodIr right = rightIrs.get(region.rightMethods().iterator().next());
        if (left == null || right == null) {
            return EquivalenceVerdict.UNKNOWN;
        }

        Set<Integer> leftInRegion = inRegionValues(region, leftGraph, true);
        Set<Integer> rightInRegion = inRegionValues(region, rightGraph, false);
        if (leftInRegion.isEmpty() || rightInRegion.isEmpty()) {
            return EquivalenceVerdict.UNKNOWN;
        }

        Map<Integer, SymbolicExpression> leftOutputs = summarizer.summarize(left.ir(), left.defUse(), leftInRegion);
        Map<Integer, SymbolicExpression> rightOutputs = summarizer.summarize(right.ir(), right.defUse(), rightInRegion);
        if (leftOutputs.isEmpty()) {
            return EquivalenceVerdict.UNKNOWN;
        }

        Map<Integer, Integer> outputAlignment = definedValueAlignment(region, leftGraph, rightGraph);
        Map<Integer, Integer> rightInputToLeft = inputAlignment(region, leftGraph, rightGraph,
                left.defUse(), right.defUse(), leftInRegion, rightInRegion);

        boolean anyChecked = false;
        for (Map.Entry<Integer, SymbolicExpression> leftOutput : leftOutputs.entrySet()) {
            Integer rightValue = outputAlignment.get(leftOutput.getKey());
            if (rightValue == null) {
                return EquivalenceVerdict.UNKNOWN; // an output not matched across sides
            }
            SymbolicExpression rightExpr = rightOutputs.get(rightValue);
            if (rightExpr == null) {
                return EquivalenceVerdict.UNKNOWN;
            }
            EquivalenceVerdict verdict = checker.checkRegionOutputs(
                    leftOutput.getValue(), rightExpr, rightInputToLeft);
            if (verdict == EquivalenceVerdict.DIFFERENT) {
                return EquivalenceVerdict.DIFFERENT;
            }
            if (verdict != EquivalenceVerdict.EQUIVALENT) {
                return EquivalenceVerdict.UNKNOWN;
            }
            anyChecked = true;
        }
        return anyChecked ? EquivalenceVerdict.EQUIVALENT : EquivalenceVerdict.UNKNOWN;
    }

    private static Set<Integer> inRegionValues(RegionGroup region, SemanticGraph graph, boolean leftSide) {
        Set<Integer> values = new HashSet<>();
        for (AlignedPair pair : region.alignment()) {
            int nodeId = leftSide ? pair.leftNodeId() : pair.rightNodeId();
            graph.node(nodeId).ifPresent(node -> {
                if (node.definedValue() >= 0) {
                    values.add(node.definedValue());
                }
            });
        }
        return values;
    }

    /** Left defined-value -> right defined-value, for each aligned pair that defines a value on both. */
    private static Map<Integer, Integer> definedValueAlignment(RegionGroup region,
                                                               SemanticGraph leftGraph, SemanticGraph rightGraph) {
        Map<Integer, Integer> alignment = new LinkedHashMap<>();
        for (AlignedPair pair : region.alignment()) {
            SemanticNode left = leftGraph.node(pair.leftNodeId()).orElse(null);
            SemanticNode right = rightGraph.node(pair.rightNodeId()).orElse(null);
            if (left != null && right != null && left.definedValue() >= 0 && right.definedValue() >= 0) {
                alignment.put(left.definedValue(), right.definedValue());
            }
        }
        return alignment;
    }

    /**
     * Right region-input value -> left region-input value, derived from aligned instruction operands:
     * if the aligned left/right instructions use an out-of-region value at the same operand position,
     * those two inputs are the same logical variable.
     */
    private static Map<Integer, Integer> inputAlignment(RegionGroup region,
                                                        SemanticGraph leftGraph, SemanticGraph rightGraph,
                                                        DefUse leftDefUse, DefUse rightDefUse,
                                                        Set<Integer> leftInRegion, Set<Integer> rightInRegion) {
        Map<Integer, Integer> rightInputToLeft = new HashMap<>();
        for (AlignedPair pair : region.alignment()) {
            SemanticNode left = leftGraph.node(pair.leftNodeId()).orElse(null);
            SemanticNode right = rightGraph.node(pair.rightNodeId()).orElse(null);
            if (left == null || right == null || left.definedValue() < 0 || right.definedValue() < 0) {
                continue;
            }
            SSAInstruction leftInstr = leftDefUse.getDef(left.definedValue());
            SSAInstruction rightInstr = rightDefUse.getDef(right.definedValue());
            if (leftInstr == null || rightInstr == null
                    || leftInstr.getNumberOfUses() != rightInstr.getNumberOfUses()) {
                continue;
            }
            for (int i = 0; i < leftInstr.getNumberOfUses(); i++) {
                int leftUse = leftInstr.getUse(i);
                int rightUse = rightInstr.getUse(i);
                boolean leftIsInput = !leftInRegion.contains(leftUse);
                boolean rightIsInput = !rightInRegion.contains(rightUse);
                if (leftIsInput && rightIsInput) {
                    rightInputToLeft.put(rightUse, leftUse);
                }
            }
        }
        return rightInputToLeft;
    }
}
