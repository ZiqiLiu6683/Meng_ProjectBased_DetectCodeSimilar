package com.ziqi.codesim.region.grow;

import com.ziqi.codesim.region.descriptor.NodeDescriptorBuilder;
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
 * M4b: the boundary-free payoff. Left inlines {@code x*2+1}; right extracts the {@code *2} into a
 * helper {@code h} called by {@code g}. A correct boundary-free selector must grow ONE region whose
 * right side spans BOTH g and h.
 *
 * <p>This run also dumps the right graph and the top region so we can see exactly how growth behaves
 * across the interprocedural plumbing (the helper's return value travels mul(h) -> return -> caller
 * -> add(g), changing edge kinds at the boundary). If the assertion fails, the dump pinpoints where
 * growth stalls and drives the fix.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class CrossMethodRegionTest {

    @TempDir
    Path tempDir;

    @Test
    void growsRegionSpanningHelperExtraction() throws Exception {
        SemanticGraph leftGraph = build("left", "A", """
                package left;
                public class A {
                    public int f(int x) { return x * 2 + 1; }
                }
                """, "Left.java");
        SemanticGraph rightGraph = build("right", "B", """
                package right;
                public class B {
                    public int g(int y) { return h(y) + 1; }
                    public int h(int y) { return y * 2; }
                }
                """, "Right.java");

        Map<Integer, NodeDescriptor> leftDesc = new NodeDescriptorBuilder().build(leftGraph);
        Map<Integer, NodeDescriptor> rightDesc = new NodeDescriptorBuilder().build(rightGraph);
        List<SeedPair> seeds = new SeedMatcher().match(leftDesc, rightDesc);
        List<RegionGroup> regions = new RegionGrower().grow(leftGraph, leftDesc, rightGraph, rightDesc, seeds);

        // The boundary-free payoff AND the ranking fix: the substantive cross-method clone (right
        // side spans BOTH g, the +1 caller, and h, the *2 helper) must now rank FIRST, above the
        // trivial <init> constructor regions that previously outranked it.
        RegionGroup top = regions.get(0);
        assertTrue(top.rightMethods().size() >= 2,
                "the substantive cross-method clone must outrank trivial constructor regions");
        assertTrue(top.rightMethods().stream().anyMatch(s -> s.contains("B.g")), "spans B.g");
        assertTrue(top.rightMethods().stream().anyMatch(s -> s.contains("B.h")), "spans B.h");
        assertTrue(top.leftMethods().stream().anyMatch(s -> s.contains("A.f")), "from inlined A.f");
    }

    private SemanticGraph build(String pkg, String type, String source, String label) throws Exception {
        Path sourceDir = tempDir.resolve(pkg + "/src/" + pkg);
        Path classesDir = tempDir.resolve(pkg + "/classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);
        Path sourceFile = sourceDir.resolve(type + ".java");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "cross-method test requires a JDK compiler");
        int exitCode = compiler.run(null, null, null, "-g", "-d", classesDir.toString(), sourceFile.toString());
        assertTrue(exitCode == 0, () -> "javac failed for " + Objects.toString(sourceFile));
        return new SdgBuilder().build(classesDir, label);
    }
}
