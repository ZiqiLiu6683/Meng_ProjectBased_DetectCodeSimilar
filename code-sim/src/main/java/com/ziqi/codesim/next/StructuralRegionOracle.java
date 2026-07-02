package com.ziqi.codesim.next;

/**
 * Supplies Phase A structural evidence for a region pair: the coverage of the strongest
 * boundary-free region group that aligns the two regions' methods. This is the evidence that lets
 * the recognizer flag a POSSIBLE Type-4 candidate for structurally-aligned, cross-method clones
 * (e.g. helper extraction) that T1/T2/T3 miss and Phase B cannot prove equivalent.
 *
 * <p>Interface lives in the always-compiled source set (like the other oracles); the implementation
 * is supplied by the semantic-analysis pipeline.
 */
public interface StructuralRegionOracle {

    /** Oracle with no structural evidence; used by the source-only pipeline. */
    StructuralRegionOracle NONE = (left, right) -> 0.0;

    /** Coverage of the strongest region group aligning these two regions; 0 if none applies. */
    double regionCoverage(CodeRegion left, CodeRegion right);
}
