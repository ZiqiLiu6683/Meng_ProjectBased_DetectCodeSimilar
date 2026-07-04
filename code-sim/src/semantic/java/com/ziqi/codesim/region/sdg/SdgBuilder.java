package com.ziqi.codesim.region.sdg;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.ClassLoaderReference;
import com.ziqi.codesim.region.model.EdgeKind;
import com.ziqi.codesim.region.model.NodeKind;
import com.ziqi.codesim.region.model.SemanticEdge;
import com.ziqi.codesim.region.model.SemanticGraph;
import com.ziqi.codesim.region.model.SemanticNode;
import com.ziqi.codesim.region.model.SourceSpan;
import com.ziqi.codesim.semantic.backend.AnalysisException;
import com.ziqi.codesim.semantic.model.InstructionCategory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The single WALA adapter for Phase A: builds one file's System Dependence Graph and lifts it into
 * our WALA-free {@link SemanticGraph}. Nothing outside this class touches WALA types, so any WALA
 * API drift only has to be fixed here.
 *
 * <p>Pipeline inside {@link #build}:
 * <ol>
 *   <li>compiled classes dir → {@link AnalysisScope} + class hierarchy;</li>
 *   <li>every application method as an entry point → 0-CFA call graph + pointer analysis;</li>
 *   <li>WALA {@link SDG} with configurable data/control dependence options;</li>
 *   <li>keep only application-scope statements, convert each to a {@link SemanticNode} with a
 *       source line span, and lift dependence edges into typed {@link SemanticEdge}s.</li>
 * </ol>
 *
 * <p>Defaults: data dependences {@code NO_HEAP} (explicit def-use only, heap dependences dropped to
 * cut noise) and control dependences {@code NO_EXCEPTIONAL_EDGES}. Both are exposed in the
 * constructor — they are the real accuracy knob for this stage, not a manual block plan.
 *
 * <p>Requires the input classes to be compiled with {@code -g} (line numbers) for source mapping.
 */
public final class SdgBuilder {

    private final Slicer.DataDependenceOptions dataOptions;
    private final Slicer.ControlDependenceOptions controlOptions;

    public SdgBuilder() {
        this(Slicer.DataDependenceOptions.NO_HEAP, Slicer.ControlDependenceOptions.NO_EXCEPTIONAL_EDGES);
    }

    public SdgBuilder(Slicer.DataDependenceOptions dataOptions,
                      Slicer.ControlDependenceOptions controlOptions) {
        this.dataOptions = dataOptions;
        this.controlOptions = controlOptions;
    }

    /**
     * @param classesDir directory of {@code .class} files for one input file (compiled with -g)
     * @param fileLabel  human-readable label for provenance (e.g. the source file name)
     */
    public SemanticGraph build(Path classesDir, String fileLabel) throws AnalysisException {
        try {
            AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(
                    classesDir.toAbsolutePath().toString(), null);
            IClassHierarchy cha = ClassHierarchyFactory.makeWithPhantom(scope);

            Iterable<Entrypoint> entrypoints = new AllApplicationEntrypoints(scope, cha);
            AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
            options.setReflectionOptions(AnalysisOptions.ReflectionOptions.NONE);
            AnalysisCacheImpl cache = new AnalysisCacheImpl();

            CallGraphBuilder<InstanceKey> builder =
                    Util.makeZeroCFABuilder(Language.JAVA, options, cache, cha, scope);
            CallGraph callGraph = builder.makeCallGraph(options, null);
            PointerAnalysis<InstanceKey> pointerAnalysis = builder.getPointerAnalysis();

            SDG<InstanceKey> sdg = new SDG<>(callGraph, pointerAnalysis, dataOptions, controlOptions);
            return lift(sdg, callGraph, fileLabel);
        } catch (Exception ex) {
            throw new AnalysisException("Failed to build SDG for: " + fileLabel, ex);
        }
    }

    /** Convert the WALA SDG into our representation, keeping only application-scope statements. */
    private SemanticGraph lift(SDG<InstanceKey> sdg, CallGraph callGraph, String fileLabel) {
        SemanticGraph.Builder graph = SemanticGraph.builder(fileLabel);
        // Content-equality map (NOT IdentityHashMap): WALA returns content-equal-but-distinct
        // Statement instances from iterator() vs getSuccNodes(), especially for the interprocedural
        // ParamCaller->ParamCallee edges. Statement subclasses implement equals/hashCode by
        // (cgNode, instructionIndex/valueNumber, kind), so a HashMap matches them correctly; an
        // identity map silently drops every cross-method edge.
        Map<Statement, Integer> idByStatement = new HashMap<>();
        // One symbolic hasher per method (IR/SymbolTable/DefUse are method-scoped), built lazily.
        Map<CGNode, SymbolicHasher> hashers = new HashMap<>();

        int nextId = 0;
        for (Statement statement : sdg) {
            if (!isApplication(statement)) {
                continue;
            }
            int id = nextId++;
            idByStatement.put(statement, id);
            long semanticValueHash = 0L;
            if (statement instanceof NormalStatement normal && normal.getInstruction() != null) {
                SymbolicHasher hasher = hashers.computeIfAbsent(
                        statement.getNode(), SymbolicHasher::forNode);
                semanticValueHash = hasher.instructionHash(normal.getInstruction());
            }
            graph.addNode(toNode(id, statement, callGraph, semanticValueHash));
        }

        for (Statement from : sdg) {
            Integer fromId = idByStatement.get(from);
            if (fromId == null) {
                continue;
            }
            for (Statement to : iterable(sdg.getSuccNodes(from))) {
                Integer toId = idByStatement.get(to);
                if (toId == null) {
                    continue;
                }
                graph.addEdge(new SemanticEdge(fromId, toId, classifyEdge(from, to, callGraph)));
            }
        }
        return graph.build();
    }

    private static SemanticNode toNode(int id, Statement statement, CallGraph callGraph,
                                       long semanticValueHash) {
        CGNode cgNode = statement.getNode();
        int cgNodeId = callGraph.getNumber(cgNode);
        String methodSignature = cgNode.getMethod().getSignature();
        String walaKind = statement.getKind().name();
        NodeKind kind = nodeKind(walaKind);

        SourceSpan source = SourceSpan.SYNTHETIC;
        String instructionText = "";
        InstructionCategory operation = InstructionCategory.OTHER;
        String operationToken;
        int definedValue = -1;
        if (statement instanceof NormalStatement normal && normal.getInstruction() != null) {
            SSAInstruction instruction = normal.getInstruction();
            instructionText = instruction.toString();
            source = sourceLine(cgNode.getMethod(), normal.getInstructionIndex());
            operation = categoryOf(instruction);
            operationToken = operationToken(instruction, operation);
            definedValue = instruction.hasDef() ? instruction.getDef(0) : -1;
        } else {
            operationToken = pseudoToken(kind);
        }
        return new SemanticNode(id, cgNodeId, methodSignature, kind, walaKind, operation,
                operationToken, semanticValueHash, definedValue, source, instructionText);
    }

    /**
     * Name-free operation token: the WL base label. Operators and call scope are kept; variable
     * names, SSA value numbers, and exact callee names are intentionally excluded so structurally
     * equal code matches regardless of identifiers. (Type/API matching via class-hierarchy LUB is
     * the separate semantic channel added later.)
     */
    private static String operationToken(SSAInstruction instruction, InstructionCategory category) {
        if (instruction instanceof SSABinaryOpInstruction binaryOp) {
            return "binaryop:" + binaryOp.getOperator().toString().toLowerCase(Locale.ROOT);
        }
        if (instruction instanceof SSAComparisonInstruction comparison) {
            return "cmp:" + comparison.getOperator().toString().toLowerCase(Locale.ROOT);
        }
        if (instruction instanceof SSAConditionalBranchInstruction conditional) {
            return "cond:" + conditional.getOperator().toString().toLowerCase(Locale.ROOT);
        }
        if (instruction instanceof SSAAbstractInvokeInstruction invoke) {
            return "invoke:" + invokeScope(invoke);
        }
        if (instruction instanceof SSAReturnInstruction returnInstruction) {
            return returnInstruction.getNumberOfUses() == 0 ? "return:void" : "return:value";
        }
        return category.name().toLowerCase(Locale.ROOT);
    }

    private static String invokeScope(SSAAbstractInvokeInstruction invoke) {
        ClassLoaderReference loader = invoke.getDeclaredTarget().getDeclaringClass().getClassLoader();
        return ClassLoaderReference.Application.equals(loader) ? "internal" : "external";
    }

    private static String pseudoToken(NodeKind kind) {
        return switch (kind) {
            case PARAM -> "param";
            case RETURN -> "return";
            case HEAP -> "heap";
            case PHI -> "phi";
            case EXCEPTION -> "exc";
            case STATEMENT, OTHER -> "other";
        };
    }

    // Mirrors the discovRE instruction-category vocabulary already used by WalaAnalysisBackend, so
    // the region module and the legacy static-CFG module classify instructions the same way.
    private static InstructionCategory categoryOf(SSAInstruction instruction) {
        if (instruction instanceof SSAConditionalBranchInstruction || instruction instanceof SSAGotoInstruction) {
            return InstructionCategory.BRANCH;
        }
        if (instruction instanceof SSAReturnInstruction) {
            return InstructionCategory.RETURN;
        }
        if (instruction instanceof SSAAbstractInvokeInstruction) {
            return InstructionCategory.CALL;
        }
        if (instruction instanceof SSABinaryOpInstruction binaryOp) {
            return isLogicOperator(binaryOp) ? InstructionCategory.LOGIC : InstructionCategory.ARITHMETIC;
        }
        if (instruction instanceof SSAComparisonInstruction) {
            return InstructionCategory.COMPARISON;
        }
        if (instruction instanceof SSANewInstruction) {
            return InstructionCategory.ALLOCATION;
        }
        if (instruction instanceof SSAPutInstruction) {
            return InstructionCategory.ASSIGNMENT;
        }
        if (instruction instanceof SSAFieldAccessInstruction) {
            return InstructionCategory.FIELD_ACCESS;
        }
        if (instruction instanceof SSAArrayReferenceInstruction) {
            return InstructionCategory.ARRAY_ACCESS;
        }
        if (instruction.hasDef()) {
            return InstructionCategory.ASSIGNMENT;
        }
        return InstructionCategory.OTHER;
    }

    private static boolean isLogicOperator(SSABinaryOpInstruction instruction) {
        String operator = instruction.getOperator().toString().toLowerCase(Locale.ROOT);
        return operator.equals("and") || operator.equals("or") || operator.equals("xor")
                || operator.equals("shl") || operator.equals("shr") || operator.equals("ushr");
    }

    /** Map an SSA instruction index back to a source line via the bytecode line table (needs -g). */
    private static SourceSpan sourceLine(IMethod method, int instructionIndex) {
        if (!(method instanceof IBytecodeMethod<?> bytecodeMethod)) {
            return SourceSpan.SYNTHETIC;
        }
        try {
            int bytecodeIndex = bytecodeMethod.getBytecodeIndex(instructionIndex);
            int line = bytecodeMethod.getLineNumber(bytecodeIndex);
            return SourceSpan.ofLine(line);
        } catch (Exception ex) {
            // getBytecodeIndex throws a checked WALA exception whose package moved across versions
            // (com.ibm.wala.shrike.shrikeCT in 1.7.1); catching broadly keeps this version-robust.
            // Any failure to resolve a line just means "no source mapping" -> synthetic.
            return SourceSpan.SYNTHETIC;
        }
    }

    private static NodeKind nodeKind(String walaKind) {
        if (walaKind.equals("NORMAL")) {
            return NodeKind.STATEMENT;
        }
        if (walaKind.startsWith("HEAP")) {
            return NodeKind.HEAP;
        }
        if (walaKind.contains("PARAM")) {
            return NodeKind.PARAM;
        }
        if (walaKind.contains("RET")) {
            return walaKind.startsWith("EXC") ? NodeKind.EXCEPTION : NodeKind.RETURN;
        }
        if (walaKind.equals("PHI") || walaKind.equals("PI")) {
            return NodeKind.PHI;
        }
        if (walaKind.equals("CATCH")) {
            return NodeKind.EXCEPTION;
        }
        return NodeKind.OTHER;
    }

    /**
     * Interprocedural edges are classified from the endpoint statement kinds (reliable);
     * intraprocedural edges are emitted as {@link EdgeKind#DEPENDENCE} in M1 (control-or-data,
     * split out later).
     */
    private static EdgeKind classifyEdge(Statement from, Statement to, CallGraph callGraph) {
        int fromCg = callGraph.getNumber(from.getNode());
        int toCg = callGraph.getNumber(to.getNode());
        if (fromCg == toCg) {
            return EdgeKind.DEPENDENCE;
        }
        String fk = from.getKind().name();
        String tk = to.getKind().name();
        if (tk.endsWith("PARAM_CALLEE")) {
            return EdgeKind.PARAM_IN;
        }
        if (fk.endsWith("RET_CALLEE")) {
            return EdgeKind.RETURN;
        }
        if (tk.endsWith("RET_CALLER")) {
            return EdgeKind.PARAM_OUT;
        }
        return EdgeKind.CALL;
    }

    private static boolean isApplication(Statement statement) {
        return ClassLoaderReference.Application.equals(
                statement.getNode().getMethod().getDeclaringClass().getClassLoader().getReference());
    }

    private static <T> Iterable<T> iterable(Iterator<T> iterator) {
        return () -> iterator;
    }

    private static long fnv1a(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix(long a, long b) {
        long h = a ^ (b + 0x9e3779b97f4a7c15L + (a << 6) + (a >>> 2));
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return h;
    }

    /**
     * Computes a normalized, name-free hash of the SSA symbolic expression a value (or instruction)
     * computes, within one method. Commutative operands are sorted, constants are hashed by value,
     * and parameters by position -- so {@code x*2} and {@code 2*x} match but {@code x*2} and
     * {@code x*3} do not (the latter is exactly what the WL channel misses, since the constant is
     * not a graph node). Bounded depth + a visiting guard keep recursion finite across phi/loops.
     * This is the seed-level semantic approximation; proving real equivalence is Phase B's SMT job.
     */
    private static final class SymbolicHasher {
        private static final int MAX_DEPTH = 12;
        private static final Set<String> COMMUTATIVE = Set.of("add", "mul", "and", "or", "xor");

        private final IR ir;
        private final SymbolTable symbolTable;
        private final DefUse defUse;
        private final Map<Integer, Integer> parameterPosition = new HashMap<>();

        private SymbolicHasher(IR ir) {
            this.ir = ir;
            this.symbolTable = ir == null ? null : ir.getSymbolTable();
            this.defUse = ir == null ? null : new DefUse(ir);
            if (ir != null) {
                int[] params = symbolTable.getParameterValueNumbers();
                for (int i = 0; i < params.length; i++) {
                    parameterPosition.put(params[i], i);
                }
            }
        }

        static SymbolicHasher forNode(CGNode node) {
            IR ir;
            try {
                ir = node.getIR();
            } catch (RuntimeException ex) {
                ir = null;
            }
            return new SymbolicHasher(ir);
        }

        long instructionHash(SSAInstruction instruction) {
            if (ir == null) {
                return 0L;
            }
            if (instruction.hasDef()) {
                return valueHash(instruction.getDef(0), 0, new HashSet<>());
            }
            if (instruction instanceof SSAReturnInstruction returnInstruction) {
                if (returnInstruction.getNumberOfUses() == 0) {
                    return 0L;
                }
                return mix(fnv1a("return"), valueHash(returnInstruction.getUse(0), 0, new HashSet<>()));
            }
            if (instruction instanceof SSAConditionalBranchInstruction conditional) {
                long token = fnv1a("cond:" + conditional.getOperator().toString().toLowerCase(Locale.ROOT));
                // Comparisons are not commutative (le != ge), so operand order is preserved.
                long left = valueHash(conditional.getUse(0), 0, new HashSet<>());
                long right = valueHash(conditional.getUse(1), 0, new HashSet<>());
                return mix(mix(token, left), right);
            }
            return 0L;
        }

        private long valueHash(int value, int depth, Set<Integer> visiting) {
            if (value < 0) {
                return fnv1a("void");
            }
            if (symbolTable.isConstant(value)) {
                if (symbolTable.isNullConstant(value)) {
                    return fnv1a("const:null");
                }
                Object constant = symbolTable.getConstantValue(value);
                String type = constant == null ? "?" : constant.getClass().getSimpleName();
                return fnv1a("const:" + type + ":" + constant);
            }
            Integer position = parameterPosition.get(value);
            if (position != null) {
                return fnv1a("param:" + position);
            }
            if (depth >= MAX_DEPTH || !visiting.add(value)) {
                return fnv1a("opaque");
            }
            SSAInstruction def = defUse.getDef(value);
            long result = def == null ? fnv1a("leaf") : defHash(def, depth, visiting);
            visiting.remove(value);
            return result;
        }

        private long defHash(SSAInstruction def, int depth, Set<Integer> visiting) {
            long hash = fnv1a("op:" + operationToken(def, categoryOf(def)));
            int uses = def.getNumberOfUses();
            long[] children = new long[uses];
            for (int i = 0; i < uses; i++) {
                children[i] = valueHash(def.getUse(i), depth + 1, visiting);
            }
            if (def instanceof SSABinaryOpInstruction binaryOp && children.length == 2
                    && COMMUTATIVE.contains(binaryOp.getOperator().toString().toLowerCase(Locale.ROOT))
                    && children[0] > children[1]) {
                long swap = children[0];
                children[0] = children[1];
                children[1] = swap;
            }
            for (long child : children) {
                hash = mix(hash, child);
            }
            return hash;
        }
    }
}
