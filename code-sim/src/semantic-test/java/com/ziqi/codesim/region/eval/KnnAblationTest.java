package com.ziqi.codesim.region.eval;

import com.ziqi.codesim.region.CloneAnalyzer;
import com.ziqi.codesim.region.seed.SeedMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ablation for the provisional KNN seed layer. Runs the structural corpus with bucket+KNN vs
 * bucket-only seeds and compares recall. Hypothesis: region growth expands from any one seed, so
 * the exact-hash bucket anchors already suffice and the quadratic KNN layer adds no region-level
 * recall. If bucket-only recall is not worse, KNN is redundant and can default off.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class KnnAblationTest {

    @Test
    void knnDoesNotImproveRecallOverBucketSeeds() {
        List<EvalCase> corpus = structuralCorpus();
        RegionEvalHarness full = new RegionEvalHarness(new CloneAnalyzer(SeedMatcher.withKnn()), 3, 1.0);
        RegionEvalHarness bucketOnly = new RegionEvalHarness(new CloneAnalyzer(SeedMatcher.bucketOnly()), 3, 1.0);

        int fullDetected = 0;
        int bucketDetected = 0;
        System.out.println("== KNN ablation (structural corpus) ==");
        System.out.printf("%-20s %-14s %-14s%n", "case", "bucket+KNN", "bucket-only");
        for (EvalCase c : corpus) {
            boolean f = full.evaluate(c).detected();
            boolean b = bucketOnly.evaluate(c).detected();
            fullDetected += f ? 1 : 0;
            bucketDetected += b ? 1 : 0;
            System.out.printf("%-20s %-14s %-14s%s%n", c.id(),
                    f ? "YES" : "no", b ? "YES" : "no", f != b ? "   <-- DIFFERS" : "");
        }
        System.out.printf("recall: bucket+KNN=%d/%d  bucket-only=%d/%d%n",
                fullDetected, corpus.size(), bucketDetected, corpus.size());

        assertTrue(bucketDetected >= fullDetected,
                "bucket-only recall should not be worse than bucket+KNN; if it is, KNN genuinely helps "
                        + "and must stay (bucket-only=" + bucketDetected + ", full=" + fullDetected + ")");
    }

    private static List<EvalCase> structuralCorpus() {
        return List.of(
                new EvalCase("rename", "T2",
                        "public class A { public int f(int x){ return x*2+1; } }",
                        "public class B { public int g(int y){ return y*2+1; } }",
                        true, List.of("A.f"), List.of("B.g")),
                new EvalCase("helper_extract", "HELPER",
                        "public class A { public int f(int x){ return x*2+1; } }",
                        "public class B { public int g(int y){ return h(y)+1; } public int h(int y){ return y*2; } }",
                        true, List.of("A.f"), List.of("B.g", "B.h")),
                new EvalCase("inline", "INLINE",
                        "public class A { public int f(int x){ return helper(x)+1; } public int helper(int x){ return x*2; } }",
                        "public class B { public int g(int y){ return y*2+1; } }",
                        true, List.of("A.f", "A.helper"), List.of("B.g")),
                new EvalCase("loop_sum", "T2_LOOP",
                        "public class A { public int f(int n){ int s=0; for(int i=0;i<n;i++){ s+=i; } return s; } }",
                        "public class B { public int g(int m){ int t=0; for(int j=0;j<m;j++){ t+=j; } return t; } }",
                        true, List.of("A.f"), List.of("B.g")),
                new EvalCase("reorder", "T3",
                        "public class A { public int f(int x){ int a=x+1; int b=x*2; return a+b; } }",
                        "public class B { public int g(int y){ int b=y*2; int a=y+1; return a+b; } }",
                        true, List.of("A.f"), List.of("B.g")),
                new EvalCase("t3_changed_const", "T3",
                        "public class A { public int f(int x){ return x*2 + x*3; } }",
                        "public class B { public int g(int y){ return y*2 + y*4; } }",
                        true, List.of("A.f"), List.of("B.g")),
                new EvalCase("partial_distractors", "PARTIAL",
                        "public class A { public int f(int x){ return x*2+1; } public int noise(int a){ return a-9; } }",
                        "public class B { public int junk(int p){ return p/4; } public int g(int y){ return y*2+1; } }",
                        true, List.of("A.f"), List.of("B.g"))
        );
    }
}
