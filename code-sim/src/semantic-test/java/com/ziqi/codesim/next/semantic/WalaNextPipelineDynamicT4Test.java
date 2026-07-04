package com.ziqi.codesim.next.semantic;

import com.ziqi.codesim.next.CloneRegionType;
import com.ziqi.codesim.next.NextPipelineResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end seam check for the DYNAMIC T4 tier. {@code loopSum2} adds 2 in a loop {@code n} times;
 * {@code formula} is its closed form {@code n>0 ? n*2 : 0}. They are behaviourally identical, but the
 * loop makes SMT (Phase B) return UNKNOWN -- so the pipeline must fall through to the dynamic layer,
 * which samples I/O and reports {@link CloneRegionType#T4_DYNAMIC_EVIDENCE} (evidence, not proof).
 * The pair is deliberately NOT reported as T4_CONFIRMED, keeping the proof/evidence distinction.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class WalaNextPipelineDynamicT4Test {

    @Test
    void evidencesLoopType4CloneWhenSmtCannotProveIt() throws Exception {
        String left = "class LeftInput { int loopSum2(int n) { int s = 0; for (int i = 0; i < n; i++) { s += 2; } return s; } }";
        String right = "class RightInput { int formula(int n) { return n > 0 ? n * 2 : 0; } }";

        NextPipelineResult result = new WalaNextPipelineRunner().run(left, right);

        boolean dynamicT4 = result.regionDecisions().stream()
                .anyMatch(decision -> decision.type() == CloneRegionType.T4_DYNAMIC_EVIDENCE);
        assertTrue(dynamicT4,
                "a loop and its closed form: SMT cannot prove it, so the dynamic layer must surface "
                        + "T4_DYNAMIC_EVIDENCE from I/O sampling");

        boolean confirmedT4 = result.regionDecisions().stream()
                .anyMatch(decision -> decision.type() == CloneRegionType.T4_CONFIRMED);
        assertFalse(confirmedT4,
                "dynamic evidence must not be reported as an SMT-confirmed T4");
    }
}
