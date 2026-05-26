package com.ziqi.codesim.semantic.backend.wala;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
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
                convertCfg(ir)
        );
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
        List<String> uses = new ArrayList<>();
        for (int i = 0; i < instruction.getNumberOfUses(); i++) {
            uses.add(valueName(instruction.getUse(i)));
        }
        String text = instruction.toString(ir.getSymbolTable());
        return new InstructionUnit(
                "i" + instruction.iIndex(),
                ordinal,
                operationName(instruction),
                categoryOf(instruction),
                callKindOf(instruction),
                defs,
                uses,
                constantsFrom(text),
                stringReferencesFrom(text)
        );
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
        if (instruction instanceof SSABinaryOpInstruction) {
            return InstructionCategory.ARITHMETIC;
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

    private static CallKind callKindOf(SSAInstruction instruction) {
        if (!(instruction instanceof SSAAbstractInvokeInstruction invoke)) {
            return CallKind.NOT_A_CALL;
        }
        String target = invoke.getDeclaredTarget().getDeclaringClass().getName().toString();
        if (target.startsWith("Ljava/") || target.startsWith("Ljavax/") || target.startsWith("Lsun/")) {
            return CallKind.EXTERNAL;
        }
        if (invoke.getDeclaredTarget().isInit()) {
            return CallKind.CONSTRUCTOR;
        }
        return CallKind.INTERNAL;
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

    private static List<String> constantsFrom(String text) {
        List<String> constants = new ArrayList<>();
        for (String token : text.split("[^A-Za-z0-9_.$-]+")) {
            if (token.matches("-?\\d+(\\.\\d+)?")) {
                constants.add(token);
            }
        }
        return constants;
    }

    private static List<String> stringReferencesFrom(String text) {
        List<String> strings = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            int start = text.indexOf('"', index);
            if (start < 0) break;
            int end = text.indexOf('"', start + 1);
            if (end < 0) break;
            strings.add(text.substring(start + 1, end));
            index = end + 1;
        }
        return strings;
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
