package com.ziqi.codesim.region.dynamic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dynamic layer: catch Type-4 clones by running both methods on the same random inputs. The
 * headline is {@code loopSum2} vs {@code formula} -- behaviourally identical but one uses a loop, so
 * SMT (Phase B) cannot prove it, yet I/O sampling agrees on every input.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class DynamicEquivalenceCheckerTest {

    @TempDir
    Path tempDir;

    @Test
    void samplesIoToDecideBehaviouralEquivalence() throws Exception {
        Path left = compile("left", "L",
                "public class L {"
                        + " public int twice(int x){ return x*2; }"
                        + " public int loopSum2(int n){ int s=0; for(int i=0;i<n;i++){ s+=2; } return s; } }");
        Path right = compile("right", "R",
                "public class R {"
                        + " public int doubled(int x){ return x+x; }"
                        + " public int plusOne(int x){ return x+1; }"
                        + " public int formula(int n){ return n>0 ? n*2 : 0; } }");

        DynamicEquivalenceChecker checker = new DynamicEquivalenceChecker();

        assertEquals(DynamicVerdict.LIKELY_EQUIVALENT,
                checker.check(left, "L", "twice", right, "R", "doubled"),
                "x*2 and x+x agree on all inputs");
        assertEquals(DynamicVerdict.DIFFERENT,
                checker.check(left, "L", "twice", right, "R", "plusOne"),
                "x*2 and x+1 differ");
        assertEquals(DynamicVerdict.LIKELY_EQUIVALENT,
                checker.check(left, "L", "loopSum2", right, "R", "formula"),
                "a loop-based method and its closed form: SMT cannot prove it, but I/O sampling agrees");
    }

    private Path compile(String pkg, String type, String source) throws Exception {
        Path sourceDir = tempDir.resolve(pkg + "/src");
        Path classesDir = tempDir.resolve(pkg + "/classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);
        Path sourceFile = sourceDir.resolve(type + ".java");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "dynamic checker test requires a JDK compiler");
        int exitCode = compiler.run(null, null, null, "-d", classesDir.toString(), sourceFile.toString());
        assertTrue(exitCode == 0, () -> "javac failed for " + Objects.toString(sourceFile));
        return classesDir;
    }
}
