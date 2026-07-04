package com.ziqi.codesim.next;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seam's core behaviour: when T1/T2/T3 all fail (structurally unrelated regions) but the
 * semantic oracle proves the two regions equivalent, the recognizer must confirm a Type-4 clone;
 * with no semantic evidence it must stay NON_CLONE (unchanged legacy behaviour).
 */
class NextRegionTypeRecognizerT4Test {

    @Test
    void confirmsT4WhenSemanticOracleProvesEquivalence() {
        RegionCandidate candidate = structurallyUnrelatedMethodPair();

        SemanticEquivalenceOracle proves = (left, right) -> true;
        RegionDecision confirmed = new NextRegionTypeRecognizer(StructuralSimilarityOracle.NONE, proves)
                .decide(candidate);
        assertEquals(CloneRegionType.T4_CONFIRMED, confirmed.type(),
                "an SMT-proven equivalent pair that fails T1/T2/T3 must be confirmed as T4");
        assertTrue(confirmed.tags().contains(RegionTag.POSSIBLE_SEMANTIC_RELATION));

        RegionDecision withoutOracle = new NextRegionTypeRecognizer().decide(candidate);
        assertEquals(CloneRegionType.NON_CLONE, withoutOracle.type(),
                "without semantic evidence the same pair stays NON_CLONE (legacy behaviour)");
    }

    @Test
    void evidencesT4WhenOnlyDynamicOracleAgrees() {
        RegionCandidate candidate = structurallyUnrelatedMethodPair();

        DynamicEquivalenceOracle samplesAgree = (left, right) -> true;
        RegionDecision evidenced = new NextRegionTypeRecognizer(
                StructuralSimilarityOracle.NONE, SemanticEquivalenceOracle.NONE,
                StructuralRegionOracle.NONE, samplesAgree)
                .decide(candidate);
        assertEquals(CloneRegionType.T4_DYNAMIC_EVIDENCE, evidenced.type(),
                "no SMT proof but I/O sampling agrees -> the evidence tier, not a confirmed T4");
        assertTrue(evidenced.tags().contains(RegionTag.POSSIBLE_SEMANTIC_RELATION));
    }

    @Test
    void smtProofOutranksDynamicEvidence() {
        RegionCandidate candidate = structurallyUnrelatedMethodPair();

        SemanticEquivalenceOracle proves = (left, right) -> true;
        DynamicEquivalenceOracle samplesAgree = (left, right) -> true;
        RegionDecision decision = new NextRegionTypeRecognizer(
                StructuralSimilarityOracle.NONE, proves, StructuralRegionOracle.NONE, samplesAgree)
                .decide(candidate);
        assertEquals(CloneRegionType.T4_CONFIRMED, decision.type(),
                "when both fire, the SMT proof wins over dynamic evidence");
    }

    /** Two METHOD regions with fully disjoint tokens/statements, so T1/T2/T3 cannot approve. */
    private static RegionCandidate structurallyUnrelatedMethodPair() {
        CodeRegion left = new CodeRegion(
                "L1", RegionSide.LEFT, RegionKind.METHOD, "A.twice(int)", 1, 1,
                List.of("a1", "a2", "a3"), List.of("a1", "a2", "a3"), List.of("a1", "a2", "a3"),
                List.of("stmt_a1", "stmt_a2"), List.of("norm_a1", "norm_a2"));
        CodeRegion right = new CodeRegion(
                "R1", RegionSide.RIGHT, RegionKind.METHOD, "B.doubled(int)", 1, 1,
                List.of("b1", "b2", "b3"), List.of("b1", "b2", "b3"), List.of("b1", "b2", "b3"),
                List.of("stmt_b1", "stmt_b2"), List.of("norm_b1", "norm_b2"));
        return new RegionCandidate("C1", left, right, List.of());
    }
}
