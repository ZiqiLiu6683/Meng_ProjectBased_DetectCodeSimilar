package com.ziqi.codesim.region.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stress test on harder, more realistic methods (nested control flow, loops with branches, a
 * multi-method algorithm, a substantial T3 edit, and a realistic non-clone) to find where the
 * selector actually breaks. Report-only: no detection is asserted (a miss is a finding), only that
 * nothing crashes. Read the printed report.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class HarderCorpusEvalTest {

    @Test
    void stressesSelectorOnHarderMethods() {
        List<EvalCase> corpus = harderCorpus();
        RegionEvalHarness harness = new RegionEvalHarness();
        List<EvalResult> results = harness.evaluateAll(corpus);

        System.out.println(harness.report(results));

        long errors = results.stream().filter(r -> r.error() != null).count();
        assertEquals(0L, errors, "harder cases should run without analysis crashes");
    }

    private static List<EvalCase> harderCorpus() {
        return List.of(
                // Nested control flow: if inside a loop over an array (phi + branches), renamed.
                new EvalCase("nested_control", "T2_NESTED",
                        "public class A { public int f(int[] a){ int s=0; for(int i=0;i<a.length;i++){ if(a[i]>0){ s+=a[i]; } } return s; } }",
                        "public class B { public int g(int[] xs){ int r=0; for(int k=0;k<xs.length;k++){ if(xs[k]>0){ r+=xs[k]; } } return r; } }",
                        true, List.of("A.f"), List.of("B.g")),

                // Euclid gcd loop with in-loop reassignment, renamed variables.
                new EvalCase("gcd_loop", "T2_GCD",
                        "public class A { public int f(int a,int b){ while(b!=0){ int t=b; b=a%b; a=t; } return a; } }",
                        "public class B { public int g(int x,int y){ while(y!=0){ int t=y; y=x%y; x=t; } return x; } }",
                        true, List.of("A.f"), List.of("B.g")),

                // Multi-method: a method calling a helper twice, cloned across files.
                new EvalCase("multi_method", "T2_MULTI",
                        "public class A { public int f(int x){ return sq(x)+sq(x+1); } public int sq(int y){ return y*y; } }",
                        "public class B { public int g(int x){ return sq(x)+sq(x+1); } public int sq(int y){ return y*y; } }",
                        true, List.of("A.f"), List.of("B.g")),

                // Substantial T3: an extra statement inserted mid-body and the return rewired.
                new EvalCase("harder_t3", "T3_HARD",
                        "public class A { public int f(int x){ int a=x+1; int b=a*2; return b-1; } }",
                        "public class B { public int g(int y){ int a=y+1; int b=a*2; int c=b+10; return c-1; } }",
                        true, List.of("A.f"), List.of("B.g")),

                // Realistic non-clone: gcd vs a running sum -- different algorithms, must not match.
                new EvalCase("realistic_negative", "NON_CLONE",
                        "public class A { public int f(int a,int b){ while(b!=0){ int t=b; b=a%b; a=t; } return a; } }",
                        "public class B { public int g(int n){ int s=0; for(int i=0;i<n;i++){ s+=i; } return s; } }",
                        false, List.of("A.f"), List.of("B.g"))
        );
    }
}
