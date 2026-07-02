package com.ziqi.codesim.next.semantic;

import com.ziqi.codesim.next.CloneRegionType;
import com.ziqi.codesim.next.NextPipelineResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end seam check: two methods with no shared tokens or structure ({@code x+x+x} vs
 * {@code y*3}) but the same behaviour must flow through the full next pipeline and come out as a
 * confirmed Type-4 clone — Phase B both proposes the pair (semantic candidate) and confirms it
 * (equivalence oracle). This is behaviour the source-only pipeline could never produce.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class WalaNextPipelineT4Test {

    @Test
    void confirmsSemanticType4CloneThroughFullPipeline() throws Exception {
        String left = "class LeftInput { int f(int x) { return x + x + x; } }";
        String right = "class RightInput { int g(int y) { return y * 3; } }";

        NextPipelineResult result = new WalaNextPipelineRunner().run(left, right);

        boolean confirmedT4 = result.regionDecisions().stream()
                .anyMatch(decision -> decision.type() == CloneRegionType.T4_CONFIRMED);
        assertTrue(confirmedT4,
                "x+x+x and y*3 are behaviourally identical but structurally different; the pipeline "
                        + "must confirm a Type-4 clone via the SMT equivalence proof");
    }
}
