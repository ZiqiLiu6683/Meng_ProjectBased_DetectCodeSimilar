package com.ziqi.codesim.semantic.knn;

import java.util.List;
import java.util.Map;

public record StandardizedFeatureVector(
        String itemId,
        KnnFeatureView view,
        Map<String, Double> values,
        Map<String, List<FeatureContribution>> provenance
) {
    public StandardizedFeatureVector {
        values = Map.copyOf(values);
        provenance = Map.copyOf(provenance);
    }
}
