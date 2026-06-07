package com.ziqi.codesim.next;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NextCandidateDiscovery {
    private static final double BIGCLONEBENCH_T3_MIN_SYNTACTIC_SIMILARITY = 0.50;

    public List<RegionCandidate> discover(EvidencePackage evidencePackage) {
        List<RegionCandidate> candidates = new ArrayList<>();
        int id = 1;
        for (CodeRegion left : evidencePackage.leftRegions()) {
            for (CodeRegion right : evidencePackage.rightRegions()) {
                if (!comparable(left, right)) {
                    continue;
                }
                List<CandidateSource> sources = sources(left, right);
                if (!sources.isEmpty()) {
                    candidates.add(new RegionCandidate(
                            "C" + id++,
                            left,
                            right,
                            sources
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
            sources.add(new CandidateSource("NORMALIZED_TOKEN_KNN_SCAN", normalizedTokenSimilarity));
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
}
