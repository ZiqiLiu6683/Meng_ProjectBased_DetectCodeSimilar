package com.ziqi.codesim.next.semantic.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HARD full-pipeline stress corpus, aimed squarely at where the pipeline is expected to break:
 * longer methods, NON-int signatures (String, int[]), recursion, and adversarial negatives. Several
 * cases are deliberately chosen because they should expose known limits -- e.g. behaviourally-equal
 * Type-4 pairs over String/array inputs (the dynamic layer only samples all-int signatures, and the
 * SMT summariser gives up on arrays/recursion), which are expected to MISS as NON_CLONE.
 *
 * <p>Report-only: the only assertion is no crash. Read the confusion matrix and "Misses (why)" --
 * red cells here are the actual findings that tell us which extension to build next.
 *
 * <p>{@code mvn -Psemantic-analysis -Dsemantic.tests.enabled=true -Dsurefire.useFile=false
 * -Dtest=PipelineClassificationHardEvalTest test}
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class PipelineClassificationHardEvalTest {

    @Test
    void stressesHarderCorpusAndReports() {
        PipelineClassificationHarness harness = new PipelineClassificationHarness();
        List<PipelineClassificationHarness.Outcome> outcomes = harness.evaluateAll(corpus());

        System.out.println(harness.report(outcomes));

        long crashes = outcomes.stream().filter(o -> o.error() != null).count();
        assertEquals(0L, crashes, "no case should crash the pipeline (a wrong label is a finding, a crash is not)");
    }

    private static List<ClassificationCase> corpus() {
        return List.of(
                // ---- T2 over a non-int signature: bubble sort on int[], fully renamed ----
                new ClassificationCase("t2_sort_rename", "T2",
                        "bubble sort renamed; non-int (int[]) signature, syntactic ladder should still work",
                        "class A { void f(int[] a){ for(int i=0;i<a.length;i++){ for(int j=0;j<a.length-1;j++){ if(a[j]>a[j+1]){ int t=a[j]; a[j]=a[j+1]; a[j+1]=t; } } } } }",
                        "class B { void g(int[] xs){ for(int p=0;p<xs.length;p++){ for(int q=0;q<xs.length-1;q++){ if(xs[q]>xs[q+1]){ int t=xs[q]; xs[q]=xs[q+1]; xs[q+1]=t; } } } } }"),

                // ---- T3: bubble sort with an inserted early-exit flag ----
                new ClassificationCase("t3_sort_earlyexit", "T3",
                        "bubble sort + inserted 'swapped' early-exit flag -> near-miss clone",
                        "class A { void f(int[] a){ for(int i=0;i<a.length;i++){ for(int j=0;j<a.length-1;j++){ if(a[j]>a[j+1]){ int t=a[j]; a[j]=a[j+1]; a[j+1]=t; } } } } }",
                        "class B { void g(int[] a){ for(int i=0;i<a.length;i++){ boolean sw=false; for(int j=0;j<a.length-1;j++){ if(a[j]>a[j+1]){ int t=a[j]; a[j]=a[j+1]; a[j+1]=t; sw=true; } } if(!sw){ break; } } } }"),

                // ---- T3: String-processing near-miss (non-int, syntactic) ----
                new ClassificationCase("t3_string_edit", "T3",
                        "count vowels, one extra character added to the set -> near-miss over String",
                        "class A { int f(String s){ int c=0; for(int i=0;i<s.length();i++){ char ch=s.charAt(i); if(ch=='a'||ch=='e'||ch=='i'){ c++; } } return c; } }",
                        "class B { int g(String s){ int c=0; for(int i=0;i<s.length();i++){ char ch=s.charAt(i); if(ch=='a'||ch=='e'||ch=='i'||ch=='o'){ c++; } } return c; } }"),

                // ---- T4 (int, recursion): factorial iterative vs recursive; dynamic SHOULD catch it ----
                new ClassificationCase("t4_factorial_iter_rec", "T4",
                        "factorial loop vs recursion, all-int; SMT UNKNOWN (recursion) -> dynamic should sample it",
                        "class A { int f(int n){ int r=1; for(int i=2;i<=n;i++){ r*=i; } return r; } }",
                        "class B { int g(int n){ if(n<=1){ return 1; } return n*g(n-1); } }"),

                // ---- T4 (int): x^3 via loop vs multiply; dynamic or SMT ----
                new ClassificationCase("t4_power_loop", "T4",
                        "cube via loop vs x*x*x, all-int, structurally different",
                        "class A { int f(int x){ int r=1; for(int i=0;i<3;i++){ r*=x; } return r; } }",
                        "class B { int g(int x){ return x*x*x; } }"),

                // ---- T4 PROBE (non-int): String reversed two ways; expected MISS ----
                new ClassificationCase("t4_string_reverse", "T4",
                        "PROBE: StringBuilder.reverse vs manual char-swap; same behaviour, String signature -> expected MISS (non-int)",
                        "class A { String f(String s){ return new StringBuilder(s).reverse().toString(); } }",
                        "class B { String g(String s){ char[] c=s.toCharArray(); int i=0,j=c.length-1; while(i<j){ char t=c[i]; c[i]=c[j]; c[j]=t; i++; j--; } return new String(c); } }"),

                // ---- T4 PROBE (int[] + recursion): sum loop vs recursive sum; expected MISS ----
                new ClassificationCase("t4_array_sum_rec", "T4",
                        "PROBE: array sum via loop vs recursion; same behaviour, int[] signature + recursion -> expected MISS",
                        "class A { int f(int[] a){ int s=0; for(int i=0;i<a.length;i++){ s+=a[i]; } return s; } }",
                        "class B { int g(int[] a){ return sum(a,0); } int sum(int[] a,int i){ if(i>=a.length){ return 0; } return a[i]+sum(a,i+1); } }"),

                // ---- Boundary T3/T4: half the body rewritten, behaviour differs -> still a near-miss ----
                new ClassificationCase("boundary_half_rewrite", "T3",
                        "PROBE: two of four statements changed and result differs -> near-miss T3, must not slip to T4/NON_CLONE",
                        "class A { int f(int x){ int a=x+1; int b=a*2; int c=b-3; return c; } }",
                        "class B { int g(int y){ int a=y+2; int b=a*2; int c=b+7; return c; } }"),

                // ---- Adversarial NEGATIVE: similar loop skeleton, different algorithm (max vs sum) ----
                new ClassificationCase("neg_max_vs_sum", "NON_CLONE",
                        "PROBE: same array-loop skeleton but max vs sum -- unrelated functions; false-positive risk (label debatable)",
                        "class A { int f(int[] a){ int m=a[0]; for(int i=1;i<a.length;i++){ if(a[i]>m){ m=a[i]; } } return m; } }",
                        "class B { int g(int[] a){ int s=0; for(int i=0;i<a.length;i++){ s+=a[i]; } return s; } }"),

                // ---- Clear NEGATIVE: genuinely unrelated larger methods ----
                new ClassificationCase("neg_unrelated_large", "NON_CLONE",
                        "binary search vs euclid gcd -- unrelated algorithms",
                        "class A { int f(int[] a,int key){ int lo=0,hi=a.length-1; while(lo<=hi){ int mid=(lo+hi)/2; if(a[mid]==key){ return mid; } else if(a[mid]<key){ lo=mid+1; } else { hi=mid-1; } } return -1; } }",
                        "class B { int g(int a,int b){ while(b!=0){ int t=b; b=a%b; a=t; } return a; } }")
        );
    }
}
