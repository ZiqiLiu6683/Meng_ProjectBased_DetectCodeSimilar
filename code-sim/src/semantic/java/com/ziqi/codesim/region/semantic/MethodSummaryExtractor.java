package com.ziqi.codesim.region.semantic;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
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
import java.util.Set;

/**
 * Phase B summary extraction: turns each application method into a {@link SymbolicExpression} for
 * its returned value, in terms of its parameters. WALA-typed (one of the few classes allowed to
 * touch WALA). Only straight-line, single-return integer methods are modelled exactly; anything
 * else (loops/phi, multiple returns, calls, non-integer values) yields {@link Unknown}, so the
 * equivalence checker can degrade to UNKNOWN instead of being misled.
 */
public final class MethodSummaryExtractor {

    private static final int MAX_DEPTH = 32;

    /** @return method signature -> return-value summary, for every analyzable application method. */
    public Map<String, SymbolicExpression> extractAll(Path classesDir) throws AnalysisException {
        ClassHierarchy hierarchy = WalaClassHierarchies.build(classesDir);
        AnalysisCacheImpl cache = new AnalysisCacheImpl();
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
                summaries.put(method.getSignature(), summarize(ir));
            }
        }
        return summaries;
    }

    private static SymbolicExpression summarize(IR ir) {
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
        return valueExpression(returnValue, symbolTable, defUse, parameterPosition, 0, new HashSet<>());
    }

    private static SymbolicExpression valueExpression(int value, SymbolTable symbolTable, DefUse defUse,
                                                      Map<Integer, Integer> parameterPosition,
                                                      int depth, Set<Integer> visiting) {
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
        if (depth >= MAX_DEPTH || !visiting.add(value)) {
            return new Unknown("depth limit or cycle (loop)");
        }
        SSAInstruction def = defUse.getDef(value);
        SymbolicExpression result;
        if (def instanceof SSABinaryOpInstruction binaryOp) {
            String operator = binaryOp.getOperator().toString().toLowerCase(Locale.ROOT);
            SymbolicExpression left = valueExpression(
                    binaryOp.getUse(0), symbolTable, defUse, parameterPosition, depth + 1, visiting);
            SymbolicExpression right = valueExpression(
                    binaryOp.getUse(1), symbolTable, defUse, parameterPosition, depth + 1, visiting);
            result = new BinaryOperation(operator, left, right);
        } else {
            result = new Unknown(def == null ? "no defining instruction" : def.getClass().getSimpleName());
        }
        visiting.remove(value);
        return result;
    }
}
