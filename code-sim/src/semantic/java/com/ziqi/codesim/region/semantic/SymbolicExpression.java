package com.ziqi.codesim.region.semantic;

/**
 * A method's computed value expressed as a function of its parameters — the Phase B summary that
 * SMT reasons over. Deliberately tiny (integers + binary ops) for the first slice; anything the
 * extractor cannot model (loops/phi, calls, non-integer values, unsupported instructions) becomes
 * {@link Unknown}, which the equivalence checker treats as undecidable rather than guessing.
 */
public sealed interface SymbolicExpression
        permits SymbolicExpression.Constant,
        SymbolicExpression.Parameter,
        SymbolicExpression.RegionInput,
        SymbolicExpression.BinaryOperation,
        SymbolicExpression.Unknown {

    /** A literal integer constant. */
    record Constant(long value) implements SymbolicExpression {
    }

    /** The method parameter at a given position (name-free; position 0 is {@code this} for instance methods). */
    record Parameter(int index) implements SymbolicExpression {
    }

    /**
     * A free input to a REGION summary: a value used inside the region but defined outside it,
     * identified by its SSA value number. Region equivalence matches a left region's inputs to a
     * right region's inputs (by which aligned node/operand consumes them) and treats each matched
     * pair as the same solver variable.
     */
    record RegionInput(int valueId) implements SymbolicExpression {
    }

    /** A binary operation whose operator is a WALA operator name (add, sub, mul, div, rem, and, ...). */
    record BinaryOperation(String operator, SymbolicExpression left, SymbolicExpression right)
            implements SymbolicExpression {
    }

    /** Something the extractor could not model; carries a reason for provenance. */
    record Unknown(String reason) implements SymbolicExpression {
    }

    /** True if any part of this expression is {@link Unknown} (so equivalence is undecidable). */
    default boolean hasUnknown() {
        if (this instanceof Unknown) {
            return true;
        }
        if (this instanceof BinaryOperation operation) {
            return operation.left().hasUnknown() || operation.right().hasUnknown();
        }
        return false; // Constant, Parameter, RegionInput
    }
}
