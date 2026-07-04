package com.ziqi.codesim.region.semantic;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Region-facing Phase B: verifies a SUB-METHOD region is behaviourally equivalent even though the
 * whole methods are not. Both {@code f} and {@code g} compute {@code (v+1)*2} internally but then
 * diverge ({@code +100} vs {@code -50}); the aligned sub-region up to {@code (v+1)*2} must be proven
 * EQUIVALENT -- something method-level Phase B (which only compares whole return values) cannot do.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class RegionEquivalenceVerifierTest {

    @TempDir
    Path tempDir;

    @Test
    void provesSubMethodRegionEquivalentDespiteDifferentMethods() throws Exception {
        Path leftClasses = compile("left", "A",
                "public class A { public int f(int x){ int a=x+1; int b=a*2; return b+100; } }");
        Path rightClasses = compile("right", "B",
                "public class B { public int g(int y){ int c=y+1; int d=c*2; return d-50; } }");

        SemanticGraph leftGraph = new SdgBuilder().build(leftClasses, "A.java");
        SemanticGraph rightGraph = new SdgBuilder().build(rightClasses, "B.java");
        Map<Integer, NodeDescriptor> leftDesc = new NodeDescriptorBuilder().build(leftGraph);
        Map<Integer, NodeDescriptor> rightDesc = new NodeDescriptorBuilder().build(rightGraph);
        List<SeedPair> seeds = new SeedMatcher().match(leftDesc, rightDesc);
        List<RegionGroup> regions = new RegionGrower().grow(leftGraph, leftDesc, rightGraph, rightDesc, seeds);

        Map<String, RegionEquivalenceVerifier.MethodIr> leftIrs = RegionEquivalenceVerifier.methodIrs(leftClasses);
        Map<String, RegionEquivalenceVerifier.MethodIr> rightIrs = RegionEquivalenceVerifier.methodIrs(rightClasses);
        RegionEquivalenceVerifier verifier = new RegionEquivalenceVerifier();

        boolean anyEquivalent = regions.stream()
                .map(region -> verifier.verify(region, leftGraph, rightGraph, leftIrs, rightIrs))
                .anyMatch(verdict -> verdict == EquivalenceVerdict.EQUIVALENT);
        assertTrue(anyEquivalent,
                "the aligned (v+1)*2 sub-region must be proven equivalent even though f and g differ overall");
    }

    private Path compile(String pkg, String type, String source) throws Exception {
        Path sourceDir = tempDir.resolve(pkg + "/src");
        Path classesDir = tempDir.resolve(pkg + "/classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);
        Path sourceFile = sourceDir.resolve(type + ".java");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "verifier test requires a JDK compiler");
        int exitCode = compiler.run(null, null, null, "-g", "-d", classesDir.toString(), sourceFile.toString());
        assertTrue(exitCode == 0, () -> "javac failed for " + Objects.toString(sourceFile));
        return classesDir;
    }
}
