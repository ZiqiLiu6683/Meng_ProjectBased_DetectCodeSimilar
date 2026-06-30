package com.ziqi.codesim.region.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Injection-based evaluation: each case injects a known transformation (or a known non-clone) and
 * we measure whether the region selector recovers it, at what rank, with what coverage. The printed
 * report is the primary output (run with {@code -Dsurefire.useFile=false}); the assertions are only
 * a loose regression guard so the harness itself does not silently rot.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class RegionEvalTest {

    @Test
    void evaluatesInjectedTransformations() {
        List<EvalCase> corpus = corpus();
        RegionEvalHarness harness = new RegionEvalHarness();
        List<EvalResult> results = harness.evaluateAll(corpus);

        System.out.println(harness.report(results));

        // The two transformations already proven elsewhere must stay detected.
        assertDetected(results, "rename");
        assertDetected(results, "helper_extract");
        // A non-clone pair must never be reported as a clone.
        long falsePositives = results.stream().filter(EvalResult::isFalsePositive).count();
        assertEquals(0L, falsePositives, "no non-clone pair should be reported as a clone");
    }

    private static void assertDetected(List<EvalResult> results, String id) {
        EvalResult result = results.stream()
                .filter(r -> r.evalCase().id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no eval case " + id));
        assertTrue(result.detected(),
                () -> "expected to detect " + id + " (rank=" + result.rank()
                        + ", coverage=" + result.coverage() + ", error=" + result.error() + ")");
    }

    private static List<EvalCase> corpus() {
        return List.of(
                new EvalCase("rename", "T2_RENAME",
                        "public class A { public int f(int x) { return x * 2 + 1; } }",
                        "public class B { public int g(int y) { return y * 2 + 1; } }",
                        true, List.of("A.f"), List.of("B.g")),

                new EvalCase("helper_extract", "HELPER_EXTRACT",
                        "public class A { public int f(int x) { return x * 2 + 1; } }",
                        "public class B { public int g(int y) { return h(y) + 1; }"
                                + " public int h(int y) { return y * 2; } }",
                        true, List.of("A.f"), List.of("B.g", "B.h")),

                new EvalCase("inline", "INLINE",
                        "public class A { public int f(int x) { return helper(x) + 1; }"
                                + " public int helper(int x) { return x * 2; } }",
                        "public class B { public int g(int y) { return y * 2 + 1; } }",
                        true, List.of("A.f", "A.helper"), List.of("B.g")),

                new EvalCase("t3_insert", "T3_INSERT",
                        "public class A { public int f(int x) { return x * 2 + 1; } }",
                        "public class B { public int g(int y) { int z = y + 7; return y * 2 + 1; } }",
                        true, List.of("A.f"), List.of("B.g")),

                new EvalCase("noncl_diffop", "NON_CLONE",
                        "public class A { public int f(int x) { return x * 2 + 1; } }",
                        "public class B { public int g(int y) { return y - 3; } }",
                        false, List.of("A.f"), List.of("B.g")),

                new EvalCase("noncl_mulvsadd", "NON_CLONE",
                        "public class A { public int f(int x) { return x * x; } }",
                        "public class B { public int g(int y) { return y + y; } }",
                        false, List.of("A.f"), List.of("B.g"))
        );
    }
}
