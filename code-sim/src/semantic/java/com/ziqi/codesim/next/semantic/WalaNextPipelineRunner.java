package com.ziqi.codesim.next.semantic;

import com.ziqi.codesim.next.CandidateSignalProvider;
import com.ziqi.codesim.next.NextPipelineResult;
import com.ziqi.codesim.next.NextPipelineRunner;
import com.ziqi.codesim.next.RawToolCfgCandidateProvider;
import com.ziqi.codesim.next.StructuralSimilarityOracle;
import com.ziqi.codesim.next.WalaStructuralSimilarityOracle;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ziqi.codesim.semantic.backend.AnalysisException;
import com.ziqi.codesim.semantic.backend.wala.WalaAnalysisBackend;
import com.ziqi.codesim.semantic.backend.wala.WalaClassHierarchies;
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

            // Build each side's WALA class hierarchy and IR cache once, then share them between the
            // raw-snapshot extractor (kNN candidates) and the structural backend (CFG/MCS), so the
            // same classes are not analyzed twice.
            ClassHierarchy leftHierarchy = WalaClassHierarchies.build(leftClasses);
            ClassHierarchy rightHierarchy = WalaClassHierarchies.build(rightClasses);
            AnalysisCacheImpl leftCache = new AnalysisCacheImpl();
            AnalysisCacheImpl rightCache = new AnalysisCacheImpl();
            String leftLabel = leftClasses.toAbsolutePath().toString();
            String rightLabel = rightClasses.toAbsolutePath().toString();

            RawToolProgram leftProgram = extractor.extract(leftHierarchy, leftCache, leftLabel);
            RawToolProgram rightProgram = extractor.extract(rightHierarchy, rightCache, rightLabel);
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
                    backend.analyze(leftHierarchy, leftCache, leftLabel).methods(),
                    backend.analyze(rightHierarchy, rightCache, rightLabel).methods()
            );
            return new NextPipelineRunner(List.of(provider), structuralOracle).run(leftSource, rightSource);
        } catch (IOException | AnalysisException | RuntimeException ex) {
            // CFG analysis unavailable (e.g. the input does not compile standalone, or WALA fails):
            // fall back to the source-only pipeline so the user still gets the syntactic result
            // (just without cfg-sim). This reports real data, never fabricated CFG output.
            System.err.println("[WalaNextPipelineRunner] CFG analysis unavailable, "
                    + "falling back to source-only result: " + ex.getMessage());
            return new NextPipelineRunner().run(leftSource, rightSource);
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
            // javac requires a public top-level type to live in a file of the same name, so name
            // the file after the public class/interface/enum/record when present (falling back to
            // the provided default). Without this, any input with a public class fails to compile.
            Path sourceFile = sourceDir.resolve(publicTypeFileName(source, fileName));
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

    private static String publicTypeFileName(String source, String fallback) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "public\\s+(?:final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+|strictfp\\s+)*"
                        + "(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)")
                .matcher(source);
        if (matcher.find()) {
            return matcher.group(1) + ".java";
        }
        return fallback;
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
