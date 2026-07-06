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

    @Test
    void samplesNonIntSignatures() throws Exception {
        Path left = compile("left2", "L2",
                "public class L2 {"
                        + " public int sumLoop(int[] a){ int s=0; for(int i=0;i<a.length;i++){ s+=a[i]; } return s; }"
                        + " public String rev(String s){ return new StringBuilder(s).reverse().toString(); }"
                        + " public int maxLoop(int[] a){ if(a.length==0){ return 0; } int m=a[0]; for(int i=1;i<a.length;i++){ if(a[i]>m){ m=a[i]; } } return m; } }");
        Path right = compile("right2", "R2",
                "public class R2 {"
                        + " public int sumRec(int[] a){ return go(a,0); }"
                        + " private int go(int[] a,int i){ if(i>=a.length){ return 0; } return a[i]+go(a,i+1); }"
                        + " public String revManual(String s){ char[] c=s.toCharArray(); int i=0,j=c.length-1; while(i<j){ char t=c[i]; c[i]=c[j]; c[j]=t; i++; j--; } return new String(c); } }");

        DynamicEquivalenceChecker checker = new DynamicEquivalenceChecker();

        assertEquals(DynamicVerdict.LIKELY_EQUIVALENT,
                checker.check(left, "L2", "sumLoop", right, "R2", "sumRec"),
                "int[] sum via loop vs recursion agree on all sampled arrays");
        assertEquals(DynamicVerdict.LIKELY_EQUIVALENT,
                checker.check(left, "L2", "rev", right, "R2", "revManual"),
                "String reverse two ways agree on all sampled strings");
        assertEquals(DynamicVerdict.DIFFERENT,
                checker.check(left, "L2", "maxLoop", right, "R2", "sumRec"),
                "array max vs array sum differ on some input");
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
