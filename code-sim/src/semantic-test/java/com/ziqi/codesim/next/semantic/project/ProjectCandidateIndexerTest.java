package com.ziqi.codesim.next.semantic.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class ProjectCandidateIndexerTest {

    @TempDir
    Path temp;

    @Test
    void retrievesStructurallySimilarMethodsWithoutTakingAQuadraticFileProduct() throws Exception {
        Path left = temp.resolve("left");
        Path right = temp.resolve("right");
        write(left.resolve("src/Alpha.java"), """
                class Alpha {
                  int total(int[] values) {
                    int sum = 0;
                    for (int value : values) sum += value;
                    return sum;
                  }
                }
                """);
        write(left.resolve("src/Noise.java"), "class Noise { String name() { return \"x\"; } }");
        write(right.resolve("src/Beta.java"), """
                class Beta {
                  int aggregate(int[] input) {
                    int result = 0;
                    for (int item : input) result += item;
                    return result;
                  }
                }
                """);
        write(right.resolve("target/Generated.java"),
                "class Generated { int total(int[] x) { return 0; } }");

        ProjectCandidateIndexer.ProjectScanPlan plan = new ProjectCandidateIndexer()
                .plan(left, right, 4, 10);

        assertEquals(2, plan.leftJavaFiles());
        assertEquals(1, plan.rightJavaFiles(), "build outputs must not become source candidates");
        assertTrue(plan.candidates().stream().anyMatch(candidate ->
                candidate.leftFile().endsWith("Alpha.java")
                        && candidate.rightFile().endsWith("Beta.java")));
        assertTrue(plan.candidates().size() < plan.leftJavaFiles() * plan.rightJavaFiles() + 1);
    }

    @Test
    void sameProjectScanCanonicalizesPairsAndNeverComparesAFileToItself() throws Exception {
        Path root = temp.resolve("same");
        write(root.resolve("A.java"), "class A { int f(int x) { return x + 1; } }");
        write(root.resolve("B.java"), "class B { int g(int y) { return y + 1; } }");
        write(root.resolve("C.java"), "class C { int h(int z) { return z + 2; } }");

        ProjectCandidateIndexer.ProjectScanPlan plan = new ProjectCandidateIndexer()
                .plan(root, root, 8, 20);

        Set<String> seen = new HashSet<>();
        plan.candidates().forEach(candidate -> {
            assertFalse(candidate.leftFile().equals(candidate.rightFile()));
            assertTrue(candidate.leftFile().toString().compareTo(candidate.rightFile().toString()) < 0);
            assertTrue(seen.add(candidate.leftFile() + "\n" + candidate.rightFile()));
        });
        assertFalse(plan.candidates().isEmpty());
    }

    @Test
    void projectScanManifestMakesSafetyControlsExplicit() throws Exception {
        Path left = temp.resolve("manifest-left");
        Path right = temp.resolve("manifest-right");
        Path output = temp.resolve("scan.jsonl");
        write(left.resolve("A.java"), "class A {}");
        write(right.resolve("B.java"), "class B {}");
        String previous = System.getProperty("codesim.skipDynamic");
        try {
            ProjectScanMain.main(new String[] {
                    left.toString(), right.toString(), output.toString(), "--no-stubs"
            });
        } finally {
            if (previous == null) System.clearProperty("codesim.skipDynamic");
            else System.setProperty("codesim.skipDynamic", previous);
        }

        List<String> rows = Files.readAllLines(output);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).contains("\"schemaVersion\":\"project-scan-1.0\""));
        assertTrue(rows.get(0).contains("\"allowStubs\":false"));
        assertTrue(rows.get(0).contains("\"dynamicEnabled\":false"));
        assertTrue(rows.get(0).contains("\"javaRelease\":17"));
    }

    private static void write(Path file, String source) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }
}
