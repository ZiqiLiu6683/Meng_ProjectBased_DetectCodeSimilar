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
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class JavaCompilationCoordinatorTest {

    @TempDir
    Path temp;

    /**
     * A file that only fails standalone because its own project's classes are missing must be
     * resolved from real sibling sources, never from invented stubs -- and the siblings must land
     * in the support classpath (WALA Extension scope), never in the analysed classes directory,
     * or they would silently become clone candidates.
     */
    @Test
    void resolvesSiblingSourcesWithoutStubsAndKeepsThemOutOfTheAnalysedClasses() throws Exception {
        Path project = temp.resolve("project");
        Files.createDirectories(project);
        Files.writeString(project.resolve("Helper.java"),
                "public class Helper { static int weigh(int v) { return v * 3; } }",
                StandardCharsets.UTF_8);
        String target = "public class Target { int f(int v) { return Helper.weigh(v); } }";

        JavaCompilationCoordinator coordinator = new JavaCompilationCoordinator(temp.resolve("cache"));
        CompilationArtifact artifact = coordinator.compile(new SourceAnalysisInput(
                target, "Target.java", null, List.of(), List.of(project), true));

        assertEquals(CompilationArtifact.CompilationMode.SOURCE_PATH_CONTEXT, artifact.mode());
        assertEquals(0, artifact.generatedStubCount(), "real sources must not trigger stub generation");
        assertFalse(artifact.usesStubs(), "sibling-source context stays T4-eligible");

        assertTrue(Files.exists(artifact.classesDirectory().resolve("Target.class")));
        assertFalse(Files.exists(artifact.classesDirectory().resolve("Helper.class")),
                "the sibling must not be an Application-scope clone candidate");
        assertTrue(artifact.supportClasspath().stream()
                        .anyMatch(path -> Files.exists(path.resolve("Helper.class"))),
                "the sibling must be reachable as Extension-scope context");
    }

    /** Without a source path the same file has no way to resolve its sibling and must fail closed. */
    @Test
    void withoutSourcePathTheSameFileStillFailsWhenStubsAreDisabled() throws Exception {
        JavaCompilationCoordinator coordinator = new JavaCompilationCoordinator(temp.resolve("cache"));
        assertThrows(AnalysisException.class, () -> coordinator.compile(new SourceAnalysisInput(
                "public class Target { int f(int v) { return Helper.weigh(v); } }",
                "Target.java", null, List.of(), List.of(), false)));
    }

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
        Properties manifest = new Properties();
        try (var reader = Files.newBufferedReader(
                first.classesDirectory().getParent().resolve("compilation.properties"),
                StandardCharsets.UTF_8)) {
            manifest.load(reader);
        }
        assertEquals(JavaCompilationCoordinator.implementationFingerprint(),
                manifest.getProperty("compilerImplementationSha256"));
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

    @Test
    void createsPackageMarkersAndUnambiguousLocalTypesForMultipleWildcardImports()
            throws Exception {
        SourceAnalysisInput input = SourceAnalysisInput.standalone(
                "import missing.one.*;\nimport missing.two.*;\n"
                        + "class Input { External f(External value) { return value; } }",
                "Input.java");

        CompilationArtifact artifact = new JavaCompilationCoordinator(temp.resolve("cache-wildcard"))
                .compile(input);

        assertEquals(CompilationArtifact.CompilationMode.STUBBED, artifact.mode());
        assertTrue(containsClass(artifact.classesDirectory(), "Input.class"));
        assertTrue(artifact.generatedStubCount() >= 3);
    }

    @Test
    void rendersObservedDependencyFieldsAsStaticForTypeQualifiedAccess() throws Exception {
        SourceAnalysisInput input = SourceAnalysisInput.standalone(
                "import missing.Config; class Input { Object f() { return Config.VALUE; } }",
                "Input.java");

        CompilationArtifact artifact = new JavaCompilationCoordinator(temp.resolve("cache-field"))
                .compile(input);

        assertEquals(CompilationArtifact.CompilationMode.STUBBED, artifact.mode());
        assertTrue(containsClass(artifact.classesDirectory(), "Input.class"));
    }

    @Test
    void attachesUnresolvedInheritedMembersToMissingSuperclass() throws Exception {
        SourceAnalysisInput input = SourceAnalysisInput.standalone(
                "class Input extends MissingBase { "
                        + "Object f() { return inheritedMethod(); } "
                        + "Object g() { return inheritedField; } }",
                "Input.java");

        CompilationArtifact artifact = new JavaCompilationCoordinator(temp.resolve("cache-inherited"))
                .compile(input);

        assertEquals(CompilationArtifact.CompilationMode.STUBBED, artifact.mode());
        assertTrue(containsClass(artifact.classesDirectory(), "Input.class"));
    }

    private static boolean containsClass(Path root, String fileName) throws Exception {
        try (var stream = Files.walk(root)) {
            return stream.anyMatch(path -> path.getFileName().toString().equals(fileName));
        }
    }
}
