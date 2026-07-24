package com.ziqi.codesim.next.semantic;

import java.nio.file.Path;
import java.util.List;

/**
 * One source-side input to the pairwise pipeline.
 *
 * <p>The source text and file name are always present, preserving the product's default
 * "compare two files" contract. Project root and classpath are optional compilation context: they
 * help javac and WALA resolve dependencies but are never themselves treated as clone candidates.
 */
public record SourceAnalysisInput(
        String source,
        String fileName,
        Path projectRoot,
        List<Path> classpath,
        boolean allowStubs
) {
    public SourceAnalysisInput {
        source = source == null ? "" : source;
        fileName = fileName == null || fileName.isBlank() ? "Input.java" : fileName;
        projectRoot = projectRoot == null ? null : projectRoot.toAbsolutePath().normalize();
        classpath = classpath == null ? List.of() : classpath.stream()
                .filter(path -> path != null)
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .toList();
    }

    public static SourceAnalysisInput standalone(String source, String fileName) {
        return new SourceAnalysisInput(source, fileName, null, List.of(), true);
    }

    public boolean hasRequestedContext() {
        return projectRoot != null || !classpath.isEmpty();
    }
}
