package com.ziqi.codesim.region;

import com.ziqi.codesim.next.CloneRegionType;
import com.ziqi.codesim.next.NextEvidenceExtractor;
import com.ziqi.codesim.next.NextRegionTypeRecognizer;
import com.ziqi.codesim.next.RegionCandidate;
import com.ziqi.codesim.next.RegionDecision;
import com.ziqi.codesim.region.descriptor.NodeDescriptorBuilder;
import com.ziqi.codesim.region.grow.RegionGroup;
import com.ziqi.codesim.region.grow.RegionGrower;
import com.ziqi.codesim.region.model.NodeDescriptor;
import com.ziqi.codesim.region.model.SemanticGraph;
import com.ziqi.codesim.region.sdg.SdgBuilder;
import com.ziqi.codesim.region.seed.SeedMatcher;
import com.ziqi.codesim.region.seed.SeedPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the boundary-free reconstruction feeding the UNCHANGED recognizer. Covers the agreed edge
 * cases: intra-loop edit (statements切开 inside loops), a statement split into several, same-line
 * packing, and helper extraction. Sources are multi-line (one statement per line) except the
 * deliberate same-line probe.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class AlignedRegionReconstructionTest {

    @TempDir
    Path tempDir;

    @Test
    void identicalBodiesReadAsT1() throws Exception {
        assertEquals(CloneRegionType.T1, classify(
                "class A {\n int f(int x){\n  int a=x+1;\n  int b=a*2;\n  return b;\n }\n}", "f",
                "class B {\n int f(int x){\n  int a=x+1;\n  int b=a*2;\n  return b;\n }\n}", "f"));
    }

    @Test
    void changedLiteralReadsAsT2() throws Exception {
        assertEquals(CloneRegionType.T2, classify(
                "class A {\n int f(int x){\n  int a=x+1;\n  int b=a*2;\n  return b;\n }\n}", "f",
                "class B {\n int g(int y){\n  int a=y+1;\n  int b=a*3;\n  return b;\n }\n}", "g"));
    }

    @Test
    void insertedStatementReadsAsT3() throws Exception {
        assertEquals(CloneRegionType.T3, classify(
                "class A {\n int f(int x){\n  int a=x+1;\n  int b=a*2;\n  return b;\n }\n}", "f",
                "class B {\n int g(int y){\n  int a=y+1;\n  int b=a*2;\n  int c=b+7;\n  return c;\n }\n}", "g"));
    }

    // 1b: an edit INSIDE the loop (an inserted early-exit flag) must be seen as a statement insert.
    @Test
    void intraLoopEditReadsAsT3() throws Exception {
        CloneRegionType type = classify(
                "class A {\n void f(int[] a){\n  for(int i=0;i<a.length;i++){\n   for(int j=0;j<a.length-1;j++){\n    if(a[j]>a[j+1]){\n     int t=a[j];\n     a[j]=a[j+1];\n     a[j+1]=t;\n    }\n   }\n  }\n }\n}", "f",
                "class B {\n void g(int[] a){\n  for(int i=0;i<a.length;i++){\n   boolean sw=false;\n   for(int j=0;j<a.length-1;j++){\n    if(a[j]>a[j+1]){\n     int t=a[j];\n     a[j]=a[j+1];\n     a[j+1]=t;\n     sw=true;\n    }\n   }\n   if(!sw){ break; }\n  }\n }\n}", "g");
        assertEquals(CloneRegionType.T3, type, "an inserted flag inside the loop must read as T3");
    }

    // 2: one statement split into several -> a near-miss clone (never dropped by forced one-to-one).
    @Test
    void splitStatementIsANearMissClone() throws Exception {
        CloneRegionType type = classify(
                "class A {\n int f(int a,int b,int d,int e){\n  int p=a+e;\n  int q=p*b;\n  int c=a*b+d;\n  int r=c+q;\n  return r;\n }\n}", "f",
                "class B {\n int g(int a,int b,int d,int e){\n  int p=a+e;\n  int q=p*b;\n  int t=a*b;\n  int c=t+d;\n  int r=c+q;\n  return r;\n }\n}", "g");
        assertNotEquals(CloneRegionType.NON_CLONE, type,
                "a split statement is a near-miss clone, not a non-clone; got " + type);
    }

    // 1a: two statements on ONE line -> line granularity can't separate them, but token comparison
    // is unaffected, so an identical same-line clone still reads as T1.
    @Test
    void sameLineStatementsStillReadAsT1() throws Exception {
        assertEquals(CloneRegionType.T1, classify(
                "class A {\n int f(int x){\n  int a=x+1; int b=a*2;\n  return b;\n }\n}", "f",
                "class B {\n int f(int x){\n  int a=x+1; int b=a*2;\n  return b;\n }\n}", "f"));
    }

    @Test
    void helperExtractionReadsAsSyntacticCloneNotT4() throws Exception {
        CloneRegionType type = classify(
                "class A {\n int f(int x){\n  int t=x+1;\n  return t*t;\n }\n}", "f",
                "class B {\n int g(int x){\n  int t=x+1;\n  return sq(t);\n }\n int sq(int y){\n  return y*y;\n }\n}", "g");
        assertTrue(type == CloneRegionType.T1 || type == CloneRegionType.T2 || type == CloneRegionType.T3,
                "helper extraction must read as a syntactic clone, not be forced to T4; got " + type);
    }

    // Region-vs-region, not method-vs-method: a shared fragment (a gcd loop) buried in two methods
    // whose SURROUNDING code differs must be recovered as a clone -- the whole-method span would
    // dilute it below the T3 threshold and miss it.
    @Test
    void fragmentCloneInsideDifferentMethodsIsRecovered() throws Exception {
        CloneRegionType type = classify(
                "class A {\n int f(int a,int b){\n  int p=a+1;\n  int q=p*2;\n  int u=q-3;\n  int v=u+p;\n  while(b!=0){\n   int t=b;\n   b=a%b;\n   a=t;\n  }\n  return a;\n }\n}", "f",
                "class B {\n int g(int x,int y){\n  int m=x*x;\n  int n=m+y;\n  int o=n*n;\n  int w=o-m;\n  while(y!=0){\n   int t=y;\n   y=x%y;\n   x=t;\n  }\n  return x;\n }\n}", "g");
        assertNotEquals(CloneRegionType.NON_CLONE, type,
                "the shared gcd-loop fragment must be recovered region-vs-region, not diluted by the "
                        + "different surrounding code; got " + type);
    }

    private CloneRegionType classify(String leftSource, String leftHint,
                                     String rightSource, String rightHint) throws Exception {
        Path leftClasses = compile("left", leftSource);
        Path rightClasses = compile("right", rightSource);
        SemanticGraph leftGraph = new SdgBuilder().build(leftClasses, "left");
        SemanticGraph rightGraph = new SdgBuilder().build(rightClasses, "right");
        Map<Integer, NodeDescriptor> leftDesc = new NodeDescriptorBuilder().build(leftGraph);
        Map<Integer, NodeDescriptor> rightDesc = new NodeDescriptorBuilder().build(rightGraph);
        List<SeedPair> seeds = new SeedMatcher().match(leftDesc, rightDesc);
        List<RegionGroup> regions = new RegionGrower().grow(leftGraph, leftDesc, rightGraph, rightDesc, seeds);

        RegionGroup best = regions.stream()
                .filter(r -> covers(r.leftMethods(), leftHint) && covers(r.rightMethods(), rightHint))
                .max(Comparator.comparingDouble(RegionGroup::priority))
                .orElse(null);
        assertNotNull(best, "expected a region linking " + leftHint + " and " + rightHint);

        RegionAlignmentExtractor.Input input =
                new RegionAlignmentExtractor().extract(best, leftGraph, rightGraph);
        RegionCandidate candidate = NextEvidenceExtractor.reconstructAlignedRegion(
                leftSource, input.leftSpanLines(), rightSource, input.rightSpanLines(),
                input.crossMethod(), "1");
        RegionDecision decision = new NextRegionTypeRecognizer().decide(candidate);
        System.out.println(leftHint + "->" + rightHint + " : " + decision.type()
                + "  path=" + decision.decisionPath());
        return decision.type();
    }

    private static boolean covers(Set<String> methods, String hint) {
        return methods.stream().anyMatch(m -> m.contains("." + hint + "("));
    }

    private Path compile(String pkg, String source) throws Exception {
        Path sourceDir = tempDir.resolve(pkg + "/src");
        Path classesDir = tempDir.resolve(pkg + "/classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);
        String typeName = source.replaceFirst("(?s).*?class\\s+([A-Za-z_$][A-Za-z0-9_$]*).*", "$1");
        Path sourceFile = sourceDir.resolve(typeName + ".java");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "reconstruction test requires a JDK compiler");
        int exitCode = compiler.run(null, null, null, "-g", "-d", classesDir.toString(), sourceFile.toString());
        assertTrue(exitCode == 0, () -> "javac failed for " + Objects.toString(sourceFile));
        return classesDir;
    }
}
