package com.ziqi.codesim.region.seed;

import com.ziqi.codesim.region.descriptor.NodeDescriptorBuilder;
import com.ziqi.codesim.region.model.NodeDescriptor;
import com.ziqi.codesim.region.model.SemanticGraph;
import com.ziqi.codesim.region.model.SemanticNode;
import com.ziqi.codesim.region.sdg.SdgBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M3 check: the seed matcher must connect a renamed (Type-2) clone across two separate files, with
 * the structurally/semantically identical nodes surfacing as the strongest anchors, and never pair
 * a node with one from its own file.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class SeedMatcherTest {

    @TempDir
    Path tempDir;

    @Test
    void seedsConnectRenamedCloneAcrossFiles() throws Exception {
        SemanticGraph leftGraph = buildGraph("left", "A", "f", "x", "Left.java");
        SemanticGraph rightGraph = buildGraph("right", "B", "g", "y", "Right.java");

        Map<Integer, NodeDescriptor> leftDescriptors = new NodeDescriptorBuilder().build(leftGraph);
        Map<Integer, NodeDescriptor> rightDescriptors = new NodeDescriptorBuilder().build(rightGraph);

        var seeds = new SeedMatcher().match(leftDescriptors, rightDescriptors);
        assertFalse(seeds.isEmpty(), "renamed clone must produce cross-file seeds");

        // Every seed is cross-file by construction.
        for (SeedPair seed : seeds) {
            assertTrue(leftGraph.node(seed.leftNodeId()).isPresent(), "left id must belong to left graph");
            assertTrue(rightGraph.node(seed.rightNodeId()).isPresent(), "right id must belong to right graph");
        }

        // The two mul nodes (x*2 vs y*2) are an identical computation, so they should anchor with a
        // top-strength semantic seed despite the rename.
        SemanticNode leftMul = mulNode(leftGraph);
        SemanticNode rightMul = mulNode(rightGraph);
        Optional<SeedPair> mulSeed = seeds.stream()
                .filter(s -> s.leftNodeId() == leftMul.id() && s.rightNodeId() == rightMul.id())
                .findFirst();
        assertTrue(mulSeed.isPresent(), "the two mul nodes should be seeded together");
        assertEquals(1.0, mulSeed.get().strength(), 1e-9, "identical computation = top-strength anchor");
        assertTrue(mulSeed.get().evidence().contains("SEM"),
                "semantic value hash should fire for the identical computation");
    }

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

    private static SemanticNode mulNode(SemanticGraph graph) {
        return graph.nodes().stream()
                .filter(n -> n.operationToken().equals("binaryop:mul"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no mul node in " + graph.fileLabel()));
    }

    private static void compile(Path sourceFile, Path outputDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "seed matcher test requires a JDK compiler");
        int exitCode = compiler.run(null, null, null, "-g", "-d", outputDir.toString(), sourceFile.toString());
        assertTrue(exitCode == 0, () -> "javac failed for " + Objects.toString(sourceFile));
    }
}
