package com.ziqi.codesim.region.model;

/**
 * Typed dependence edge in a {@link SemanticGraph}.
 *
 * <p>Interprocedural kinds ({@link #CALL}, {@link #PARAM_IN}, {@link #PARAM_OUT}, {@link #RETURN})
 * are recovered reliably from the WALA {@code Statement} kinds at the two endpoints, so they are
 * emitted precisely from M1 on. Intraprocedural dependence edges are emitted as {@link #DEPENDENCE}
 * in M1 (control-or-data, not yet split); splitting them into {@link #CONTROL_DEP} vs
 * {@link #DATA_DEP} is an M2 refinement (e.g. by differencing a control-only vs data-only SDG, or
 * by introspecting the per-method PDG). The values exist now so downstream code can switch on them
 * without a later enum change.
 */
public enum EdgeKind {

    /** Intraprocedural control dependence (reserved; not yet emitted as a distinct kind in M1). */
    CONTROL_DEP,

    /** Intraprocedural data (def-use) dependence (reserved; not yet split out in M1). */
    DATA_DEP,

    /** Intraprocedural dependence whose control-vs-data subtype is not yet distinguished. */
    DEPENDENCE,

    /** Caller call-site statement to the invoked callee context. */
    CALL,

    /** Actual-in (caller) to formal-in (callee) parameter passing. */
    PARAM_IN,

    /** Formal-out/return value (callee) back to the caller. */
    PARAM_OUT,

    /** Callee return statement to the caller's return-value site. */
    RETURN,

    /** Any interprocedural edge that does not fit the categories above. */
    INTERPROC_OTHER
}
