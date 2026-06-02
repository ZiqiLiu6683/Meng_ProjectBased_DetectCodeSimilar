package com.ziqi.codesim.semantic.knn;

import java.util.List;

public record FeatureContribution(
        String featureName,
        String channel,
        String rawValue,
        List<String> provenance
) {
    public FeatureContribution {
        provenance = List.copyOf(provenance);
    }
}
