package com.ziqi.codesim.next.semantic.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Full-pipeline classification stress test. Runs a mixed, deliberately-not-tuned corpus (each type
 * plus probe cases where the correct output is genuinely uncertain) through the real WALA pipeline
 * and prints a confusion matrix + per-miss diagnostics. Report-only: the ONLY assertion is that
 * nothing crashes; a misclassification is a finding to read, not a failure to fix by tuning.
 *
 * <p>Run with:
 * {@code mvn -Psemantic-analysis -Dsemantic.tests.enabled=true -Dsurefire.useFile=false
 * -Dtest=PipelineClassificationEvalTest test}
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class PipelineClassificationEvalTest {

    @Test
    void classifiesMixedCorpusAndReports() {
        PipelineClassificationHarness harness = new PipelineClassificationHarness();
        List<PipelineClassificationHarness.Outcome> outcomes = harness.evaluateAll(corpus());

        System.out.println(harness.report(outcomes));

        long crashes = outcomes.stream().filter(o -> o.error() != null).count();
        assertEquals(0L, crashes, "no case should crash the pipeline (a wrong label is fine; a crash is not)");
    }

    private static List<ClassificationCase> corpus() {
        return List.of(
                // ---- T1: identical logic, only whitespace differs ----
                new ClassificationCase("t1_whitespace", "T1",
                        "same tokens, only formatting differs -> exact clone",
                        "class A { int f(int x){ int y = x * 2; return y + 1; } }",
                        "class A { int f(int x){int y=x*2;return y+1;} }"),

                // ---- T2: identifiers renamed ----
                new ClassificationCase("t2_rename", "T2",
                        "method + variables renamed, structure identical -> renamed clone",
                        "class A { int area(int w, int h){ int a = w * h; return a; } }",
                        "class B { int compute(int x, int y){ int r = x * y; return r; } }"),

                // ---- T2: a literal changed (behaviour differs, but syntactically still T2) ----
                new ClassificationCase("t2_literal", "T2",
                        "only a literal changed (1 -> 2); BigCloneBench counts literal edits as T2",
                        "class A { int f(int x){ return x + 1; } }",
                        "class B { int g(int y){ return y + 2; } }"),

                // ---- T3: a statement inserted mid-body, return rewired, renamed ----
                new ClassificationCase("t3_insert", "T3",
                        "extra statement inserted + renamed -> near-miss clone",
                        "class A { int f(int x){ int a=x+1; int b=a*2; return b; } }",
                        "class B { int g(int y){ int a=y+1; int b=a*2; int c=b+5; return c; } }"),

                // ---- T3: substantial edit near the T3/T4 boundary ----
                new ClassificationCase("t3_boundary", "T3",
                        "extra computation added mid-body, return rewired -> harder T3",
                        "class A { int f(int x){ int a=x+1; int b=a*2; return b-1; } }",
                        "class B { int g(int y){ int a=y+1; int b=a*2; int c=b+10; return c-1; } }"),

                // ---- T4: behaviourally equal, structurally different (SMT-provable) ----
                new ClassificationCase("t4_smt", "T4",
                        "x+x+x == y*3, no shared structure; SMT should prove it",
                        "class A { int f(int x){ return x + x + x; } }",
                        "class B { int g(int y){ return y * 3; } }"),

                // ---- T4: loop vs closed form (SMT can't prove; dynamic evidence path) ----
                new ClassificationCase("t4_loop", "T4",
                        "loop adding 2 n times == n>0?n*2:0; SMT UNKNOWN, dynamic must catch it",
                        "class A { int f(int n){ int s=0; for(int i=0;i<n;i++){ s+=2; } return s; } }",
                        "class B { int g(int n){ return n>0 ? n*2 : 0; } }"),

                // ---- T4 PROBE: helper extraction (cross-method) ----
                new ClassificationCase("t4_helper_extract", "T4",
                        "PROBE: right extracts (x+1)*(x+1) into a helper; unsure if T4_CONFIRMED via inlining or only POSSIBLE_T4",
                        "class A { int f(int x){ return (x+1)*(x+1); } }",
                        "class B { int g(int x){ return sq(x+1); } int sq(int y){ return y*y; } }"),

                // ---- NON_CLONE: different algorithms ----
                new ClassificationCase("neg_gcd_vs_sum", "NON_CLONE",
                        "euclid gcd vs running sum -- unrelated",
                        "class A { int f(int a,int b){ while(b!=0){ int t=b; b=a%b; a=t; } return a; } }",
                        "class B { int g(int n){ int s=0; for(int i=0;i<n;i++){ s+=i; } return s; } }"),

                // ---- NON_CLONE PROBE: two short arithmetic methods, unrelated ----
                new ClassificationCase("neg_short_arith", "NON_CLONE",
                        "PROBE: short unrelated arithmetic; watch for a spurious T2/T3/T4 false positive",
                        "class A { int f(int x){ return x + 1; } }",
                        "class B { int g(int x){ return x * x - 7; } }"),

                // ---- PROBE: same structure, one operator differs -> different behaviour ----
                new ClassificationCase("probe_op_diff", "T3",
                        "PROBE: identical shape, loop body 's+=i' vs 's+=2*i'; near-miss (T3) but must NOT be called T4",
                        "class A { int f(int n){ int s=0; for(int i=0;i<n;i++){ s+=i; } return s; } }",
                        "class B { int g(int n){ int s=0; for(int i=0;i<n;i++){ s+=2*i; } return s; } }"),

                // ---- PROBE: reordered independent statements ----
                new ClassificationCase("probe_reorder", "T3",
                        "PROBE: two independent statements swapped; unsure if scored T3 or slips to T4/NON_CLONE",
                        "class A { int f(int a,int b){ int x=a+b; int y=a*b; return x+y; } }",
                        "class B { int h(int a,int b){ int y=a*b; int x=a+b; return x+y; } }")
        );
    }
}
