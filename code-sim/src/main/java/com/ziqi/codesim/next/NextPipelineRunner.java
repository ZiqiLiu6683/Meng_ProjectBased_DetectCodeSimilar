package com.ziqi.codesim.next;

import java.util.List;

public class NextPipelineRunner {
    private final NextEvidenceExtractor evidenceExtractor;
    private final NextCandidateDiscovery candidateDiscovery;
    private final CandidateMergerRanker candidateMergerRanker;
    private final NextRegionTypeRecognizer regionTypeRecognizer;
    private final AcceptedRegionSelector acceptedRegionSelector;
    private final FileLevelAggregator fileLevelAggregator;

    public NextPipelineRunner() {
        this(new NextEvidenceExtractor(),
                new NextCandidateDiscovery(),
                new NextRegionTypeRecognizer());
    }

    public NextPipelineRunner(List<CandidateSignalProvider> signalProviders) {
        this(signalProviders, StructuralSimilarityOracle.NONE);
    }

    public NextPipelineRunner(List<CandidateSignalProvider> signalProviders,
                              StructuralSimilarityOracle structuralOracle) {
        this(new NextEvidenceExtractor(),
                new NextCandidateDiscovery(signalProviders),
                new NextRegionTypeRecognizer(structuralOracle));
    }

    public NextPipelineRunner(List<CandidateSignalProvider> signalProviders,
                              StructuralSimilarityOracle structuralOracle,
                              SemanticEquivalenceOracle semanticOracle) {
        this(new NextEvidenceExtractor(),
                new NextCandidateDiscovery(signalProviders),
                new NextRegionTypeRecognizer(structuralOracle, semanticOracle));
    }

    public NextPipelineRunner(List<CandidateSignalProvider> signalProviders,
                              StructuralSimilarityOracle structuralOracle,
                              SemanticEquivalenceOracle semanticOracle,
                              StructuralRegionOracle structuralRegionOracle) {
        this(new NextEvidenceExtractor(),
                new NextCandidateDiscovery(signalProviders),
                new NextRegionTypeRecognizer(structuralOracle, semanticOracle, structuralRegionOracle));
    }

    public NextPipelineRunner(List<CandidateSignalProvider> signalProviders,
                              StructuralSimilarityOracle structuralOracle,
                              SemanticEquivalenceOracle semanticOracle,
                              DynamicEquivalenceOracle dynamicOracle) {
        this(new NextEvidenceExtractor(),
                new NextCandidateDiscovery(signalProviders),
                new NextRegionTypeRecognizer(structuralOracle, semanticOracle,
                        StructuralRegionOracle.NONE, dynamicOracle));
    }

    NextPipelineRunner(NextEvidenceExtractor evidenceExtractor,
                       NextCandidateDiscovery candidateDiscovery,
                       NextRegionTypeRecognizer regionTypeRecognizer) {
        this.evidenceExtractor = evidenceExtractor;
        this.candidateDiscovery = candidateDiscovery;
        this.candidateMergerRanker = new CandidateMergerRanker();
        this.regionTypeRecognizer = regionTypeRecognizer;
        this.acceptedRegionSelector = new AcceptedRegionSelector();
        this.fileLevelAggregator = new FileLevelAggregator();
    }

    public NextPipelineResult run(String leftSource, String rightSource) {
        return run(leftSource, rightSource, List.of());
    }

    /**
     * @param extraCandidates pre-formed candidates to classify alongside the discovered ones (e.g.
     *                        Phase A structural region groups projected back to source regions).
     */
    public NextPipelineResult run(String leftSource, String rightSource,
                                  List<RegionCandidate> extraCandidates) {
        EvidencePackage evidencePackage = evidenceExtractor.extract(leftSource, rightSource);
        List<RegionCandidate> candidates = new java.util.ArrayList<>(candidateDiscovery.discover(evidencePackage));
        candidates.addAll(extraCandidates);
        List<RankedRegionCandidate> rankedCandidates = candidateMergerRanker.mergeAndRank(candidates);
        List<RegionDecision> decisions = rankedCandidates.stream()
                .map(RankedRegionCandidate::candidate)
                .map(regionTypeRecognizer::decide)
                .toList();
        SelectedRegionDecisions selected = acceptedRegionSelector.select(decisions);
        FileCloneSummary fileSummary = fileLevelAggregator.aggregate(evidencePackage, selected.selectedDecisions());
        return new NextPipelineResult(
                evidencePackage,
                candidates,
                rankedCandidates,
                decisions,
                selected.selectedDecisions(),
                selected.summary(),
                fileSummary
        );
    }
}
