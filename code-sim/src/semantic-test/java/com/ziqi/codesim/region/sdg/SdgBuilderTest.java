package com.ziqi.codesim.region.sdg;

import com.ziqi.codesim.region.model.NodeKind;
import com.ziqi.codesim.region.model.SemanticGraph;
import com.ziqi.codesim.region.model.SemanticNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 foundation check: the SDG builder must produce one cross-method graph (not per-method islands)
 * whose statement nodes map back to real source lines. Everything in Phase A depends on these two
 * properties, so this test is the geological survey before we build on top.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class SdgBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsCrossMethodGraphWithSourceLineMapping() throws Exception {
        Path sourceDir = tempDir.resolve("src/probe");
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);

        // A caller that delegates to a helper: the simplest case that must yield a cross-method
        // (boundary-free) graph. helper() is the kind of extracted method our region groups target.
        Path sourceFile = sourceDir.resolve("Probe.java");
        Files.writeString(sourceFile, """
                package probe;

                public class Probe {
                    public int run(int value) {
                        int adjusted = helper(value);
                        if (adjusted > 10) {
                            return adjusted - 3;
                        }
                        return adjusted + 7;
                    }

                    private int helper(int input) {
                        return input * 2;
                    }
                }
                """, StandardCharsets.UTF_8);
        compileWithDebugInfo(sourceFile, classesDir);

        SemanticGraph graph = new SdgBuilder().build(classesDir, "Probe.java");

        // 1) Both methods present in one graph.
        boolean hasRun = graph.methodSignatures().stream().anyMatch(s -> s.contains("Probe.run"));
        boolean hasHelper = graph.methodSignatures().stream().anyMatch(s -> s.contains("Probe.helper"));
        assertTrue(hasRun, "run() should contribute nodes");
        assertTrue(hasHelper, "helper() should contribute nodes to the SAME graph");

        // 2) The two methods are actually connected (call / parameter / return edges cross methods).
        assertTrue(graph.interproceduralEdgeCount() > 0,
                "graph must contain at least one cross-method dependence edge");

        // 3) Statement nodes carry real source line numbers (requires -g; this is the load-bearing
        //    CFG-node -> source-line seam the whole selector relies on).
        long mappedStatements = graph.nodes().stream()
                .filter(n -> n.kind() == NodeKind.STATEMENT)
                .filter(SemanticNode::hasSource)
                .count();
        assertTrue(mappedStatements > 0, "at least some statement nodes must map back to source lines");

        // Lines must fall inside the compiled source range (defensive sanity on the mapping).
        int sourceLineCount = (int) Files.lines(sourceFile, StandardCharsets.UTF_8).count();
        boolean linesInRange = graph.nodes().stream()
                .filter(SemanticNode::hasSource)
                .allMatch(n -> n.source().beginLine() >= 1 && n.source().endLine() <= sourceLineCount);
        assertTrue(linesInRange, "mapped source lines must lie within the source file");
    }

    private static void compileWithDebugInfo(Path sourceFile, Path outputDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "SDG builder test requires a JDK compiler");
        // -g is required: without debug line tables, IBytecodeMethod.getLineNumber returns -1 and
        // the source mapping assertions would (correctly) fail.
        int exitCode = compiler.run(null, null, null, "-g", "-d", outputDir.toString(), sourceFile.toString());
        assertTrue(exitCode == 0, () -> "javac failed for " + Objects.toString(sourceFile));
    }
}
