package com.ziqi.codesim.region.semantic;

/**
 * Result of an SMT equivalence check between two {@link SymbolicExpression} summaries.
 */
public enum EquivalenceVerdict {

    /** Proven equal for all inputs (the negation of equality is unsatisfiable). */
    EQUIVALENT,

    /** Proven to differ on some input (a counterexample exists). */
    DIFFERENT,

    /** The solver could not decide (e.g. nonlinear arithmetic) or a summary was {@link SymbolicExpression.Unknown}. */
    UNKNOWN,

    /** The expression uses a theory the checker does not model (e.g. bitwise/shift in integer logic). */
    UNSUPPORTED
}
