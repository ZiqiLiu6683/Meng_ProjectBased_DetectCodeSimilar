package com.ziqi.codesim.semantic.knn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExactKnnIndex {
    private final List<StandardizedFeatureVector> vectors;

    public ExactKnnIndex(List<StandardizedFeatureVector> vectors) {
        this.vectors = List.copyOf(vectors);
    }

    public List<BlockCandidate> query(StandardizedFeatureVector query, int k) {
        List<BlockCandidate> candidates = new ArrayList<>();
        for (StandardizedFeatureVector candidate : vectors) {
            if (candidate.itemId().equals(query.itemId())) {
                continue;
            }
            candidates.add(new BlockCandidate(
                    query.itemId(),
                    candidate.itemId(),
                    query.view(),
                    euclideanDistance(query.values(), candidate.values()),
                    sharedContributions(query, candidate)
            ));
        }
        candidates.sort(Comparator
                .comparingDouble(BlockCandidate::distance)
                .thenComparing(BlockCandidate::candidateBlockId));
        if (candidates.size() <= k) {
            return candidates;
        }
        return List.copyOf(candidates.subList(0, k));
    }

    private static double euclideanDistance(Map<String, Double> left, Map<String, Double> right) {
        Set<String> features = new HashSet<>();
        features.addAll(left.keySet());
        features.addAll(right.keySet());
        double sum = 0.0;
        for (String feature : features) {
            double delta = left.getOrDefault(feature, 0.0) - right.getOrDefault(feature, 0.0);
            sum += delta * delta;
        }
        return Math.sqrt(sum);
    }

    private static List<FeatureContribution> sharedContributions(
            StandardizedFeatureVector query,
            StandardizedFeatureVector candidate) {
        List<FeatureContribution> shared = new ArrayList<>();
        for (String feature : query.provenance().keySet()) {
            if (!candidate.provenance().containsKey(feature)) {
                continue;
            }
            shared.addAll(query.provenance().get(feature));
            shared.addAll(candidate.provenance().get(feature));
        }
        return shared;
    }
}
