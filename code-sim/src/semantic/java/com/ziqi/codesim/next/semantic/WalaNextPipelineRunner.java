package com.ziqi.codesim.next.semantic;

import com.ziqi.codesim.next.CandidateSignalProvider;
import com.ziqi.codesim.next.NextPipelineResult;
import com.ziqi.codesim.next.NextPipelineRunner;
import com.ziqi.codesim.next.RawToolCfgCandidateProvider;
import com.ziqi.codesim.next.StructuralSimilarityOracle;
import com.ziqi.codesim.next.WalaStructuralSimilarityOracle;
import com.ziqi.codesim.semantic.backend.AnalysisException;
import com.ziqi.codesim.semantic.backend.wala.WalaAnalysisBackend;
import com.ziqi.codesim.semantic.backend.wala.raw.WalaRawSnapshotExtractor;
import com.ziqi.codesim.semantic.knn.KnnFeatureView;
import com.ziqi.codesim.semantic.raw.RawToolClass;
import com.ziqi.codesim.semantic.raw.RawToolMethod;
import com.ziqi.codesim.semantic.raw.RawToolProgram;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WalaNextPipelineRunner {
    private static final Set<String> RAW_FILTERED_KEEP = Set.of(
            "block.rawExceptionalSuccessor",
            "block.rawNormalSuccessor",
            "block.rawPredecessor",
            "instruction.rawDeclaredTargetText",
            "instruction.rawDefValue",
            "instruction.rawInstructionClassName",
            "instruction.rawInstructionIndex",
            "instruction.rawInstructionText",
            "instruction.rawUseValue",
            "method.rawCFGText"
    );

    private final WalaRawSnapshotExtractor extractor;
    private final KnnFeatureView view;
    private final int topK;

    public WalaNextPipelineRunner() {
        this(new WalaRawSnapshotExtractor(), KnnFeatureView.HYBRID_NUMERIC_HASH, 8);
    }

    public WalaNextPipelineRunner(WalaRawSnapshotExtractor extractor,
                                  KnnFeatureView view,
                                  int topK) {
        this.extractor = extractor;
        this.view = view;
        this.topK = topK;
    }

    public NextPipelineResult run(String leftSource, String rightSource) throws AnalysisException {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("code-sim-next-wala-");
            Path leftClasses = compileSource(workDir.resolve("left"), "LeftInput.java", leftSource);
            Path rightClasses = compileSource(workDir.resolve("right"), "RightInput.java", rightSource);
            RawToolProgram leftProgram = extractor.extract(leftClasses);
            RawToolProgram rightProgram = extractor.extract(rightClasses);
            CandidateSignalProvider provider = new RawToolCfgCandidateProvider(
                    methods(leftProgram),
                    methods(rightProgram),
                    RAW_FILTERED_KEEP,
                    view,
                    topK
            );
            // Second discovRE stage: analyze the same classes into method CFGs so the recognizer
            // can confirm/veto whole-method clones with the approximate-MCS structural matcher.
            WalaAnalysisBackend backend = new WalaAnalysisBackend();
            StructuralSimilarityOracle structuralOracle = new WalaStructuralSimilarityOracle(
                    backend.analyze(leftClasses).methods(),
                    backend.analyze(rightClasses).methods()
            );
            return new NextPipelineRunner(List.of(provider), structuralOracle).run(leftSource, rightSource);
        } catch (IOException ex) {
            throw new AnalysisException("Failed to prepare temporary WALA next-pipeline workspace", ex);
        } finally {
            if (workDir != null) {
                deleteQuietly(workDir);
            }
        }
    }

    private static Path compileSource(Path sourceRoot, String fileName, String source) throws AnalysisException {
        try {
            Path sourceDir = sourceRoot.resolve("src");
            Path classesDir = sourceRoot.resolve("classes");
            Files.createDirectories(sourceDir);
            Files.createDirectories(classesDir);
            Path sourceFile = sourceDir.resolve(fileName);
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new AnalysisException("WALA next pipeline requires a JDK compiler");
            }
            int exitCode = compiler.run(
                    null,
                    null,
                    null,
                    "-g",
                    "-d",
                    classesDir.toString(),
                    sourceFile.toString()
            );
            if (exitCode != 0) {
                throw new AnalysisException("Failed to compile source for WALA next pipeline: " + sourceFile);
            }
            return classesDir;
        } catch (IOException ex) {
            throw new AnalysisException("Failed to compile source for WALA next pipeline", ex);
        }
    }

    private static List<RawToolMethod> methods(RawToolProgram program) {
        List<RawToolMethod> methods = new ArrayList<>();
        for (RawToolClass rawClass : program.classes()) {
            methods.addAll(rawClass.methods());
        }
        return methods;
    }

    private static void deleteQuietly(Path path) {
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // Temporary cleanup failure should not hide analysis results.
                        }
                    });
        } catch (IOException ignored) {
            // Temporary cleanup failure should not hide analysis results.
        }
    }
}
