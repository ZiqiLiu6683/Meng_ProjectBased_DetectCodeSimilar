package com.ziqi.codesim.next.semantic;

import com.ziqi.codesim.next.NextPipelineResult;
import com.ziqi.codesim.next.RegionKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Diagnostic: two unrelated methods that merely SHARE a small fragment ({@code _*2+1}) inside
 * otherwise-different computations. Phase A will grow a tiny aligned region on that fragment, whose
 * ABSOLUTE coverage (~2) clears the recognizer's 2.0 floor. If f<->g comes out POSSIBLE_T4_CANDIDATE,
 * the absolute-coverage signal is producing a false positive -- which a coverage RATIO would prevent
 * (2 aligned ops out of a large method is a tiny fraction). Report-only; read the printed type.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class CoverageFalsePositiveDiagnosticTest {

    @Test
    void showsWhetherSharedFragmentFalselyFlagsPossibleT4() throws Exception {
        String left = "class LeftInput {"
                + " int f(int a, int b){ int p = a*2+1; return p + b*b*b; } }";
        String right = "class RightInput {"
                + " int g(int x, int y){ int q = x*2+1; return q - y%7 - y%3; } }";

        NextPipelineResult result = new WalaNextPipelineRunner().run(left, right);
        assertNotNull(result);

        System.out.println("== Coverage false-positive diagnostic ==");
        System.out.println("(f and g are unrelated but both contain _*2+1)");
        result.regionDecisions().forEach(decision -> {
            if (decision.candidate().left().kind() == RegionKind.METHOD
                    && decision.candidate().right().kind() == RegionKind.METHOD) {
                System.out.println("  " + decision.candidate().left().displayName()
                        + " <-> " + decision.candidate().right().displayName()
                        + "  type=" + decision.type());
            }
        });
    }
}
