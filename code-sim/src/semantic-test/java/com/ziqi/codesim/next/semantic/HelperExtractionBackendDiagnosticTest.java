package com.ziqi.codesim.next.semantic;

import com.ziqi.codesim.next.NextPipelineResult;
import com.ziqi.codesim.next.RegionKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Diagnostic (not an assertion of behaviour): runs a helper-extraction clone through the full
 * current next pipeline (source + CFG-KNN + Phase B semantic seam, but NOT Phase A structural
 * region groups) and prints what clone types it produces. This tells us whether the backend
 * already catches helper extraction (A.f inline == B.g calling B.h) or misses it -- which decides
 * whether feeding Phase A structural region groups is worth doing.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class HelperExtractionBackendDiagnosticTest {

    @Test
    void showsWhatBackendMakesOfHelperExtraction() throws Exception {
        String left = "class LeftInput { int f(int x){ return x * 2 + 1; } }";
        String right = "class RightInput {"
                + " int g(int y){ return h(y) + 1; }"
                + " int h(int y){ return y * 2; } }";

        NextPipelineResult result = new WalaNextPipelineRunner().run(left, right);
        assertNotNull(result);

        System.out.println("== Helper-extraction backend diagnostic ==");
        System.out.println("[CANDIDATES] (method<->method only)");
        result.candidates().forEach(candidate -> {
            if (candidate.left().kind() == RegionKind.METHOD
                    && candidate.right().kind() == RegionKind.METHOD) {
                System.out.println("  " + candidate.left().displayName()
                        + " <-> " + candidate.right().displayName()
                        + "  channels=" + candidate.sources().stream()
                        .map(s -> s.channel()).toList());
            }
        });
        System.out.println("[DECISIONS] (method<->method only)");
        result.regionDecisions().forEach(decision -> {
            if (decision.candidate().left().kind() == RegionKind.METHOD
                    && decision.candidate().right().kind() == RegionKind.METHOD) {
                System.out.println("  " + decision.candidate().left().displayName()
                        + " <-> " + decision.candidate().right().displayName()
                        + "  type=" + decision.type() + "  tags=" + decision.tags());
            }
        });
    }
}
