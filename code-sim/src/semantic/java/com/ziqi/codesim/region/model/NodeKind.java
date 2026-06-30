package com.ziqi.codesim.region.model;

/**
 * Coarse category of a {@link SemanticNode}, abstracted from WALA's finer {@code Statement.Kind}.
 *
 * <p>The raw WALA kind name is preserved separately on the node for provenance; this enum is the
 * stable category the rest of the pipeline switches on. Only {@link #STATEMENT} nodes are expected
 * to carry a real source line; the pseudo-node kinds model interprocedural parameter/heap/return
 * wiring and are normally synthetic.
 */
public enum NodeKind {

    /** A real SSA instruction (the only kind that normally has a source line). */
    STATEMENT,

    /** Formal/actual parameter pseudo-node (caller or callee side). */
    PARAM,

    /** Return-value pseudo-node (caller or callee side). */
    RETURN,

    /** Heap (pointer/field) parameter pseudo-node. */
    HEAP,

    /** SSA phi/pi pseudo-node merging values across control flow. */
    PHI,

    /** Exceptional-flow pseudo-node. */
    EXCEPTION,

    /** Anything not classified above. */
    OTHER
}
