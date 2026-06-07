package com.ziqi.codesim.next;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CandidateMergerRanker {
    public List<RankedRegionCandidate> mergeAndRank(List<RegionCandidate> candidates) {
        Map<CandidateKey, CandidateAccumulator> merged = new LinkedHashMap<>();
        for (RegionCandidate candidate : candidates) {
            CandidateKey key = new CandidateKey(candidate.left().regionId(), candidate.right().regionId());
            merged.computeIfAbsent(key, ignored -> new CandidateAccumulator(candidate))
                    .add(candidate.sources());
        }

        return merged.values().stream()
                .map(CandidateAccumulator::toRanked)
                .sorted(Comparator.comparingDouble(RankedRegionCandidate::rankScore).reversed()
                        .thenComparing(r -> r.candidate().left().regionId())
                        .thenComparing(r -> r.candidate().right().regionId()))
                .toList();
    }

    private static double rankScore(RegionCandidate candidate) {
        double bestSource = candidate.sources().stream()
                .mapToDouble(CandidateSource::score)
                .max()
                .orElse(0.0);
        double sourceDiversity = Math.min(1.0, uniqueChannels(candidate).size() / 4.0);
        double coverage = Math.min(1.0,
                (candidate.left().tokenCount() + candidate.right().tokenCount()) / 120.0);
        double kindPriority = kindPriority(candidate.left().kind(), candidate.right().kind());
        double cfgBonus = hasChannel(candidate, "CFG_KNN_SCAN") ? 0.12 : 0.0;
        return 0.42 * bestSource
                + 0.22 * sourceDiversity
                + 0.18 * coverage
                + 0.18 * kindPriority
                + cfgBonus;
    }

    private static List<String> rankingReasons(RegionCandidate candidate) {
        List<String> reasons = new ArrayList<>();
        Set<String> channels = uniqueChannels(candidate);
        reasons.add("evidence_sources=" + channels.size());
        reasons.add("best_source_score=" + String.format("%.4f", bestSource(candidate)));
        reasons.add("region_tokens=" + (candidate.left().tokenCount() + candidate.right().tokenCount()));
        reasons.add("region_kinds=" + candidate.left().kind() + "<->" + candidate.right().kind());
        if (hasChannel(candidate, "CFG_KNN_SCAN")) {
            reasons.add("cfg_knn_candidate");
        }
        if (candidate.left().kind() == RegionKind.STATEMENT_WINDOW_REGION
                || candidate.right().kind() == RegionKind.STATEMENT_WINDOW_REGION) {
            reasons.add("statement_window_lower_priority");
        }
        return List.copyOf(reasons);
    }

    private static double bestSource(RegionCandidate candidate) {
        return candidate.sources().stream()
                .mapToDouble(CandidateSource::score)
                .max()
                .orElse(0.0);
    }

    private static Set<String> uniqueChannels(RegionCandidate candidate) {
        return candidate.sources().stream()
                .map(CandidateSource::channel)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean hasChannel(RegionCandidate candidate, String channel) {
        return candidate.sources().stream().anyMatch(source -> source.channel().equals(channel));
    }

    private static double kindPriority(RegionKind left, RegionKind right) {
        if (left == RegionKind.METHOD && right == RegionKind.METHOD) {
            return 1.0;
        }
        if (left == RegionKind.METHOD_BODY_REGION && right == RegionKind.METHOD_BODY_REGION) {
            return 0.92;
        }
        if ((left == RegionKind.METHOD || left == RegionKind.METHOD_BODY_REGION)
                && (right == RegionKind.METHOD || right == RegionKind.METHOD_BODY_REGION)) {
            return 0.88;
        }
        if (left == RegionKind.BLOCK_SEQUENCE_REGION || right == RegionKind.BLOCK_SEQUENCE_REGION) {
            return 0.70;
        }
        if (left == RegionKind.CONTROL_REGION || right == RegionKind.CONTROL_REGION) {
            return 0.62;
        }
        if (left == RegionKind.STATEMENT_WINDOW_REGION || right == RegionKind.STATEMENT_WINDOW_REGION) {
            return 0.45;
        }
        return 0.50;
    }

    private record CandidateKey(String leftRegionId, String rightRegionId) {
    }

    private static final class CandidateAccumulator {
        private final RegionCandidate first;
        private final List<CandidateSource> sources = new ArrayList<>();

        CandidateAccumulator(RegionCandidate first) {
            this.first = first;
        }

        void add(List<CandidateSource> newSources) {
            sources.addAll(newSources);
        }

        RankedRegionCandidate toRanked() {
            RegionCandidate merged = new RegionCandidate(
                    first.candidateId(),
                    first.left(),
                    first.right(),
                    deduplicateSources(sources)
            );
            return new RankedRegionCandidate(merged, rankScore(merged), rankingReasons(merged));
        }

        private static List<CandidateSource> deduplicateSources(List<CandidateSource> sources) {
            Map<String, CandidateSource> byChannel = new LinkedHashMap<>();
            for (CandidateSource source : sources) {
                byChannel.merge(
                        source.channel(),
                        source,
                        (left, right) -> left.score() >= right.score() ? left : right
                );
            }
            return List.copyOf(byChannel.values());
        }
    }
}
