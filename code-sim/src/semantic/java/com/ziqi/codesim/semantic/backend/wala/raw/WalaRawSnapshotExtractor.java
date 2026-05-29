package com.ziqi.codesim.semantic.backend.wala.raw;

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
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ziqi.codesim.semantic.backend.AnalysisException;
import com.ziqi.codesim.semantic.raw.RawToolBlock;
import com.ziqi.codesim.semantic.raw.RawToolClass;
import com.ziqi.codesim.semantic.raw.RawToolInstruction;
import com.ziqi.codesim.semantic.raw.RawToolMethod;
import com.ziqi.codesim.semantic.raw.RawToolProgram;
import com.ziqi.codesim.semantic.raw.RawToolRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WalaRawSnapshotExtractor {
    public RawToolProgram extract(Path input) throws AnalysisException {
        try {
            AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(
                    input.toAbsolutePath().toString(),
                    null
            );
            ClassHierarchy hierarchy = ClassHierarchyFactory.makeWithPhantom(scope);
            AnalysisCacheImpl cache = new AnalysisCacheImpl();
            List<RawToolClass> classes = new ArrayList<>();
            for (IClass clazz : hierarchy) {
                if (!ClassLoaderReference.Application.equals(clazz.getClassLoader().getReference())) {
                    continue;
                }
                RawToolClass rawClass = convertClass(cache, clazz);
                if (!rawClass.methods().isEmpty()) {
                    classes.add(rawClass);
                }
            }
            return new RawToolProgram(
                    "WALA",
                    resolveWalaVersion(),
                    input.toAbsolutePath().toString(),
                    classes,
                    List.of(record("program.input", input.toAbsolutePath().toString(), "program"))
            );
        } catch (IOException | RuntimeException ex) {
            throw new AnalysisException("Failed to extract WALA raw snapshot: " + input, ex);
        } catch (Exception ex) {
            throw new AnalysisException("Failed to build WALA class hierarchy for raw snapshot: " + input, ex);
        }
    }

    private static RawToolClass convertClass(AnalysisCacheImpl cache, IClass clazz) {
        List<RawToolMethod> methods = new ArrayList<>();
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
        List<RawToolRecord> records = List.of(
                record("class.rawClassString", raw(clazz), "class", clazz.getName().toString()),
                record("class.rawClassName", clazz.getName().toString(), "class", clazz.getName().toString()),
                record("class.rawClassLoader", raw(clazz.getClassLoader().getReference()), "class", clazz.getName().toString())
        );
        return new RawToolClass(
                raw(clazz),
                clazz.getName().toString(),
                raw(clazz.getClassLoader().getReference()),
                methods,
                records
        );
    }

    private static RawToolMethod convertMethod(IClass clazz, IMethod method, IR ir) {
        List<String> parameterTypes = new ArrayList<>();
        for (int i = 0; i < method.getNumberOfParameters(); i++) {
            parameterTypes.add(method.getParameterType(i).getName().toString());
        }
        List<RawToolBlock> blocks = convertBlocks(method, ir);
        String methodPath = clazz.getName() + "#" + method.getSignature();
        List<RawToolRecord> records = new ArrayList<>();
        records.add(record("method.rawMethodString", raw(method), methodPath));
        records.add(record("method.rawMethodSignature", method.getSignature(), methodPath));
        records.add(record("method.rawDeclaringClass", clazz.getName().toString(), methodPath));
        records.add(record("method.rawReturnType", method.getReturnType().getName().toString(), methodPath));
        for (String parameterType : parameterTypes) {
            records.add(record("method.rawParameterType", parameterType, methodPath));
        }
        records.add(record("method.rawIRText", raw(ir), methodPath));
        records.add(record("method.rawSymbolTableText", raw(ir.getSymbolTable()), methodPath));
        records.add(record("method.rawCFGText", raw(ir.getControlFlowGraph()), methodPath));
        return new RawToolMethod(
                raw(method),
                method.getSignature(),
                clazz.getName().toString(),
                method.getReturnType().getName().toString(),
                parameterTypes,
                raw(ir),
                raw(ir.getSymbolTable()),
                raw(ir.getControlFlowGraph()),
                blocks,
                records
        );
    }

    private static List<RawToolBlock> convertBlocks(IMethod method, IR ir) {
        SSACFG cfg = ir.getControlFlowGraph();
        List<RawToolBlock> blocks = new ArrayList<>();
        for (ISSABasicBlock block : iterable(cfg.iterator())) {
            blocks.add(convertBlock(method, ir, cfg, block));
        }
        return blocks;
    }

    private static RawToolBlock convertBlock(IMethod method, IR ir, SSACFG cfg, ISSABasicBlock block) {
        String blockPath = method.getSignature() + "#b" + cfg.getNumber(block);
        List<String> normalSuccessors = blockNumbers(cfg, cfg.getNormalSuccessors(block));
        List<String> exceptionalSuccessors = blockNumbers(cfg, cfg.getExceptionalSuccessors(block));
        List<String> predecessors = blockNumbers(cfg, cfg.getPredNodes(block));
        List<RawToolInstruction> instructions = new ArrayList<>();
        int ordinal = 0;
        for (SSAInstruction instruction : block) {
            if (instruction == null) {
                continue;
            }
            instructions.add(convertInstruction(ir, instruction, blockPath, ordinal++));
        }
        List<RawToolRecord> records = new ArrayList<>();
        records.add(record("block.rawBlockString", raw(block), blockPath));
        records.add(record("block.rawBlockNumber", Integer.toString(cfg.getNumber(block)), blockPath));
        records.add(record("block.rawIsEntry", Boolean.toString(block.isEntryBlock()), blockPath));
        records.add(record("block.rawIsExit", Boolean.toString(block.isExitBlock()), blockPath));
        normalSuccessors.forEach(value -> records.add(record("block.rawNormalSuccessor", value, blockPath)));
        exceptionalSuccessors.forEach(value -> records.add(record("block.rawExceptionalSuccessor", value, blockPath)));
        predecessors.forEach(value -> records.add(record("block.rawPredecessor", value, blockPath)));
        return new RawToolBlock(
                raw(block),
                cfg.getNumber(block),
                block.isEntryBlock(),
                block.isExitBlock(),
                normalSuccessors,
                exceptionalSuccessors,
                predecessors,
                instructions,
                records
        );
    }

    private static RawToolInstruction convertInstruction(
            IR ir,
            SSAInstruction instruction,
            String blockPath,
            int ordinal) {
        String instructionPath = blockPath + "#i" + ordinal;
        List<String> defs = new ArrayList<>();
        for (int i = 0; i < instruction.getNumberOfDefs(); i++) {
            defs.add(Integer.toString(instruction.getDef(i)));
        }
        List<String> uses = new ArrayList<>();
        for (int i = 0; i < instruction.getNumberOfUses(); i++) {
            uses.add(Integer.toString(instruction.getUse(i)));
        }
        String declaredTarget = "";
        if (instruction instanceof SSAAbstractInvokeInstruction invoke) {
            declaredTarget = invoke.getDeclaredTarget().toString();
        }
        String rawText = instruction.toString(ir.getSymbolTable());
        List<RawToolRecord> records = new ArrayList<>();
        records.add(record("instruction.rawInstructionClassName", instruction.getClass().getName(), instructionPath));
        records.add(record("instruction.rawInstructionText", rawText, instructionPath));
        records.add(record("instruction.rawInstructionIndex", Integer.toString(instruction.iIndex()), instructionPath));
        defs.forEach(value -> records.add(record("instruction.rawDefValue", value, instructionPath)));
        uses.forEach(value -> records.add(record("instruction.rawUseValue", value, instructionPath)));
        if (!declaredTarget.isBlank()) {
            records.add(record("instruction.rawDeclaredTargetText", declaredTarget, instructionPath));
        }
        return new RawToolInstruction(
                instruction.getClass().getName(),
                rawText,
                instruction.iIndex(),
                defs,
                uses,
                declaredTarget,
                records
        );
    }

    private static List<String> blockNumbers(SSACFG cfg, Iterable<ISSABasicBlock> blocks) {
        List<String> numbers = new ArrayList<>();
        for (ISSABasicBlock block : blocks) {
            numbers.add(Integer.toString(cfg.getNumber(block)));
        }
        return numbers;
    }

    private static List<String> blockNumbers(SSACFG cfg, Iterator<ISSABasicBlock> blocks) {
        return blockNumbers(cfg, iterable(blocks));
    }

    private static String resolveWalaVersion() {
        Package pkg = AnalysisScopeReader.class.getPackage();
        String version = pkg == null ? null : pkg.getImplementationVersion();
        return version == null ? "unknown" : version;
    }

    private static RawToolRecord record(String channel, String rawValue, String... provenance) {
        return new RawToolRecord(channel, rawValue, List.of(provenance));
    }

    private static String raw(Object value) {
        return value == null ? "" : value.toString();
    }

    private static <T> Iterable<T> iterable(Iterator<T> iterator) {
        return () -> iterator;
    }
}
