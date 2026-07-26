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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M4 check: from the seed anchors, the grower must expand into a region group that covers most of a
 * cloned method across two files (not just the single seed pair), and project that region back to
 * source lines. This is the end-to-end proof of Phase A on the simplest (single-method) clone.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class RegionGrowerTest {

    @TempDir
    Path tempDir;

    @Test
    void growsRegionCoveringClonedMethodAcrossFiles() throws Exception {
        SemanticGraph leftGraph = buildGraph("left", "A", "f", "x", "Left.java");
        SemanticGraph rightGraph = buildGraph("right", "B", "g", "y", "Right.java");
        Map<Integer, NodeDescriptor> leftDesc = new NodeDescriptorBuilder().build(leftGraph);
        Map<Integer, NodeDescriptor> rightDesc = new NodeDescriptorBuilder().build(rightGraph);
        List<SeedPair> seeds = new SeedMatcher().match(leftDesc, rightDesc);

        List<RegionGroup> regions = new RegionGrower().grow(leftGraph, leftDesc, rightGraph, rightDesc, seeds);
        assertFalse(regions.isEmpty(), "a cloned method must yield at least one region group");

        RegionGroup top = regions.get(0);
        assertTrue(top.size() >= 3,
                "region should grow beyond the seed to cover the cloned computation, got " + top.size());
        assertTrue(top.leftMethods().stream().anyMatch(s -> s.contains("A.f")), "left side spans A.f");
        assertTrue(top.rightMethods().stream().anyMatch(s -> s.contains("B.g")), "right side spans B.g");
        assertTrue(top.leftSpan().isKnown() && top.rightSpan().isKnown(),
                "region must project back to real source line ranges on both sides");
        assertTrue(top.strength() >= 0.8,
                "a renamed (Type-2) clone should align with high agreement, got " + top.strength());

        // Determinism: a second run produces the same top-region size and strength.
        List<RegionGroup> rerun = new RegionGrower().grow(leftGraph, leftDesc, rightGraph, rightDesc, seeds);
        assertEquals(top.size(), rerun.get(0).size());
        assertEquals(top.strength(), rerun.get(0).strength(), 1e-9);
    }

    /**
     * The substantive floor must not weaken a real clone, and must reject a region whose only
     * real work is a single node. Such a region scores a perfect aligned fraction (1 substantive
     * node out of 1) while representing one statement, which is a coincidence, not a clone.
     */
    @Test
    void substantiveFloorKeepsRealClonesAndRejectsSingleNodeCoincidences() throws Exception {
        SemanticGraph leftGraph = buildGraph("left", "A", "f", "x", "Left.java");
        SemanticGraph rightGraph = buildGraph("right", "B", "g", "y", "Right.java");
        Map<Integer, NodeDescriptor> leftDesc = new NodeDescriptorBuilder().build(leftGraph);
        Map<Integer, NodeDescriptor> rightDesc = new NodeDescriptorBuilder().build(rightGraph);
        List<SeedPair> seeds = new SeedMatcher().match(leftDesc, rightDesc);

        List<RegionGroup> withFloor =
                new RegionGrower(2, 2).grow(leftGraph, leftDesc, rightGraph, rightDesc, seeds);
        List<RegionGroup> withoutFloor =
                new RegionGrower(2, 0).grow(leftGraph, leftDesc, rightGraph, rightDesc, seeds);

        assertFalse(withFloor.isEmpty(), "the real clone must survive the substantive floor");
        assertEquals(withoutFloor.get(0).size(), withFloor.get(0).size(),
                "the floor must not shrink the top region of a real clone");
        assertTrue(withFloor.size() <= withoutFloor.size(),
                "the floor may only remove regions, never add them");
        for (RegionGroup region : withFloor) {
            long substantive = region.alignment().stream()
                    .map(pair -> leftGraph.node(pair.leftNodeId()).orElse(null))
                    .filter(node -> node != null && SUBSTANTIVE.contains(node.operation()))
                    .count();
            assertTrue(substantive >= 2,
                    "every emitted region must do at least two units of real work, got " + substantive);
        }
    }

    private static final java.util.Set<com.ziqi.codesim.semantic.model.InstructionCategory> SUBSTANTIVE =
            java.util.EnumSet.of(
                    com.ziqi.codesim.semantic.model.InstructionCategory.ARITHMETIC,
                    com.ziqi.codesim.semantic.model.InstructionCategory.LOGIC,
                    com.ziqi.codesim.semantic.model.InstructionCategory.COMPARISON,
                    com.ziqi.codesim.semantic.model.InstructionCategory.BRANCH,
                    com.ziqi.codesim.semantic.model.InstructionCategory.FIELD_ACCESS,
                    com.ziqi.codesim.semantic.model.InstructionCategory.ARRAY_ACCESS,
                    com.ziqi.codesim.semantic.model.InstructionCategory.ALLOCATION,
                    com.ziqi.codesim.semantic.model.InstructionCategory.CALL,
                    com.ziqi.codesim.semantic.model.InstructionCategory.ASSIGNMENT);

    private SemanticGraph buildGraph(String pkg, String type, String method, String param,
                                     String label) throws Exception {
        Path sourceDir = tempDir.resolve(pkg + "/src/" + pkg);
        Path classesDir = tempDir.resolve(pkg + "/classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);
        Path sourceFile = sourceDir.resolve(type + ".java");
        Files.writeString(sourceFile, String.format("""
                package %s;

                public class %s {
                    public int %s(int %s) { return %s * 2 + 1; }
                }
                """, pkg, type, method, param, param), StandardCharsets.UTF_8);
        compile(sourceFile, classesDir);
        return new SdgBuilder().build(classesDir, label);
    }

    private static void compile(Path sourceFile, Path outputDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "region grower test requires a JDK compiler");
        int exitCode = compiler.run(null, null, null, "-g", "-d", outputDir.toString(), sourceFile.toString());
        assertTrue(exitCode == 0, () -> "javac failed for " + Objects.toString(sourceFile));
    }
}
