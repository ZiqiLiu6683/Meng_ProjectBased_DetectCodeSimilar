package com.ziqi.codesim.next;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NextCandidateDiscovery {
    private static final double BIGCLONEBENCH_T3_MIN_SYNTACTIC_SIMILARITY = 0.50;
    private final List<CandidateSignalProvider> signalProviders;
    // When false, the built-in source-only scans (whole-method / file / window text+AST matching) are
    // skipped, so ONLY external provider signals (Phase B semantic + dynamic method pairs) create
    // candidates. The WALA region pipeline uses this: T1/T2/T3 comes from Phase A regions, not from
    // method-level source scans; those scans are only the WALA-unavailable fallback.
    private final boolean includeSourceScans;

    public NextCandidateDiscovery() {
        this(List.of());
    }

    public NextCandidateDiscovery(List<CandidateSignalProvider> signalProviders) {
        this(signalProviders, true);
    }

    public NextCandidateDiscovery(List<CandidateSignalProvider> signalProviders, boolean includeSourceScans) {
        this.signalProviders = List.copyOf(signalProviders);
        this.includeSourceScans = includeSourceScans;
    }

    public List<RegionCandidate> discover(EvidencePackage evidencePackage) {
        Map<CandidateKey, List<CandidateSource>> externalSources = externalSources(evidencePackage);
        List<RegionCandidate> candidates = new ArrayList<>();
        int id = 1;
        for (CodeRegion left : evidencePackage.leftRegions()) {
            for (CodeRegion right : evidencePackage.rightRegions()) {
                if (!comparable(left, right)) {
                    continue;
                }
                List<CandidateSource> sources = new ArrayList<>(
                        includeSourceScans ? sources(left, right) : List.of());
                sources.addAll(externalSources.getOrDefault(
                        new CandidateKey(left.regionId(), right.regionId()),
                        List.of()
                ));
                if (!sources.isEmpty()) {
                    candidates.add(new RegionCandidate(
                            "C" + id++,
                            left,
                            right,
                            List.copyOf(sources)
                    ));
                }
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparingDouble(NextCandidateDiscovery::bestScore).reversed()
                        .thenComparing(c -> c.left().regionId())
                        .thenComparing(c -> c.right().regionId()))
                .toList();
    }

    private Map<CandidateKey, List<CandidateSource>> externalSources(EvidencePackage evidencePackage) {
        Map<CandidateKey, List<CandidateSource>> sources = new HashMap<>();
        for (CandidateSignalProvider provider : signalProviders) {
            for (CandidateSignal signal : provider.findSignals(evidencePackage)) {
                CandidateKey key = new CandidateKey(signal.leftRegionId(), signal.rightRegionId());
                sources.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(new CandidateSource(signal.channel(), signal.score()));
            }
        }
        return sources;
    }

    private static boolean comparable(CodeRegion left, CodeRegion right) {
        if (left.kind() == RegionKind.FILE || right.kind() == RegionKind.FILE) {
            return left.kind() == right.kind();
        }
        return true;
    }

    private static List<CandidateSource> sources(CodeRegion left, CodeRegion right) {
        List<CandidateSource> sources = new ArrayList<>();
        if (!left.t1ComparableTokens().isEmpty()
                && left.t1ComparableTokens().equals(right.t1ComparableTokens())) {
            sources.add(new CandidateSource("EXACT_TEXT_SCAN", 1.0));
        }
        if (!left.t2NormalizedTokens().isEmpty()
                && left.t2NormalizedTokens().equals(right.t2NormalizedTokens())) {
            sources.add(new CandidateSource("NORMALIZED_AST_SCAN", 1.0));
        }

        double normalizedTokenSimilarity = NextEvidenceExtractor.jaccardSimilarity(
                left.t2NormalizedTokens(),
                right.t2NormalizedTokens()
        );
        if (normalizedTokenSimilarity >= BIGCLONEBENCH_T3_MIN_SYNTACTIC_SIMILARITY) {
            // Jaccard overlap of normalized tokens (not a kNN scan): renamed for honesty.
            sources.add(new CandidateSource("NORMALIZED_TOKEN_OVERLAP_SCAN", normalizedTokenSimilarity));
        }

        double statementSimilarity = NextEvidenceExtractor.lcsSimilarity(
                left.normalizedStatementTexts(),
                right.normalizedStatementTexts()
        );
        if (statementSimilarity >= BIGCLONEBENCH_T3_MIN_SYNTACTIC_SIMILARITY) {
            sources.add(new CandidateSource("STATEMENT_DIFF_SCAN", statementSimilarity));
        }
        return List.copyOf(sources);
    }

    private static double bestScore(RegionCandidate candidate) {
        return candidate.sources().stream()
                .mapToDouble(CandidateSource::score)
                .max()
                .orElse(0.0);
    }

    private record CandidateKey(String leftRegionId, String rightRegionId) {
    }
}
