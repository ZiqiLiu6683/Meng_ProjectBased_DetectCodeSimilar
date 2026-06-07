package com.ziqi.codesim.next;

import java.util.List;

public class NextPipelineRunner {
    private final NextEvidenceExtractor evidenceExtractor;
    private final NextCandidateDiscovery candidateDiscovery;
    private final CandidateMergerRanker candidateMergerRanker;
    private final NextRegionTypeRecognizer regionTypeRecognizer;

    public NextPipelineRunner() {
        this(new NextEvidenceExtractor(),
                new NextCandidateDiscovery(),
                new NextRegionTypeRecognizer());
    }

    public NextPipelineRunner(List<CandidateSignalProvider> signalProviders) {
        this(new NextEvidenceExtractor(),
                new NextCandidateDiscovery(signalProviders),
                new NextRegionTypeRecognizer());
    }

    NextPipelineRunner(NextEvidenceExtractor evidenceExtractor,
                       NextCandidateDiscovery candidateDiscovery,
                       NextRegionTypeRecognizer regionTypeRecognizer) {
        this.evidenceExtractor = evidenceExtractor;
        this.candidateDiscovery = candidateDiscovery;
        this.candidateMergerRanker = new CandidateMergerRanker();
        this.regionTypeRecognizer = regionTypeRecognizer;
    }

    public NextPipelineResult run(String leftSource, String rightSource) {
        EvidencePackage evidencePackage = evidenceExtractor.extract(leftSource, rightSource);
        List<RegionCandidate> candidates = candidateDiscovery.discover(evidencePackage);
        List<RankedRegionCandidate> rankedCandidates = candidateMergerRanker.mergeAndRank(candidates);
        List<RegionDecision> decisions = rankedCandidates.stream()
                .map(RankedRegionCandidate::candidate)
                .map(regionTypeRecognizer::decide)
                .toList();
        return new NextPipelineResult(evidencePackage, candidates, rankedCandidates, decisions);
    }
}
