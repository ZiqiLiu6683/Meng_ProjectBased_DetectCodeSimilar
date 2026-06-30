package com.ziqi.codesim.region.descriptor;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2 check: node descriptors must be name-invariant but operation-sensitive. Two methods with
 * identical structure and different variable names must produce equal WL hashes for corresponding
 * nodes; a method that differs only by operator (mul vs add) must produce a different hash. This is
 * the property the cross-file seed matcher (M3) will rely on.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class NodeDescriptorBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void wlHashIsNameInvariantButOperationSensitive() throws Exception {
        Path sourceDir = tempDir.resolve("src/probe");
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);

        // a and b: structurally identical, only the variable name differs.
        // c: differs by one operator (add instead of mul).
        Path sourceFile = sourceDir.resolve("Probe.java");
        Files.writeString(sourceFile, """
                package probe;

                public class Probe {
                    public int a(int x) { return x * 2; }
                    public int b(int y) { return y * 2; }
                    public int c(int z) { return z + 2; }
                }
                """, StandardCharsets.UTF_8);
        compile(sourceFile, classesDir);

        SemanticGraph graph = new SdgBuilder().build(classesDir, "Probe.java");
        Map<Integer, NodeDescriptor> descriptors = new NodeDescriptorBuilder().build(graph);

        List<SemanticNode> mulNodes = graph.nodes().stream()
                .filter(n -> n.operationToken().equals("binaryop:mul")).toList();
        List<SemanticNode> addNodes = graph.nodes().stream()
                .filter(n -> n.operationToken().equals("binaryop:add")).toList();
        assertEquals(2, mulNodes.size(), "a() and b() each contribute one mul node");
        assertEquals(1, addNodes.size(), "c() contributes one add node");

        long mulA = descriptors.get(mulNodes.get(0).id()).finalWlHash();
        long mulB = descriptors.get(mulNodes.get(1).id()).finalWlHash();
        long add = descriptors.get(addNodes.get(0).id()).finalWlHash();

        assertEquals(mulA, mulB,
                "structurally identical methods (only variable names differ) must share a WL hash");
        assertNotEquals(mulA, add,
                "a different operator (mul vs add) must change the WL hash");

        // Determinism: a second build yields byte-identical hash sequences.
        Map<Integer, NodeDescriptor> rebuilt = new NodeDescriptorBuilder().build(graph);
        assertEquals(descriptors.get(mulNodes.get(0).id()).wlHashes(),
                rebuilt.get(mulNodes.get(0).id()).wlHashes(),
                "WL labeling must be deterministic across runs");
    }

    private static void compile(Path sourceFile, Path outputDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "descriptor test requires a JDK compiler");
        int exitCode = compiler.run(null, null, null, "-g", "-d", outputDir.toString(), sourceFile.toString());
        assertTrue(exitCode == 0, () -> "javac failed for " + Objects.toString(sourceFile));
    }
}
