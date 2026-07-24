package com.ziqi.codesim.next.semantic.compilation;

import com.ziqi.codesim.next.semantic.SourceAnalysisInput;
import com.ziqi.codesim.semantic.backend.AnalysisException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class JavaCompilationCoordinatorTest {

    @TempDir
    Path temp;

    @Test
    void generatesDependencyStubOnceAndThenReusesImmutableCache() throws Exception {
        JavaCompilationCoordinator coordinator = new JavaCompilationCoordinator(temp.resolve("cache"));
        SourceAnalysisInput input = SourceAnalysisInput.standalone(
                "class Input { MissingType value; int f() { return 1; } }", "Input.java");

        CompilationArtifact first = coordinator.compile(input);
        CompilationArtifact second = coordinator.compile(input);

        assertEquals(CompilationArtifact.CompilationMode.STUBBED, first.mode());
        assertEquals(1, first.generatedStubCount());
        assertFalse(first.cacheHit());
        assertTrue(second.cacheHit());
        assertEquals(first.cacheKey(), second.cacheKey());
        assertTrue(first.diagnosticSummary().contains("MissingType"));
        assertEquals(first.diagnosticSummary(), second.diagnosticSummary());
        assertTrue(containsClass(first.classesDirectory(), "Input.class"));
        assertTrue(second.supportClasspath().stream()
                .anyMatch(path -> path.endsWith("stub-classes")));
    }

    @Test
    void usesRealProjectClassesBeforeConsideringStubs() throws Exception {
        Path project = temp.resolve("project");
        Path dependencySource = project.resolve("dep-src/dep/Helper.java");
        Path projectClasses = project.resolve("target/classes");
        Files.createDirectories(dependencySource.getParent());
        Files.createDirectories(projectClasses);
        Files.writeString(dependencySource,
                "package dep; public class Helper { public static int value() { return 7; } }",
                StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int rc = compiler.run(null, null, null, "--release", "17", "-d",
                projectClasses.toString(), dependencySource.toString());
        assertEquals(0, rc);

        SourceAnalysisInput input = new SourceAnalysisInput(
                "import dep.Helper; class Input { int f() { return Helper.value(); } }",
                "Input.java", project, List.of(), true);
        CompilationArtifact artifact = new JavaCompilationCoordinator(temp.resolve("cache-project"))
                .compile(input);

        assertEquals(CompilationArtifact.CompilationMode.PROJECT_CONTEXT, artifact.mode());
        assertEquals(0, artifact.generatedStubCount());
        assertTrue(artifact.supportClasspath().contains(projectClasses.toAbsolutePath().normalize()));
    }

    @Test
    void failsClosedAndCachesDiagnosticsThatCannotBeSafelyStubbed() {
        JavaCompilationCoordinator coordinator = new JavaCompilationCoordinator(temp.resolve("cache-fail"));
        SourceAnalysisInput input = SourceAnalysisInput.standalone(
                "class Input { int f() { return unknownValue; } }", "Input.java");

        assertThrows(AnalysisException.class, () -> coordinator.compile(input));
        AnalysisException cached = assertThrows(
                AnalysisException.class, () -> coordinator.compile(input));
        assertTrue(cached.getMessage().startsWith("Cached Java 17 compilation failure"));
    }

    @Test
    void emptyProjectRootIsNotMisreportedAsUsedContext() throws Exception {
        Path emptyProject = temp.resolve("empty-project");
        Files.createDirectories(emptyProject);
        SourceAnalysisInput input = new SourceAnalysisInput(
                "class Input { int f() { return 1; } }", "Input.java",
                emptyProject, List.of(), true);

        CompilationArtifact artifact = new JavaCompilationCoordinator(temp.resolve("cache-empty"))
                .compile(input);

        assertEquals(CompilationArtifact.CompilationMode.STANDALONE, artifact.mode());
        assertTrue(artifact.supportClasspath().isEmpty());
    }

    @Test
    void understandsSingleMemberStaticImportsWhenStubbing() throws Exception {
        SourceAnalysisInput input = SourceAnalysisInput.standalone(
                "import static missing.Tools.answer; "
                        + "class Input { Object f() { return answer(); } }", "Input.java");

        CompilationArtifact artifact = new JavaCompilationCoordinator(temp.resolve("cache-static"))
                .compile(input);

        assertEquals(CompilationArtifact.CompilationMode.STUBBED, artifact.mode());
        assertEquals(1, artifact.generatedStubCount());
        assertFalse(artifact.diagnosticSummary().isBlank());
    }

    private static boolean containsClass(Path root, String fileName) throws Exception {
        try (var stream = Files.walk(root)) {
            return stream.anyMatch(path -> path.getFileName().toString().equals(fileName));
        }
    }
}
