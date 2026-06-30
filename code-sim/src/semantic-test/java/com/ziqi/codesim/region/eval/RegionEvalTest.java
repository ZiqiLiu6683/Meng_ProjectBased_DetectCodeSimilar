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

    /**
     * Exploratory: harder cases meant to probe where the selector breaks. No detection is asserted
     * (a T4 miss is a finding, not a failure) — only that nothing crashes. Read the printed report.
     */
    @Test
    void exploresHardCases() {
        List<EvalCase> hard = hardCorpus();
        RegionEvalHarness harness = new RegionEvalHarness();
        List<EvalResult> results = harness.evaluateAll(hard);

        System.out.println(harness.report(results));

        long errors = results.stream().filter(r -> r.error() != null).count();
        assertEquals(0L, errors, "hard cases should run without analysis crashes");
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

    private static List<EvalCase> hardCorpus() {
        return List.of(
                // Renamed loop clone: exercises phi nodes and loop back-edges.
                new EvalCase("loop_sum", "T2_LOOP",
                        "public class A { public int f(int n){ int s=0; for(int i=0;i<n;i++){ s+=i; } return s; } }",
                        "public class B { public int g(int m){ int t=0; for(int j=0;j<m;j++){ t+=j; } return t; } }",
                        true, List.of("A.f"), List.of("B.g")),

                // Independent statements reordered (real T3).
                new EvalCase("reorder", "T3_REORDER",
                        "public class A { public int f(int x){ int a=x+1; int b=x*2; return a+b; } }",
                        "public class B { public int g(int y){ int b=y*2; int a=y+1; return a+b; } }",
                        true, List.of("A.f"), List.of("B.g")),

                // Real clone f<->g hidden among distractor methods on both sides.
                new EvalCase("partial_distractors", "PARTIAL",
                        "public class A { public int f(int x){ return x*2+1; } public int noise1(int a){ return a-9; } }",
                        "public class B { public int junk(int p){ return p/4; }"
                                + " public int g(int y){ return y*2+1; } public int more(int q){ return q+100; } }",
                        true, List.of("A.f"), List.of("B.g")),

                // T4 semantic: x*2 vs y+y — same behaviour, different operation (expected MISS;
                // this is the case that would justify Phase B's SMT layer).
                new EvalCase("t4_mul_vs_add", "T4_SEMANTIC",
                        "public class A { public int f(int x){ return x*2; } }",
                        "public class B { public int g(int y){ return y+y; } }",
                        true, List.of("A.f"), List.of("B.g")),

                // T4 semantic: loop sum vs closed-form formula — same behaviour, totally different
                // structure (expected MISS; only dynamic/SMT could relate these).
                new EvalCase("t4_loop_vs_formula", "T4_SEMANTIC",
                        "public class A { public int f(int n){ int s=0; for(int i=0;i<n;i++){ s+=i; } return s; } }",
                        "public class B { public int g(int n){ return n*(n-1)/2; } }",
                        true, List.of("A.f"), List.of("B.g")),

                // T3 with one constant changed: most structure identical (probes edit tolerance).
                new EvalCase("t3_changed_const", "T3_EDIT",
                        "public class A { public int f(int x){ return x*2 + x*3; } }",
                        "public class B { public int g(int y){ return y*2 + y*4; } }",
                        true, List.of("A.f"), List.of("B.g")),

                // Precision probe (negative): same mul-then-add shape but different constants. WL
                // ignores constants, so this may over-match — a real precision finding if it does.
                new EvalCase("near_miss", "PRECISION_PROBE",
                        "public class A { public int f(int x){ return x*2+1; } }",
                        "public class B { public int g(int y){ return y*3+2; } }",
                        false, List.of("A.f"), List.of("B.g"))
        );
    }
}
