package com.ziqi.codesim.region.semantic;

import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ziqi.codesim.region.semantic.SymbolicExpression.BinaryOperation;
import com.ziqi.codesim.region.semantic.SymbolicExpression.Constant;
import com.ziqi.codesim.region.semantic.SymbolicExpression.RegionInput;
import com.ziqi.codesim.region.semantic.SymbolicExpression.Unknown;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Summarizes a REGION (a subset of one method's SSA values) as a set of output expressions over the
 * region's inputs -- the region-facing analogue of {@link MethodSummaryExtractor}. A region is a
 * sub-computation with:
 * <ul>
 *   <li><b>outputs</b> = in-region values used by an instruction outside the region (or the return);</li>
 *   <li><b>inputs</b> = values used inside the region but defined outside it (parameters or
 *       pre-region values), each becoming a {@link RegionInput} free variable.</li>
 * </ul>
 * The equivalence verifier compares two regions' output expressions with their inputs matched.
 *
 * <p>First slice: same-method regions of integer arithmetic. A call inside the region yields
 * {@link Unknown} (cross-method region summarization is a later extension).
 */
public final class RegionSummarizer {

    private static final int MAX_DEPTH = 64;

    /** @return output SSA value -> its expression over {@link RegionInput}s, for each region output. */
    public Map<Integer, SymbolicExpression> summarize(IR ir, DefUse defUse, Set<Integer> inRegionValues) {
        SymbolTable symbolTable = ir.getSymbolTable();
        Map<Integer, SymbolicExpression> outputs = new LinkedHashMap<>();
        for (int value : inRegionValues) {
            if (isOutput(value, defUse, inRegionValues)) {
                outputs.put(value, expression(value, symbolTable, defUse, inRegionValues, 0, new HashSet<>()));
            }
        }
        return outputs;
    }

    private static boolean isOutput(int value, DefUse defUse, Set<Integer> inRegionValues) {
        for (Iterator<SSAInstruction> it = defUse.getUses(value); it.hasNext(); ) {
            if (!isInRegion(it.next(), inRegionValues)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInRegion(SSAInstruction instruction, Set<Integer> inRegionValues) {
        return instruction != null && instruction.hasDef() && inRegionValues.contains(instruction.getDef(0));
    }

    private SymbolicExpression expression(int value, SymbolTable symbolTable, DefUse defUse,
                                          Set<Integer> inRegionValues, int depth, Set<Integer> visiting) {
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
        // Defined outside the region (parameter or pre-region value) -> a free input.
        if (!inRegionValues.contains(value)) {
            return new RegionInput(value);
        }
        if (depth >= MAX_DEPTH || !visiting.add(value)) {
            return new Unknown("depth limit or cycle");
        }
        SSAInstruction def = defUse.getDef(value);
        SymbolicExpression result;
        if (def instanceof SSABinaryOpInstruction binaryOp) {
            String operator = binaryOp.getOperator().toString().toLowerCase(Locale.ROOT);
            SymbolicExpression left = expression(binaryOp.getUse(0), symbolTable, defUse,
                    inRegionValues, depth + 1, visiting);
            SymbolicExpression right = expression(binaryOp.getUse(1), symbolTable, defUse,
                    inRegionValues, depth + 1, visiting);
            result = new BinaryOperation(operator, left, right);
        } else {
            // Calls, phi, field/array, etc. inside the region are not modelled in this first slice.
            result = new Unknown(def == null ? "no defining instruction" : def.getClass().getSimpleName());
        }
        visiting.remove(value);
        return result;
    }
}
