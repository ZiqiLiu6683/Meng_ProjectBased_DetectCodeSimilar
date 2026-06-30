package com.ziqi.codesim.region.semantic;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase B end-to-end: symbolic summary + SMT must decide semantic equivalence, reaching the Type-4
 * clone that Phase A's structural matching missed ({@code x*2} vs {@code y+y}), while staying sound
 * (different formulas DIFFERENT) and degrading gracefully on loops (UNKNOWN).
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class MethodEquivalenceTest {

    @TempDir
    Path tempDir;

    @Test
    void provesType4EquivalenceAndDistinguishesDifferences() throws Exception {
        Path sourceDir = tempDir.resolve("src");
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);
        Path sourceFile = sourceDir.resolve("P.java");
        Files.writeString(sourceFile, """
                public class P {
                    public int twice(int x)    { return x * 2; }
                    public int doubled(int y)  { return y + y; }
                    public int mulConst(int x) { return x * 2 + 1; }
                    public int other(int y)    { return y * 3 + 2; }
                    public int withLoop(int n) { int s = 0; for (int i = 0; i < n; i++) { s += i; } return s; }
                }
                """, StandardCharsets.UTF_8);
        compile(sourceFile, classesDir);

        Map<String, SymbolicExpression> summaries = new MethodSummaryExtractor().extractAll(classesDir);
        SmtEquivalenceChecker checker = new SmtEquivalenceChecker();

        // The Type-4 win: structurally different (mul vs add), behaviourally identical.
        assertEquals(EquivalenceVerdict.EQUIVALENT,
                checker.check(summary(summaries, "twice"), summary(summaries, "doubled")),
                "x*2 and y+y must be proven equivalent");

        // Soundness: genuinely different functions must be DIFFERENT.
        assertEquals(EquivalenceVerdict.DIFFERENT,
                checker.check(summary(summaries, "mulConst"), summary(summaries, "other")),
                "x*2+1 and y*3+2 differ");
        assertEquals(EquivalenceVerdict.DIFFERENT,
                checker.check(summary(summaries, "twice"), summary(summaries, "mulConst")),
                "x*2 and x*2+1 differ");

        // Graceful degradation: a loop summary is Unknown, so equivalence is UNKNOWN, not a guess.
        assertEquals(EquivalenceVerdict.UNKNOWN,
                checker.check(summary(summaries, "withLoop"), summary(summaries, "twice")),
                "a loop method must degrade to UNKNOWN");
    }

    private static SymbolicExpression summary(Map<String, SymbolicExpression> summaries, String method) {
        return summaries.entrySet().stream()
                .filter(e -> e.getKey().contains(method + "("))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no summary for " + method));
    }

    private static void compile(Path sourceFile, Path outputDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "phase B test requires a JDK compiler");
        int exitCode = compiler.run(null, null, null, "-g", "-d", outputDir.toString(), sourceFile.toString());
        assertTrue(exitCode == 0, () -> "javac failed for " + Objects.toString(sourceFile));
    }
}
