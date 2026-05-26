package com.ziqi.codesim.semantic.backend.wala;

import com.ziqi.codesim.semantic.model.AnalyzedMethod;
import com.ziqi.codesim.semantic.model.AnalyzedProgram;
import com.ziqi.codesim.semantic.model.CallKind;
import com.ziqi.codesim.semantic.model.InstructionCategory;
import com.ziqi.codesim.semantic.model.InstructionUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class WalaAnalysisBackendTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsCfgCallsAndSemanticInstructionCategoriesFromBytecode() throws Exception {
        Path sourceDir = tempDir.resolve("src/probe");
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);

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
        compile(sourceFile, classesDir);

        AnalyzedProgram program = new WalaAnalysisBackend().analyze(classesDir);
        AnalyzedMethod run = program.methods().stream()
                .filter(method -> method.signature().contains("Probe.run"))
                .findFirst()
                .orElseThrow();
        List<InstructionUnit> instructions = run.cfg().blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .toList();

        assertFalse(program.methods().isEmpty());
        assertTrue(run.cfg().blocks().size() >= 3, "branching method should expose multiple CFG blocks");
        assertFalse(run.cfg().edges().isEmpty(), "CFG should contain normal control-flow edges");
        assertTrue(instructions.stream().anyMatch(i -> i.category() == InstructionCategory.CALL));
        assertTrue(instructions.stream().anyMatch(i -> i.category() == InstructionCategory.BRANCH));
        assertTrue(instructions.stream().anyMatch(i -> i.category() == InstructionCategory.RETURN));
        assertTrue(instructions.stream().anyMatch(i -> i.callKind() == CallKind.INTERNAL));
    }

    private static void compile(Path sourceFile, Path outputDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "WALA backend integration test requires a JDK compiler");
        int exitCode = compiler.run(
                null,
                null,
                null,
                "-d",
                outputDir.toString(),
                sourceFile.toString()
        );
        assertTrue(exitCode == 0, () -> "javac failed for " + Objects.toString(sourceFile));
    }
}
