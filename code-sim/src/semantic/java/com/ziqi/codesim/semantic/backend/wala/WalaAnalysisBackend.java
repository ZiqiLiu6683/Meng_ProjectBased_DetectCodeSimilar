package com.ziqi.codesim.semantic.backend.wala;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.ShrikeCTMethod;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
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
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.ClassLoaderReference;
import com.ziqi.codesim.semantic.backend.AnalysisBackend;
import com.ziqi.codesim.semantic.backend.AnalysisException;
import com.ziqi.codesim.semantic.model.AnalyzedMethod;
import com.ziqi.codesim.semantic.model.AnalyzedProgram;
import com.ziqi.codesim.semantic.model.BasicBlockUnit;
import com.ziqi.codesim.semantic.model.CallKind;
import com.ziqi.codesim.semantic.model.ControlFlowEdge;
import com.ziqi.codesim.semantic.model.ControlFlowGraphUnit;
import com.ziqi.codesim.semantic.model.InstructionCategory;
import com.ziqi.codesim.semantic.model.InstructionUnit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WalaAnalysisBackend implements AnalysisBackend {
    @Override
    public AnalyzedProgram analyze(Path input) throws AnalysisException {
        try {
            AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(
                    input.toAbsolutePath().toString(),
                    null
            );
            ClassHierarchy hierarchy = ClassHierarchyFactory.makeWithPhantom(scope);
            AnalysisCacheImpl cache = new AnalysisCacheImpl();
            List<AnalyzedMethod> methods = new ArrayList<>();
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
                    methods.add(convertMethod(clazz, method, ir));
                }
            }
            return new AnalyzedProgram(input.toAbsolutePath().toString(), methods);
        } catch (IOException | RuntimeException ex) {
            throw new AnalysisException("Failed to analyze bytecode input with WALA: " + input, ex);
        } catch (Exception ex) {
            throw new AnalysisException("Failed to build WALA class hierarchy for: " + input, ex);
        }
    }

    private static AnalyzedMethod convertMethod(IClass clazz, IMethod method, IR ir) {
        List<String> parameterTypes = new ArrayList<>();
        for (int i = 0; i < method.getNumberOfParameters(); i++) {
            parameterTypes.add(method.getParameterType(i).getName().toString());
        }
        return new AnalyzedMethod(
                method.getSignature(),
                clazz.getName().toString(),
                method.getSignature(),
                method.getReturnType().getName().toString(),
                parameterTypes,
                convertCfg(ir),
                localVariableCount(method, ir)
        );
    }

    // discovRE's "size of local variables" maps to the JVM method's local variable slot count
    // (max_locals from the bytecode Code attribute). We read it from the Shrike method when
    // available and fall back to the SSA value count only if the bytecode size is unavailable.
    private static int localVariableCount(IMethod method, IR ir) {
        if (method instanceof ShrikeCTMethod shrikeMethod) {
            try {
                return shrikeMethod.getMaxLocals();
            } catch (Exception ignored) {
                // Fall back to the SSA value-count approximation below.
            }
        }
        return ir.getSymbolTable().getMaxValueNumber();
    }

    private static ControlFlowGraphUnit convertCfg(IR ir) {
        SSACFG cfg = ir.getControlFlowGraph();
        List<BasicBlockUnit> blocks = new ArrayList<>();
        for (ISSABasicBlock block : iterable(cfg.iterator())) {
            blocks.add(convertBlock(ir, cfg, block));
        }
        List<ControlFlowEdge> edges = new ArrayList<>();
        for (ISSABasicBlock from : iterable(cfg.iterator())) {
            for (ISSABasicBlock to : iterable(cfg.getSuccNodes(from))) {
                edges.add(new ControlFlowEdge(blockId(cfg, from), blockId(cfg, to), edgeKind(cfg, from, to)));
            }
        }
        return new ControlFlowGraphUnit(blocks, edges);
    }

    private static BasicBlockUnit convertBlock(IR ir, SSACFG cfg, ISSABasicBlock block) {
        List<InstructionUnit> instructions = new ArrayList<>();
        int ordinal = 0;
        for (SSAInstruction instruction : block) {
            if (instruction == null) continue;
            instructions.add(convertInstruction(ir, instruction, ordinal++));
        }
        return new BasicBlockUnit(
                blockId(cfg, block),
                cfg.getNumber(block),
                block.isEntryBlock(),
                block.isExitBlock(),
                instructions
        );
    }

    private static InstructionUnit convertInstruction(IR ir, SSAInstruction instruction, int ordinal) {
        List<String> defs = new ArrayList<>();
        for (int i = 0; i < instruction.getNumberOfDefs(); i++) {
            defs.add(valueName(instruction.getDef(i)));
        }
        SymbolTable symbolTable = ir.getSymbolTable();
        List<String> uses = new ArrayList<>();
        List<String> numericConstants = new ArrayList<>();
        List<String> stringConstants = new ArrayList<>();
        for (int i = 0; i < instruction.getNumberOfUses(); i++) {
            int value = instruction.getUse(i);
            uses.add(valueName(value));
            collectConstantOperand(symbolTable, value, numericConstants, stringConstants);
        }
        return new InstructionUnit(
                "i" + instruction.iIndex(),
                ordinal,
                operationName(instruction),
                categoryOf(instruction),
                callKindOf(instruction),
                defs,
                uses,
                numericConstants,
                stringConstants,
                callTargetOf(instruction)
        );
    }

    private static String callTargetOf(SSAInstruction instruction) {
        if (instruction instanceof SSAAbstractInvokeInstruction invoke) {
            return invoke.getDeclaredTarget().getSignature();
        }
        return "";
    }

    // Extract real constant operands from WALA's symbol table instead of scanning the
    // instruction's printed text (which also contains SSA value numbers and indices and
    // therefore over-counts). Only genuine constant operands are recorded.
    private static void collectConstantOperand(SymbolTable symbolTable, int value,
                                               List<String> numericConstants,
                                               List<String> stringConstants) {
        if (value < 0 || !symbolTable.isConstant(value) || symbolTable.isNullConstant(value)) {
            return;
        }
        Object constant = symbolTable.getConstantValue(value);
        if (symbolTable.isStringConstant(value)) {
            stringConstants.add(String.valueOf(constant));
        } else {
            numericConstants.add(String.valueOf(constant));
        }
    }

    private static InstructionCategory categoryOf(SSAInstruction instruction) {
        if (instruction instanceof SSAConditionalBranchInstruction
                || instruction instanceof SSAGotoInstruction) {
            return InstructionCategory.BRANCH;
        }
        if (instruction instanceof SSAReturnInstruction) {
            return InstructionCategory.RETURN;
        }
        if (instruction instanceof SSAAbstractInvokeInstruction) {
            return InstructionCategory.CALL;
        }
        if (instruction instanceof SSABinaryOpInstruction binaryOp) {
            return isLogicOperator(binaryOp)
                    ? InstructionCategory.LOGIC
                    : InstructionCategory.ARITHMETIC;
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
        // WALA folds boolean/bitwise/shift binary ops into SSABinaryOpInstruction together
        // with arithmetic ops. discovRE separates arithmetic vs logic instruction classes,
        // so we split by operator name: and/or/xor/shl/shr/ushr are logic; add/sub/mul/div/rem
        // are arithmetic. We compare on the operator name to avoid binding to a specific
        // shrike enum type (binary vs shift operators implement different interfaces).
        String operator = instruction.getOperator().toString().toLowerCase(java.util.Locale.ROOT);
        return operator.equals("and") || operator.equals("or") || operator.equals("xor")
                || operator.equals("shl") || operator.equals("shr") || operator.equals("ushr");
    }

    private static CallKind callKindOf(SSAInstruction instruction) {
        if (!(instruction instanceof SSAAbstractInvokeInstruction invoke)) {
            return CallKind.NOT_A_CALL;
        }
        if (invoke.getDeclaredTarget().isInit()) {
            return CallKind.CONSTRUCTOR;
        }
        // Internal = the callee belongs to the application's own code base (same set of classes
        // we analyze); everything else (JDK or any third-party library) is external. This relies
        // on WALA's class loader of the target type, replacing the previous java/javax/sun
        // package-prefix heuristic that mislabelled non-JDK libraries (e.g. org.apache) as internal.
        ClassLoaderReference targetLoader = invoke.getDeclaredTarget().getDeclaringClass().getClassLoader();
        if (ClassLoaderReference.Application.equals(targetLoader)) {
            return CallKind.INTERNAL;
        }
        return CallKind.EXTERNAL;
    }

    private static String operationName(SSAInstruction instruction) {
        String simpleName = instruction.getClass().getSimpleName();
        if (simpleName.startsWith("SSA")) {
            simpleName = simpleName.substring(3);
        }
        if (simpleName.endsWith("Instruction")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Instruction".length());
        }
        return simpleName;
    }

    private static String valueName(int valueNumber) {
        return "v" + valueNumber;
    }

    private static String blockId(SSACFG cfg, ISSABasicBlock block) {
        return "b" + cfg.getNumber(block);
    }

    private static String edgeKind(SSACFG cfg, ISSABasicBlock from, ISSABasicBlock to) {
        if (cfg.getExceptionalSuccessors(from).contains(to)) {
            return "exceptional";
        }
        return "normal";
    }

    private static <T> Iterable<T> iterable(Iterator<T> iterator) {
        return () -> iterator;
    }
}
