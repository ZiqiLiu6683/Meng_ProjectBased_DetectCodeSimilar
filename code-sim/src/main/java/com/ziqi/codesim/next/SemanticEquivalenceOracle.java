package com.ziqi.codesim.next;

/**
 * Supplies an independent semantic-equivalence verdict for a region pair — the "independent
 * CFG/dynamic semantic approval" the {@link NextRegionTypeRecognizer} waits for before it will
 * confirm a Type-4 clone. Backed by Phase B (SMT proof that two methods compute the same value for
 * all inputs), this is what lets the recognizer approve behaviourally-equivalent, structurally
 * different code that T1/T2/T3 cannot.
 *
 * <p>Kept as an interface in the always-compiled source set (like {@link StructuralSimilarityOracle})
 * so the recognizer has no dependency on the WALA/SMT layer; the real implementation is supplied by
 * the semantic-analysis pipeline.
 */
public interface SemanticEquivalenceOracle {

    /** Oracle with no semantic evidence; used by the source-only pipeline (never confirms T4). */
    SemanticEquivalenceOracle NONE = (left, right) -> false;

    /** True only when the two regions are proven to compute the same value for all inputs. */
    boolean provenEquivalent(CodeRegion left, CodeRegion right);
}
