package com.ziqi.codesim.semantic.backend.wala.raw;

import com.ziqi.codesim.semantic.raw.RawToolInstruction;
import com.ziqi.codesim.semantic.raw.RawToolMethod;
import com.ziqi.codesim.semantic.raw.RawToolProgram;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class WalaRawSnapshotExtractorTest {
    @TempDir
    Path tempDir;

    @Test
    void preservesRawWalaMethodCfgBlockAndInstructionOutputs() throws Exception {
        Path sourceDir = tempDir.resolve("src/probe");
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);
        Path sourceFile = sourceDir.resolve("RawProbe.java");
        Files.writeString(sourceFile, """
                package probe;

                public class RawProbe {
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
        compile(sourceFile, classesDir, "-g");

        RawToolProgram snapshot = new WalaRawSnapshotExtractor().extract(classesDir);
        RawToolMethod run = snapshot.classes().stream()
                .flatMap(clazz -> clazz.methods().stream())
                .filter(method -> method.rawMethodSignature().contains("RawProbe.run"))
                .findFirst()
                .orElseThrow();
        List<RawToolInstruction> instructions = run.blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .toList();

        assertEquals("WALA", snapshot.toolName());
        assertFalse(run.rawIRText().isBlank());
        assertFalse(run.rawCFGText().isBlank());
        assertTrue(run.blocks().size() >= 3);
        assertTrue(instructions.stream().anyMatch(i -> i.rawInstructionText().contains("helper")));
        assertTrue(instructions.stream().anyMatch(i -> !i.rawInstructionClassName().isBlank()));
        assertTrue(instructions.stream().anyMatch(i -> i.rawDeclaredTargetText().contains("helper")));
        assertTrue(instructions.stream().anyMatch(i -> !i.rawDefValues().isEmpty()));
        assertTrue(instructions.stream().anyMatch(i -> !i.rawUseValues().isEmpty()));
    }

    private static void compile(Path sourceFile, Path outputDir, String debugFlag) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "WALA raw snapshot test requires a JDK compiler");
        int exitCode = compiler.run(
                null,
                null,
                null,
                debugFlag,
                "-d",
                outputDir.toString(),
                sourceFile.toString()
        );
        assertEquals(0, exitCode);
    }
}
