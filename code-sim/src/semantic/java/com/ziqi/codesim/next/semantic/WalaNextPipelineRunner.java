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
import com.ziqi.codesim.next.semantic.compilation.CompilationArtifact;
import com.ziqi.codesim.next.semantic.compilation.JavaCompilationCoordinator;
import com.ziqi.codesim.semantic.backend.AnalysisException;
import com.ziqi.codesim.semantic.backend.wala.WalaAnalysisBackend;
import com.ziqi.codesim.semantic.backend.wala.WalaClassHierarchies;
import com.ziqi.codesim.semantic.backend.wala.raw.WalaRawSnapshotExtractor;
import com.ziqi.codesim.semantic.knn.KnnFeatureView;
import com.ziqi.codesim.semantic.raw.RawToolClass;
import com.ziqi.codesim.semantic.raw.RawToolMethod;
import com.ziqi.codesim.semantic.raw.RawToolProgram;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final JavaCompilationCoordinator compilationCoordinator;

    public WalaNextPipelineRunner() {
        this(new WalaRawSnapshotExtractor(), KnnFeatureView.HYBRID_NUMERIC_HASH, 8,
                new JavaCompilationCoordinator());
    }

    public WalaNextPipelineRunner(WalaRawSnapshotExtractor extractor,
                                  KnnFeatureView view,
                                  int topK) {
        this(extractor, view, topK, new JavaCompilationCoordinator());
    }

    public WalaNextPipelineRunner(WalaRawSnapshotExtractor extractor,
                                  KnnFeatureView view,
                                  int topK,
                                  JavaCompilationCoordinator compilationCoordinator) {
        this.extractor = extractor;
        this.view = view;
        this.topK = topK;
        this.compilationCoordinator = compilationCoordinator;
    }

    public NextPipelineResult run(String leftSource, String rightSource) throws AnalysisException {
        return runDetailed(leftSource, rightSource, stage -> { }).result();
    }

    public PipelineExecution runDetailed(String leftSource, String rightSource) throws AnalysisException {
        return runDetailed(leftSource, rightSource, stage -> { });
    }

    /**
     * As {@link #run(String, String)}, reporting each pipeline stage to {@code progress} the moment it
     * is reached, for a live progress UI. Stage keys, in real execution order: {@code compile},
     * {@code graph}, {@code smt}, {@code dynamic}, {@code regions}, {@code classify}. The key
     * {@code fallback} is reported if WALA is unavailable and the source-only path is used instead.
     */
    public NextPipelineResult run(String leftSource, String rightSource, java.util.function.Consumer<String> progress)
            throws AnalysisException {
        return runDetailed(leftSource, rightSource, progress).result();
    }

    /**
     * Runs the detector and preserves the execution path, per-stage duration, and normalized
     * fallback provenance. Existing {@link #run(String, String)} callers retain the detector-only
     * API; benchmark and observability callers should use this method.
     */
    public PipelineExecution runDetailed(String leftSource, String rightSource,
                                         java.util.function.Consumer<String> progress)
            throws AnalysisException {
        return runDetailed(
                SourceAnalysisInput.standalone(leftSource, "LeftInput.java"),
                SourceAnalysisInput.standalone(rightSource, "RightInput.java"),
                progress);
    }

    public PipelineExecution runDetailed(SourceAnalysisInput leftInput,
                                         SourceAnalysisInput rightInput) throws AnalysisException {
        return runDetailed(leftInput, rightInput, stage -> { });
    }

    /** Pairwise execution with optional, side-specific project/classpath compilation context. */
    public PipelineExecution runDetailed(SourceAnalysisInput leftInput,
                                         SourceAnalysisInput rightInput,
                                         java.util.function.Consumer<String> progress)
            throws AnalysisException {
        ExecutionTrace trace = new ExecutionTrace(progress);
        Map<String, PipelineExecution.CompilationProvenance> compilations = new LinkedHashMap<>();
        try {
            trace.start("compile_left", "compile");
            CompilationArtifact leftCompilation = compilationCoordinator.compile(leftInput);
            compilations.put("left", provenance(leftCompilation));
            trace.success(compilationDetail(leftCompilation));

            trace.start("compile_right", null);
            CompilationArtifact rightCompilation = compilationCoordinator.compile(rightInput);
            compilations.put("right", provenance(rightCompilation));
            trace.success(compilationDetail(rightCompilation));

            Path leftClasses = leftCompilation.classesDirectory();
            Path rightClasses = rightCompilation.classesDirectory();

            trace.start("graph", "graph");
            // Build each side's WALA class hierarchy and IR cache once, then share them between the
            // raw-snapshot extractor (kNN candidates) and the structural backend (CFG/MCS), so the
            // same classes are not analyzed twice.
            ClassHierarchy leftHierarchy = WalaClassHierarchies.build(
                    leftClasses, leftCompilation.supportClasspath());
            ClassHierarchy rightHierarchy = WalaClassHierarchies.build(
                    rightClasses, rightCompilation.supportClasspath());
            AnalysisCacheImpl leftCache = new AnalysisCacheImpl();
            AnalysisCacheImpl rightCache = new AnalysisCacheImpl();
            String leftLabel = leftClasses.toAbsolutePath().toString();
            String rightLabel = rightClasses.toAbsolutePath().toString();

            // Pre-Phase-A discovRE/kNN stages, OFF by default since the A/B below.
            //
            // They cost a full raw snapshot, a kNN index build, and a second WALA analysis pass on
            // every pair, and contribute two things, neither of them wanted:
            //   * RawToolCfgCandidateProvider -> METHOD-level T1/T2/T3 candidates. Phase A regions
            //     never needed them: an aligned region is already a complete comparable unit
            //     (RegionKind.CALL_EXPANDED_REGION) and already carries a non-source channel
            //     (ALIGNED_REGION_SCAN), so it satisfies the recognizer's T3 scope gate twice over.
            //     Those METHOD-level verdicts were also the only reason the main path emitted a
            //     method-level syntactic clone type, which the region-level contract excludes.
            //   * WalaStructuralSimilarityOracle -> a similarity number the recognizer itself
            //     labels "informational; not used to change the type"; it appears in no condition.
            //
            // Measured over 209 pairs (36 labelled clones, 140 negatives), same build, warm cache:
            //   pair-level recall 36/36 and false positives 0/140 -- IDENTICAL either way;
            //   method-level T1/T2/T3 regions 68 -> 0;
            //   Phase A regions actually emitted 124 -> 145, because METHOD candidates outrank
            //     CALL_EXPANDED_REGION in AcceptedRegionSelector.kindPriority and were suppressing
            //     the region-level results they contained (77% -> 52% of candidates suppressed);
            //   graph stage 528s -> 370s (-30%), end to end 5551s -> 4523s (-18%).
            //
            // The classes stay compiled and the switch stays so the comparison can be reproduced
            // on a future corpus: -Dcodesim.legacyCfgChannels=true restores the old behaviour.
            boolean legacyCfgChannels = Boolean.parseBoolean(
                    System.getProperty("codesim.legacyCfgChannels", "false"));
            List<CandidateSignalProvider> cfgProviders = List.of();
            StructuralSimilarityOracle structuralOracle = StructuralSimilarityOracle.NONE;
            if (legacyCfgChannels) {
                RawToolProgram leftProgram = extractor.extract(leftHierarchy, leftCache, leftLabel);
                RawToolProgram rightProgram = extractor.extract(rightHierarchy, rightCache, rightLabel);
                cfgProviders = List.of(new RawToolCfgCandidateProvider(
                        methods(leftProgram), methods(rightProgram), RAW_FILTERED_KEEP, view, topK));
                WalaAnalysisBackend backend = new WalaAnalysisBackend();
                structuralOracle = new WalaStructuralSimilarityOracle(
                        backend.analyze(leftHierarchy, leftCache, leftLabel).methods(),
                        backend.analyze(rightHierarchy, rightCache, rightLabel).methods()
                );
            }
            trace.success();

            boolean stubbedContext = leftCompilation.usesStubs() || rightCompilation.usesStubs();
            SemanticVerdicts verdicts;
            if (stubbedContext) {
                // Generated dependency shells are sufficient to construct WALA graphs for the
                // application methods, but they are not semantic truth. Never let them participate
                // in a strict T4 proof or an observed-behaviour claim.
                trace.skipped("smt", "stubbed_dependency_context_not_eligible_for_t4");
                verdicts = new SemanticVerdicts(List.of(), List.of());
            } else {
                trace.start("smt", "smt");
                // Phase B (semantic): prove which cross-file method pairs compute the same value for
                // all inputs. Feed them both as candidates and as an equivalence oracle.
                verdicts = semanticVerdicts(leftCompilation, rightCompilation);
                trace.success();
            }
            List<String[]> equivalentPairs = verdicts.equivalent();
            SemanticEquivalenceOracle semanticOracle =
                    MethodPairEquivalenceOracle.fromRawSignaturePairs(equivalentPairs);
            CandidateSignalProvider semanticProvider = new SemanticMethodCandidateProvider(equivalentPairs);

            // Dynamic layer: for the pairs SMT could NOT decide (loops/nonlinear -> UNKNOWN), run both
            // methods on the same random inputs. Agreement on every input is EVIDENCE (not proof) of a
            // Type-4 clone, surfaced by the recognizer as T4_DYNAMIC_EVIDENCE. Pairs SMT proved
            // DIFFERENT are excluded up front (no point sampling a known counterexample).
            boolean dynamicDisabled = Boolean.getBoolean("codesim.skipDynamic");
            // -Dcodesim.skipDynamic=true disables the dynamic tier. Required for benchmark sweeps
            // over UNTRUSTED corpus code (e.g. BigCloneBench): the dynamic checker EXECUTES both
            // methods, and arbitrary corpus fragments may spawn processes, touch files, or call
            // System.exit (killing a batch JVM). The syntactic categories never need this tier.
            List<String[]> dynamicPairs;
            boolean dynamicSuppressed = dynamicDisabled || stubbedContext;
            if (dynamicSuppressed) {
                progress.accept("dynamic");
                trace.skipped("dynamic", stubbedContext
                        ? "stubbed_dependency_context_not_eligible_for_t4"
                        : "disabled_by_codesim.skipDynamic");
                dynamicPairs = List.of();
            } else {
                trace.start("dynamic", "dynamic");
                dynamicPairs = dynamicEquivalentPairs(
                        leftCompilation, rightCompilation, verdicts.undecided());
                trace.success();
            }
            DynamicEquivalenceOracle dynamicOracle =
                    MethodPairDynamicOracle.fromRawSignaturePairs(dynamicPairs);
            CandidateSignalProvider dynamicProvider =
                    new SemanticMethodCandidateProvider(dynamicPairs, "DYNAMIC_EQUIV_SCAN");

            // Phase A (structural): boundary-free region groups, RECONSTRUCTED in aligned order back
            // into source regions and classified in full (Phase A selects the region, the recognizer
            // classifies it). Unlike the old flat line-set projection, this keeps the alignment so a
            // helper-extracted / reorganized near-copy reads as a syntactic T1/T2/T3 clone; a truly
            // divergent cross-method region carries a marker so it is surfaced, never silently dropped.
            trace.start("regions", "regions");
            List<RegionCandidate> reconstructedRegions =
                    reconstructedRegionCandidates(leftCompilation, rightCompilation,
                            leftInput.source(), rightInput.source());
            trace.success();

            // Region-only syntactic: T1/T2/T3 come only from the reconstructed Phase A regions;
            // the source-only whole-method/window scans are disabled (includeSourceScans=false). The
            // method-level providers stay for behavioural T4 only. The source-only scans remain the
            // fallback below, used only when WALA is unavailable.
            trace.start("classify", "classify");
            List<CandidateSignalProvider> providers = new ArrayList<>(cfgProviders);
            providers.add(semanticProvider);
            providers.add(dynamicProvider);
            NextPipelineResult result = new NextPipelineRunner(
                    List.copyOf(providers),
                    structuralOracle, semanticOracle, dynamicOracle, false)
                    .run(leftInput.source(), rightInput.source(), reconstructedRegions);
            trace.success();
            return new PipelineExecution(
                    result,
                    analysisMode(leftCompilation, rightCompilation, dynamicSuppressed),
                    trace.outcomes(),
                    "",
                    "",
                    compilations
            );
        } catch (AnalysisException | RuntimeException ex) {
            // CFG analysis unavailable (e.g. the input does not compile standalone, or WALA fails):
            // fall back to the source-only pipeline so the user still gets the syntactic result
            // (just without cfg-sim). This reports real data, never fabricated CFG output.
            System.err.println("[WalaNextPipelineRunner] CFG analysis unavailable, "
                    + "falling back to source-only result: " + ex.getMessage());
            String failedStage = trace.failCurrent(ex);
            trace.start("fallback", "fallback");
            NextPipelineResult fallback = new NextPipelineRunner().run(
                    leftInput.source(), rightInput.source());
            trace.success();
            return new PipelineExecution(
                    fallback,
                    PipelineExecution.AnalysisMode.SOURCE_ONLY_FALLBACK,
                    trace.outcomes(),
                    failedStage,
                    normalizedFailureReason(failedStage, ex),
                    compilations
            );
        }
    }

    private static PipelineExecution.AnalysisMode analysisMode(
            CompilationArtifact left, CompilationArtifact right, boolean dynamicDisabled) {
        boolean stubbed = left.usesStubs() || right.usesStubs();
        // SOURCE_PATH_CONTEXT is real project context too: the supporting classes are the project's
        // own sources compiled under the same contract, not invented code, so it keeps full T4
        // eligibility and reports as project context here. The exact per-side mode stays visible in
        // the compilations provenance block.
        boolean projectContext = isProjectContext(left.mode()) || isProjectContext(right.mode());
        if (stubbed) {
            return dynamicDisabled
                    ? PipelineExecution.AnalysisMode.SOURCE_PLUS_STUBBED_WALA_SMT
                    : PipelineExecution.AnalysisMode.SOURCE_PLUS_STUBBED_WALA_SMT_DYNAMIC;
        }
        if (projectContext) {
            return dynamicDisabled
                    ? PipelineExecution.AnalysisMode.SOURCE_PLUS_PROJECT_CONTEXT_WALA_SMT
                    : PipelineExecution.AnalysisMode.SOURCE_PLUS_PROJECT_CONTEXT_WALA_SMT_DYNAMIC;
        }
        return dynamicDisabled
                ? PipelineExecution.AnalysisMode.SOURCE_PLUS_WALA_SMT
                : PipelineExecution.AnalysisMode.SOURCE_PLUS_WALA_SMT_DYNAMIC;
    }

    private static boolean isProjectContext(CompilationArtifact.CompilationMode mode) {
        return mode == CompilationArtifact.CompilationMode.PROJECT_CONTEXT
                || mode == CompilationArtifact.CompilationMode.SOURCE_PATH_CONTEXT;
    }

    private static PipelineExecution.CompilationProvenance provenance(CompilationArtifact artifact) {
        return new PipelineExecution.CompilationProvenance(
                artifact.mode().name(), artifact.cacheKey(), artifact.cacheHit(),
                artifact.generatedStubCount(), JavaCompilationCoordinator.JAVA_RELEASE,
                artifact.supportClasspath().size(), artifact.diagnosticSummary());
    }

    private static String compilationDetail(CompilationArtifact artifact) {
        return "mode=" + artifact.mode().name()
                + ",cache=" + (artifact.cacheHit() ? "hit" : "miss")
                + ",stubs=" + artifact.generatedStubCount()
                + ",release=" + JavaCompilationCoordinator.JAVA_RELEASE;
    }

    private static String normalizedFailureReason(String stage, Exception ex) {
        if (stage.startsWith("compile_")) {
            return "COMPILATION_FAILED";
        }
        if (ex instanceof AnalysisException) {
            return "ANALYSIS_FAILED";
        }
        return "RUNTIME_ERROR";
    }

    /** Mutable only for the lifetime of one call; the published map is an immutable snapshot. */
    private static final class ExecutionTrace {
        private static final List<String> STAGE_ORDER = List.of(
                "compile_left", "compile_right", "graph", "smt", "dynamic", "regions",
                "classify", "fallback");

        private final java.util.function.Consumer<String> progress;
        private final Map<String, PipelineExecution.StageOutcome> outcomes = new LinkedHashMap<>();
        private String currentStage = "";
        private long currentStartNanos;

        ExecutionTrace(java.util.function.Consumer<String> progress) {
            this.progress = progress;
        }

        void start(String stage, String progressKey) {
            currentStage = stage;
            currentStartNanos = System.nanoTime();
            if (progressKey != null) {
                progress.accept(progressKey);
            }
        }

        void success() {
            success("");
        }

        void success(String detail) {
            if (currentStage.isEmpty()) {
                return;
            }
            outcomes.put(currentStage, new PipelineExecution.StageOutcome(
                    PipelineExecution.StageStatus.SUCCESS, elapsedMs(), detail));
            currentStage = "";
        }

        void skipped(String stage, String detail) {
            outcomes.put(stage, new PipelineExecution.StageOutcome(
                    PipelineExecution.StageStatus.SKIPPED_CONFIG, 0L, detail));
            currentStage = "";
        }

        String failCurrent(Exception ex) {
            String failed = currentStage.isEmpty() ? "unknown" : currentStage;
            if (!currentStage.isEmpty()) {
                outcomes.put(currentStage, new PipelineExecution.StageOutcome(
                        PipelineExecution.StageStatus.FAILED, elapsedMs(), failureDetail(ex)));
            }
            currentStage = "";
            return failed;
        }

        Map<String, PipelineExecution.StageOutcome> outcomes() {
            Map<String, PipelineExecution.StageOutcome> complete = new LinkedHashMap<>();
            for (String stage : STAGE_ORDER) {
                complete.put(stage, outcomes.getOrDefault(stage, new PipelineExecution.StageOutcome(
                        PipelineExecution.StageStatus.NOT_REACHED, 0L, "")));
            }
            return complete;
        }

        private long elapsedMs() {
            return Math.max(0L, (System.nanoTime() - currentStartNanos) / 1_000_000L);
        }

        private static String failureDetail(Exception ex) {
            String message = ex.getMessage();
            String detail = ex.getClass().getSimpleName() + (message == null ? "" : ": " + message);
            return detail.length() <= 500 ? detail : detail.substring(0, 500);
        }
    }

    /**
     * Phase A region groups reconstructed into aligned-order source candidates for the recognizer.
     * Each region's spanned methods are rebuilt boundary-free (so helper extraction is compared as one
     * unit) and carry a cross-method marker when they cross methods, so a divergent cross-method clone
     * is surfaced rather than dropped. Replaces the old flat line-set projection.
     */
    private static List<RegionCandidate> reconstructedRegionCandidates(
            CompilationArtifact leftCompilation, CompilationArtifact rightCompilation,
            String leftSource, String rightSource)
            throws AnalysisException {
        SdgBuilder sdgBuilder = new SdgBuilder();
        NodeDescriptorBuilder descriptorBuilder = new NodeDescriptorBuilder();
        SemanticGraph leftGraph = sdgBuilder.build(leftCompilation.classesDirectory(),
                leftCompilation.supportClasspath(), "left");
        SemanticGraph rightGraph = sdgBuilder.build(rightCompilation.classesDirectory(),
                rightCompilation.supportClasspath(), "right");
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
    private static SemanticVerdicts semanticVerdicts(CompilationArtifact leftCompilation,
                                                      CompilationArtifact rightCompilation)
            throws AnalysisException {
        MethodSummaryExtractor extractor = new MethodSummaryExtractor();
        Map<String, SymbolicExpression> leftSummaries = extractor.extractAll(
                leftCompilation.classesDirectory(), leftCompilation.supportClasspath());
        Map<String, SymbolicExpression> rightSummaries = extractor.extractAll(
                rightCompilation.classesDirectory(), rightCompilation.supportClasspath());
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
    private static List<String[]> dynamicEquivalentPairs(CompilationArtifact leftCompilation,
                                                         CompilationArtifact rightCompilation,
                                                         List<String[]> undecided) {
        DynamicEquivalenceChecker checker = new DynamicEquivalenceChecker();
        List<String[]> pairs = new ArrayList<>();
        for (String[] pair : undecided) {
            DynamicVerdict verdict = checker.check(
                    leftCompilation.classesDirectory(), leftCompilation.supportClasspath(),
                    className(pair[0]), methodName(pair[0]), pair[0],
                    rightCompilation.classesDirectory(), rightCompilation.supportClasspath(),
                    className(pair[1]), methodName(pair[1]), pair[1]);
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

}
