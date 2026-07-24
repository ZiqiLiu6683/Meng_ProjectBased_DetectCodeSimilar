package com.ziqi.codesim.next.semantic.compilation;

import java.nio.file.Path;
import java.util.List;

/** Immutable, cacheable output of compiling one user-provided source file. */
public record CompilationArtifact(
        Path classesDirectory,
        List<Path> supportClasspath,
        CompilationMode mode,
        String cacheKey,
        boolean cacheHit,
        int generatedStubCount,
        String diagnosticSummary
) {
    public CompilationArtifact {
        classesDirectory = classesDirectory.toAbsolutePath().normalize();
        supportClasspath = List.copyOf(supportClasspath);
        diagnosticSummary = diagnosticSummary == null ? "" : diagnosticSummary;
    }

    public boolean usesStubs() {
        return mode == CompilationMode.STUBBED;
    }

    public enum CompilationMode {
        STANDALONE,
        PROJECT_CONTEXT,
        STUBBED
    }
}
