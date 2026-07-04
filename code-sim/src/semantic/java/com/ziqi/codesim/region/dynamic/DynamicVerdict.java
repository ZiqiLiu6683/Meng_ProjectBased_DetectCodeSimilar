package com.ziqi.codesim.region.dynamic;

/**
 * Result of a dynamic (I/O-sampling) equivalence check between two methods. Unlike the SMT
 * {@code EquivalenceVerdict}, {@link #LIKELY_EQUIVALENT} is EVIDENCE (agreement on many random
 * inputs), not a proof.
 */
public enum DynamicVerdict {

    /** The two methods produced identical outputs on every sampled input (strong evidence, not proof). */
    LIKELY_EQUIVALENT,

    /** A sampled input produced different outputs (a counterexample). */
    DIFFERENT,

    /** Signatures the checker cannot sample (non-int parameters/return, differing arity). */
    UNSUPPORTED,

    /** A run failed (exception, timeout, or the class could not be instantiated). */
    UNKNOWN
}
