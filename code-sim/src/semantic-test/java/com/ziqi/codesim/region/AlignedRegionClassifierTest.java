package com.ziqi.codesim.region;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the boundary-free, alignment-driven T1/T2/T3 classifier on the two scenarios that
 * motivated it: ordinary same-method clones (T1/T2/T3 read off the aligned content), and -- the
 * whole point -- a HELPER-EXTRACTION cross-method clone, which the source-projection path forced to
 * T4 but which is really a syntactic near-copy once boundaries are erased.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class AlignedRegionClassifierTest {

    @TempDir
    Path tempDir;

    @Test
    void identicalBodiesAreT1() throws Exception {
        AlignedRegionClassifier.Verdict v = classifyPair(
                "class A { int f(int x){ int a=x+1; int b=a*2; return b; } }", "A.f",
                "class B { int f(int x){ int a=x+1; int b=a*2; return b; } }", "B.f");
        System.out.println("identical -> " + v);
        assertEquals(AlignedRegionClassifier.SyntacticType.T1, v.type(),
                "identical computation (only names differ) reads as T1 at the SSA level");
    }

    @Test
    void changedLiteralIsT2() throws Exception {
        AlignedRegionClassifier.Verdict v = classifyPair(
                "class A { int f(int x){ int a=x+1; int b=a*2; return b; } }", "A.f",
                "class B { int g(int y){ int a=y+1; int b=a*3; return b; } }", "B.g");
        System.out.println("literal changed -> " + v);
        assertEquals(AlignedRegionClassifier.SyntacticType.T2, v.type(),
                "same structure, one literal changed (2 -> 3) reads as T2");
    }

    @Test
    void insertedStatementIsT3() throws Exception {
        AlignedRegionClassifier.Verdict v = classifyPair(
                "class A { int f(int x){ int a=x+1; int b=a*2; return b; } }", "A.f",
                "class B { int g(int y){ int a=y+1; int b=a*2; int c=b+7; return c; } }", "B.g");
        System.out.println("inserted statement -> " + v);
        assertEquals(AlignedRegionClassifier.SyntacticType.T3, v.type(),
                "an extra substantive statement on one side is an insert -> T3");
    }

    @Test
    void helperExtractionIsSyntacticNotForcedToT4() throws Exception {
        AlignedRegionClassifier.Verdict v = classifyPair(
                "class A { int f(int x){ int t=x+1; return t*t; } }", "A.f",
                "class B { int g(int x){ int t=x+1; return sq(t); } int sq(int y){ return y*y; } }", "B.g");
        System.out.println("helper extraction -> " + v);
        assertNotEquals(AlignedRegionClassifier.SyntacticType.NONE, v.type(),
                "boundary-free: a helper-extracted near-copy must classify as a syntactic clone, "
                        + "not fall through to the behavioural T4 path");
        assertTrue(v.type() == AlignedRegionClassifier.SyntacticType.T1
                        || v.type() == AlignedRegionClassifier.SyntacticType.T2
                        || v.type() == AlignedRegionClassifier.SyntacticType.T3,
                "helper extraction is a T1/T2/T3 clone on its aligned content");
    }

    /** Compile both sides, build graphs, grow regions, pick the one covering the method pair, classify. */
    private AlignedRegionClassifier.Verdict classifyPair(String leftSource, String leftHint,
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
        return new AlignedRegionClassifier().classify(best, leftGraph, rightGraph);
    }

    private static boolean covers(java.util.Set<String> methods, String hint) {
        return methods.stream().anyMatch(m -> m.contains(hint));
    }

    private Path compile(String pkg, String source) throws Exception {
        Path sourceDir = tempDir.resolve(pkg + "/src");
        Path classesDir = tempDir.resolve(pkg + "/classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);
        String typeName = source.replaceFirst(".*class\\s+([A-Za-z_$][A-Za-z0-9_$]*).*", "$1");
        Path sourceFile = sourceDir.resolve(typeName + ".java");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "classifier test requires a JDK compiler");
        int exitCode = compiler.run(null, null, null, "-g", "-d", classesDir.toString(), sourceFile.toString());
        assertTrue(exitCode == 0, () -> "javac failed for " + Objects.toString(sourceFile));
        return classesDir;
    }
}
