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
        /**
         * Resolved by letting javac compile the file's real sibling sources on demand from a
         * {@code -sourcepath}. Unlike {@link #STUBBED} this introduces no invented code: every
         * supporting class is the project's own source, compiled by the same Java 17 contract.
         * The siblings are published as support classpath (WALA Extension scope), so they provide
         * resolution and real semantics without ever becoming clone candidates.
         */
        SOURCE_PATH_CONTEXT,
        STUBBED
    }
}
