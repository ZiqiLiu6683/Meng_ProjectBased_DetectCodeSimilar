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
        List<Path> sourcePath,
        boolean allowStubs
) {
    public SourceAnalysisInput {
        source = source == null ? "" : source;
        fileName = fileName == null || fileName.isBlank() ? "Input.java" : fileName;
        projectRoot = projectRoot == null ? null : projectRoot.toAbsolutePath().normalize();
        classpath = normalize(classpath);
        sourcePath = normalize(sourcePath);
    }

    private static List<Path> normalize(List<Path> paths) {
        return paths == null ? List.of() : paths.stream()
                .filter(path -> path != null)
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .toList();
    }

    /** Pre-sourcePath call shape, retained so existing two-file callers keep compiling. */
    public SourceAnalysisInput(String source, String fileName, Path projectRoot,
                               List<Path> classpath, boolean allowStubs) {
        this(source, fileName, projectRoot, classpath, List.of(), allowStubs);
    }

    public static SourceAnalysisInput standalone(String source, String fileName) {
        return new SourceAnalysisInput(source, fileName, null, List.of(), List.of(), true);
    }

    public boolean hasRequestedContext() {
        return projectRoot != null || !classpath.isEmpty() || !sourcePath.isEmpty();
    }
}
