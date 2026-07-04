package com.ziqi.codesim.next;

/**
 * Supplies a dynamic (I/O-sampling) equivalence verdict for a region pair -- the weaker, evidence-
 * based sibling of {@link SemanticEquivalenceOracle}. Where the semantic oracle carries an SMT PROOF
 * that two regions compute the same value for all inputs, this oracle carries only EVIDENCE: the two
 * regions agreed on every input that was actually sampled. It exists to catch the Type-4 clones SMT
 * cannot prove (loops, nonlinear arithmetic), and the {@link NextRegionTypeRecognizer} reports those
 * as {@link CloneRegionType#T4_DYNAMIC_EVIDENCE} -- deliberately distinct from an SMT-confirmed T4.
 *
 * <p>Kept as an interface in the always-compiled source set so the recognizer has no dependency on
 * the WALA/runtime layer; the real implementation is supplied by the semantic-analysis pipeline.
 */
public interface DynamicEquivalenceOracle {

    /** Oracle with no dynamic evidence; used by the source-only pipeline (never evidences T4). */
    DynamicEquivalenceOracle NONE = (left, right) -> false;

    /** True when the two regions produced identical outputs on every sampled input (evidence, not proof). */
    boolean likelyEquivalent(CodeRegion left, CodeRegion right);
}
