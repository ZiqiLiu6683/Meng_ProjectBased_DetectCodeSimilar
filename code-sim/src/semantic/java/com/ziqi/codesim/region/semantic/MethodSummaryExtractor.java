package com.ziqi.codesim.region.semantic;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.ClassLoaderReference;
import com.ziqi.codesim.region.semantic.SymbolicExpression.BinaryOperation;
import com.ziqi.codesim.region.semantic.SymbolicExpression.Constant;
import com.ziqi.codesim.region.semantic.SymbolicExpression.Parameter;
import com.ziqi.codesim.region.semantic.SymbolicExpression.Unknown;
import com.ziqi.codesim.semantic.backend.AnalysisException;
import com.ziqi.codesim.semantic.backend.wala.WalaClassHierarchies;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * Phase B summary extraction: turns each application method into a {@link SymbolicExpression} for
 * its returned value, in terms of its parameters. WALA-typed. Straight-line, single-return integer
 * methods are modelled exactly; anything else (loops/phi, multiple returns, non-integer values)
 * yields {@link Unknown}.
 *
 * <p>Method calls are <b>inlined</b>: at a call site the callee is resolved, summarized, and its
 * parameters are substituted with the caller's actual-argument expressions -- so a helper-extraction
 * clone ({@code g(y)=h(y)+1}, {@code h(y)=y*2}) summarizes to {@code 2y+1} and can be proven
 * equivalent to the inlined form. Inlining is bounded (depth + recursion guard) and only follows
 * unique application (non-abstract) targets; anything else stays {@link Unknown}.
 */
public final class MethodSummaryExtractor {

    private static final int MAX_EXPRESSION_DEPTH = 32;
    private static final int MAX_INLINE_DEPTH = 5;

    public Map<String, SymbolicExpression> extractAll(Path classesDir) throws AnalysisException {
        return extractAll(classesDir, List.of());
    }

    public Map<String, SymbolicExpression> extractAll(Path classesDir, List<Path> supportClasspath)
            throws AnalysisException {
        ClassHierarchy hierarchy = WalaClassHierarchies.build(classesDir, supportClasspath);
        AnalysisCacheImpl cache = new AnalysisCacheImpl();
        Summarizer summarizer = new Summarizer(hierarchy, cache);
        Map<String, SymbolicExpression> summaries = new LinkedHashMap<>();
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
                summaries.put(method.getSignature(), summarizer.summaryOf(method));
            }
        }
        return summaries;
    }

    /** Holds the hierarchy/cache so call sites can resolve + summarize callees for inlining. */
    private static final class Summarizer {
        private final ClassHierarchy hierarchy;
        private final AnalysisCacheImpl cache;

        Summarizer(ClassHierarchy hierarchy, AnalysisCacheImpl cache) {
            this.hierarchy = hierarchy;
            this.cache = cache;
        }

        SymbolicExpression summaryOf(IMethod method) {
            return summarize(method, new HashSet<>(), 0);
        }

        private SymbolicExpression summarize(IMethod method, Set<IMethod> inlining, int inlineDepth) {
            if (inlineDepth > MAX_INLINE_DEPTH || inlining.contains(method)) {
                return new Unknown("inline depth limit or recursion");
            }
            IR ir;
            try {
                ir = cache.getIR(method);
            } catch (RuntimeException ex) {
                return new Unknown("no IR");
            }
            if (ir == null || ir.isEmptyIR()) {
                return new Unknown("no IR");
            }
            SymbolTable symbolTable = ir.getSymbolTable();
            DefUse defUse = new DefUse(ir);
            Map<Integer, Integer> parameterPosition = new HashMap<>();
            int[] params = symbolTable.getParameterValueNumbers();
            for (int i = 0; i < params.length; i++) {
                parameterPosition.put(params[i], i);
            }

            int returnValue = -1;
            int returnCount = 0;
            for (SSAInstruction instruction : ir.getInstructions()) {
                if (instruction instanceof SSAReturnInstruction ret && ret.getNumberOfUses() > 0) {
                    returnCount++;
                    returnValue = ret.getUse(0);
                }
            }
            if (returnCount == 0) {
                return new Unknown("no value return (void)");
            }
            if (returnCount > 1) {
                return new Unknown("multiple returns");
            }
            // Add this method to the inlining set so a callee that calls back here is caught as
            // recursion (and left Unknown) rather than inlined forever.
            Set<IMethod> inliningWithSelf = new HashSet<>(inlining);
            inliningWithSelf.add(method);
            return valueExpression(returnValue, symbolTable, defUse, parameterPosition,
                    inliningWithSelf, inlineDepth, 0, new HashSet<>());
        }

        private SymbolicExpression valueExpression(int value, SymbolTable symbolTable, DefUse defUse,
                                                   Map<Integer, Integer> parameterPosition,
                                                   Set<IMethod> inlining, int inlineDepth,
                                                   int exprDepth, Set<Integer> visiting) {
            if (value < 0) {
                return new Unknown("void value");
            }
            if (symbolTable.isConstant(value)) {
                if (symbolTable.isNullConstant(value)) {
                    return new Unknown("null constant");
                }
                Object constant = symbolTable.getConstantValue(value);
                if (constant instanceof Integer || constant instanceof Long) {
                    return new Constant(((Number) constant).longValue());
                }
                if (constant instanceof Boolean bool) {
                    return new Constant(bool ? 1L : 0L);
                }
                return new Unknown("non-integer constant");
            }
            Integer position = parameterPosition.get(value);
            if (position != null) {
                return new Parameter(position);
            }
            if (exprDepth >= MAX_EXPRESSION_DEPTH || !visiting.add(value)) {
                return new Unknown("depth limit or cycle (loop)");
            }
            SSAInstruction def = defUse.getDef(value);
            SymbolicExpression result;
            if (def instanceof SSABinaryOpInstruction binaryOp) {
                String operator = binaryOp.getOperator().toString().toLowerCase(Locale.ROOT);
                SymbolicExpression left = valueExpression(binaryOp.getUse(0), symbolTable, defUse,
                        parameterPosition, inlining, inlineDepth, exprDepth + 1, visiting);
                SymbolicExpression right = valueExpression(binaryOp.getUse(1), symbolTable, defUse,
                        parameterPosition, inlining, inlineDepth, exprDepth + 1, visiting);
                result = new BinaryOperation(operator, left, right);
            } else if (def instanceof SSAAbstractInvokeInstruction invoke) {
                result = inlineCall(invoke, symbolTable, defUse, parameterPosition,
                        inlining, inlineDepth, exprDepth, visiting);
            } else {
                result = new Unknown(def == null ? "no defining instruction" : def.getClass().getSimpleName());
            }
            visiting.remove(value);
            return result;
        }

        /** Replace a call by the callee's summary with actual arguments substituted for its params. */
        private SymbolicExpression inlineCall(SSAAbstractInvokeInstruction invoke, SymbolTable symbolTable,
                                              DefUse defUse, Map<Integer, Integer> parameterPosition,
                                              Set<IMethod> inlining, int inlineDepth,
                                              int exprDepth, Set<Integer> visiting) {
            IMethod callee = hierarchy.resolveMethod(invoke.getDeclaredTarget());
            if (callee == null || callee.isAbstract()
                    || !ClassLoaderReference.Application.equals(
                            callee.getDeclaringClass().getClassLoader().getReference())) {
                return new Unknown("call not inlinable (unresolved/abstract/external)");
            }

            // `inlining` already carries this method (added in summarize), so a callee that leads
            // back here is caught by the recursion guard.
            SymbolicExpression calleeSummary = summarize(callee, inlining, inlineDepth + 1);
            if (calleeSummary.hasUnknown()) {
                return new Unknown("callee summary unknown");
            }

            // Callee parameter position i corresponds to the invoke's use i (receiver = 0 for
            // instance methods, then the actual arguments).
            Map<Integer, SymbolicExpression> argumentByParameter = new HashMap<>();
            for (int i = 0; i < invoke.getNumberOfUses(); i++) {
                argumentByParameter.put(i, valueExpression(invoke.getUse(i), symbolTable, defUse,
                        parameterPosition, inlining, inlineDepth, exprDepth + 1, visiting));
            }
            return substitute(calleeSummary, argumentByParameter);
        }

        private SymbolicExpression substitute(SymbolicExpression expression,
                                              Map<Integer, SymbolicExpression> argumentByParameter) {
            if (expression instanceof Parameter parameter) {
                SymbolicExpression argument = argumentByParameter.get(parameter.index());
                return argument != null ? argument : new Unknown("unbound parameter " + parameter.index());
            }
            if (expression instanceof BinaryOperation operation) {
                return new BinaryOperation(operation.operator(),
                        substitute(operation.left(), argumentByParameter),
                        substitute(operation.right(), argumentByParameter));
            }
            return expression;
        }
    }
}
