package com.ziqi.codesim.next.semantic;

import com.ziqi.codesim.next.CloneRegionType;
import com.ziqi.codesim.next.NextPipelineResult;
import com.ziqi.codesim.next.RegionKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural seam (Phase A) end-to-end: a helper-extraction clone -- one side inlines {@code x*2+1},
 * the other splits it across {@code g} calling {@code h} -- is missed by the token backend and by
 * Phase B (the call breaks the summary), but Phase A aligns it as a boundary-free region group. The
 * full pipeline must now flag it as a POSSIBLE_T4_CANDIDATE (structural support, no proof), where
 * before the seam it came out NON_CLONE.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class HelperExtractionSeamTest {

    @Test
    void flagsHelperExtractionAsPossibleType4() throws Exception {
        String left = "class LeftInput { int f(int x){ return x * 2 + 1; } }";
        String right = "class RightInput {"
                + " int g(int y){ return h(y) + 1; }"
                + " int h(int y){ return y * 2; } }";

        NextPipelineResult result = new WalaNextPipelineRunner().run(left, right);

        // The projected Phase A structural region (f's body <-> g+h's bodies) is classified as a
        // possible Type-4: it fails T1/T2/T3 (inline vs call chain differ syntactically) but carries
        // strong structural coverage. Before Phase A, helper extraction came out NON_CLONE.
        boolean possibleT4 = result.regionDecisions().stream()
                .anyMatch(d -> d.type() == CloneRegionType.POSSIBLE_T4_CANDIDATE
                        && d.candidate().left().kind() == RegionKind.CALL_EXPANDED_REGION);
        assertTrue(possibleT4,
                "Phase A's projected cross-method structural region must flag helper extraction as a "
                        + "possible Type-4 candidate");
    }
}
