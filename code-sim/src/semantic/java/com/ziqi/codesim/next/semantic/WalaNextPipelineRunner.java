package com.ziqi.codesim.next.semantic;

import com.ziqi.codesim.next.CandidateSignalProvider;
import com.ziqi.codesim.next.DynamicEquivalenceOracle;
import com.ziqi.codesim.next.MethodPairDynamicOracle;
import com.ziqi.codesim.next.MethodPairEquivalenceOracle;
import com.ziqi.codesim.next.NextEvidenceExtractor;
import com.ziqi.codesim.next.NextPipelineResult;
import com.ziqi.codesim.next.NextPipelineRunner;
import com.ziqi.codesim.next.RawToolCfgCandidateProvider;
import com.ziqi.codesim.next.RegionCandidate;
import com.ziqi.codesim.next.SemanticEquivalenceOracle;
import com.ziqi.codesim.next.SemanticMethodCandidateProvider;
import com.ziqi.codesim.next.StructuralSimilarityOracle;
import com.ziqi.codesim.next.WalaStructuralSimilarityOracle;
import com.ziqi.codesim.region.RegionAlignmentExtractor;
import com.ziqi.codesim.region.dynamic.DynamicEquivalenceChecker;
import com.ziqi.codesim.region.dynamic.DynamicVerdict;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ziqi.codesim.region.descriptor.NodeDescriptorBuilder;
import com.ziqi.codesim.region.grow.RegionGroup;
import com.ziqi.codesim.region.grow.RegionGrower;
import com.ziqi.codesim.region.model.NodeDescriptor;
import com.ziqi.codesim.region.model.SemanticGraph;
import com.ziqi.codesim.region.sdg.SdgBuilder;
import com.ziqi.codesim.region.seed.SeedMatcher;
import com.ziqi.codesim.region.seed.SeedPair;
import com.ziqi.codesim.region.semantic.EquivalenceVerdict;
import com.ziqi.codesim.region.semantic.MethodSummaryExtractor;
import com.ziqi.codesim.region.semantic.SmtEquivalenceChecker;
import com.ziqi.codesim.region.semantic.SymbolicExpression;
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
import java.util.Map;
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

            // Phase B (semantic): prove which cross-file method pairs compute the same value for all
            // inputs. Feed them both as candidates (so structurally-dissimilar Type-4 pairs enter the
            // pool) and as an equivalence oracle (so the recognizer can confirm them as T4).
            SemanticVerdicts verdicts = semanticVerdicts(leftClasses, rightClasses);
            List<String[]> equivalentPairs = verdicts.equivalent();
            SemanticEquivalenceOracle semanticOracle =
                    MethodPairEquivalenceOracle.fromRawSignaturePairs(equivalentPairs);
            CandidateSignalProvider semanticProvider = new SemanticMethodCandidateProvider(equivalentPairs);

            // Dynamic layer: for the pairs SMT could NOT decide (loops/nonlinear -> UNKNOWN), run both
            // methods on the same random inputs. Agreement on every input is EVIDENCE (not proof) of a
            // Type-4 clone, surfaced by the recognizer as T4_DYNAMIC_EVIDENCE. Pairs SMT proved
            // DIFFERENT are excluded up front (no point sampling a known counterexample).
            List<String[]> dynamicPairs = dynamicEquivalentPairs(leftClasses, rightClasses, verdicts.undecided());
            DynamicEquivalenceOracle dynamicOracle =
                    MethodPairDynamicOracle.fromRawSignaturePairs(dynamicPairs);
            CandidateSignalProvider dynamicProvider =
                    new SemanticMethodCandidateProvider(dynamicPairs, "DYNAMIC_EQUIV_SCAN");

            // Phase A (structural): boundary-free region groups, RECONSTRUCTED in aligned order back
            // into source regions and classified in full (Phase A selects the region, the recognizer
            // classifies it). Unlike the old flat line-set projection, this keeps the alignment so a
            // helper-extracted / reorganized near-copy reads as a syntactic T1/T2/T3 clone; a truly
            // divergent cross-method region carries a marker so it is surfaced, never silently dropped.
            List<RegionCandidate> reconstructedRegions =
                    reconstructedRegionCandidates(leftClasses, rightClasses, leftSource, rightSource);

            return new NextPipelineRunner(
                    List.of(provider, semanticProvider, dynamicProvider),
                    structuralOracle, semanticOracle, dynamicOracle)
                    .run(leftSource, rightSource, reconstructedRegions);
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

    /**
     * Phase A region groups reconstructed into aligned-order source candidates for the recognizer.
     * Each region's spanned methods are rebuilt boundary-free (so helper extraction is compared as one
     * unit) and carry a cross-method marker when they cross methods, so a divergent cross-method clone
     * is surfaced rather than dropped. Replaces the old flat line-set projection.
     */
    private static List<RegionCandidate> reconstructedRegionCandidates(
            Path leftClasses, Path rightClasses, String leftSource, String rightSource)
            throws AnalysisException {
        SdgBuilder sdgBuilder = new SdgBuilder();
        NodeDescriptorBuilder descriptorBuilder = new NodeDescriptorBuilder();
        SemanticGraph leftGraph = sdgBuilder.build(leftClasses, "left");
        SemanticGraph rightGraph = sdgBuilder.build(rightClasses, "right");
        Map<Integer, NodeDescriptor> leftDesc = descriptorBuilder.build(leftGraph);
        Map<Integer, NodeDescriptor> rightDesc = descriptorBuilder.build(rightGraph);
        List<SeedPair> seeds = new SeedMatcher().match(leftDesc, rightDesc);
        List<RegionGroup> regions = new RegionGrower().grow(leftGraph, leftDesc, rightGraph, rightDesc, seeds);

        RegionAlignmentExtractor extractor = new RegionAlignmentExtractor();
        List<RegionCandidate> candidates = new ArrayList<>();
        int index = 1;
        for (RegionGroup region : regions) {
            RegionAlignmentExtractor.Input input = extractor.extract(region, leftGraph, rightGraph);
            if (input.leftSpanLines().isEmpty() || input.rightSpanLines().isEmpty()) {
                continue;
            }
            RegionCandidate candidate = NextEvidenceExtractor.reconstructAlignedRegion(
                    leftSource, input.leftSpanLines(), rightSource, input.rightSpanLines(),
                    input.crossMethod(), String.valueOf(index));
            if (candidate.left().rawTokens().isEmpty() || candidate.right().rawTokens().isEmpty()) {
                continue;
            }
            candidates.add(candidate);
            index++;
        }
        return candidates;
    }

    /**
     * The SMT outcome for every cross-file method pair, split by verdict: {@code equivalent} pairs are
     * proven (T4_CONFIRMED); {@code undecided} pairs (UNKNOWN/UNSUPPORTED -- loops, nonlinear code)
     * are the ones the dynamic layer then samples. Pairs SMT proved DIFFERENT are in neither list.
     */
    private record SemanticVerdicts(List<String[]> equivalent, List<String[]> undecided) {
    }

    /** Run Phase B over the cross product and bucket each pair by SMT verdict. */
    private static SemanticVerdicts semanticVerdicts(Path leftClasses, Path rightClasses)
            throws AnalysisException {
        MethodSummaryExtractor extractor = new MethodSummaryExtractor();
        Map<String, SymbolicExpression> leftSummaries = extractor.extractAll(leftClasses);
        Map<String, SymbolicExpression> rightSummaries = extractor.extractAll(rightClasses);
        SmtEquivalenceChecker checker = new SmtEquivalenceChecker();
        List<String[]> equivalent = new ArrayList<>();
        List<String[]> undecided = new ArrayList<>();
        for (Map.Entry<String, SymbolicExpression> left : leftSummaries.entrySet()) {
            for (Map.Entry<String, SymbolicExpression> right : rightSummaries.entrySet()) {
                EquivalenceVerdict verdict = checker.check(left.getValue(), right.getValue());
                String[] pair = {left.getKey(), right.getKey()};
                if (verdict == EquivalenceVerdict.EQUIVALENT) {
                    equivalent.add(pair);
                } else if (verdict == EquivalenceVerdict.UNKNOWN
                        || verdict == EquivalenceVerdict.UNSUPPORTED) {
                    undecided.add(pair);
                }
            }
        }
        return new SemanticVerdicts(equivalent, undecided);
    }

    /**
     * Of the SMT-undecided pairs, those the dynamic checker found to agree on every sampled input.
     * The class/method names are parsed from the raw WALA signature (e.g. {@code pkg.A.f(I)I}).
     */
    private static List<String[]> dynamicEquivalentPairs(Path leftClasses, Path rightClasses,
                                                         List<String[]> undecided) {
        DynamicEquivalenceChecker checker = new DynamicEquivalenceChecker();
        List<String[]> pairs = new ArrayList<>();
        for (String[] pair : undecided) {
            DynamicVerdict verdict = checker.check(
                    leftClasses, className(pair[0]), methodName(pair[0]),
                    rightClasses, className(pair[1]), methodName(pair[1]));
            if (verdict == DynamicVerdict.LIKELY_EQUIVALENT) {
                pairs.add(pair);
            }
        }
        return pairs;
    }

    /** Fully-qualified declaring class name from a raw WALA method signature ({@code pkg.A.f(I)I} -> {@code pkg.A}). */
    private static String className(String rawSignature) {
        int paren = rawSignature.indexOf('(');
        String qualified = paren >= 0 ? rawSignature.substring(0, paren) : rawSignature;
        int lastDot = qualified.lastIndexOf('.');
        return lastDot >= 0 ? qualified.substring(0, lastDot) : qualified;
    }

    /** Simple method name from a raw WALA method signature ({@code pkg.A.f(I)I} -> {@code f}). */
    private static String methodName(String rawSignature) {
        int paren = rawSignature.indexOf('(');
        String qualified = paren >= 0 ? rawSignature.substring(0, paren) : rawSignature;
        int lastDot = qualified.lastIndexOf('.');
        return lastDot >= 0 ? qualified.substring(lastDot + 1) : qualified;
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
