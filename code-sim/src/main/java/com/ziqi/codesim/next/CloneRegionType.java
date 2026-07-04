package com.ziqi.codesim.next;

public enum CloneRegionType {
    T1,
    T2,
    T3,
    /** Type-4 proven by SMT: the two regions compute the same value for ALL inputs. */
    T4_CONFIRMED,
    /**
     * Type-4 supported by dynamic I/O sampling: the two regions agreed on every tested input
     * (loops/nonlinear code SMT cannot prove). Strong evidence, NOT a proof -- strictly weaker than
     * {@link #T4_CONFIRMED}, stronger than {@link #POSSIBLE_T4_CANDIDATE}.
     */
    T4_DYNAMIC_EVIDENCE,
    POSSIBLE_T4_CANDIDATE,
    NON_CLONE
}
