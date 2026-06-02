package com.ziqi.codesim.semantic.knn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FeaturePreprocessor {
    private final Map<String, Double> means;
    private final Map<String, Double> standardDeviations;

    private FeaturePreprocessor(
            Map<String, Double> means,
            Map<String, Double> standardDeviations) {
        this.means = Map.copyOf(means);
        this.standardDeviations = Map.copyOf(standardDeviations);
    }

    public static FeaturePreprocessor fit(Collection<RawFeatureVector> vectors) {
        Set<String> features = new HashSet<>();
        for (RawFeatureVector vector : vectors) {
            features.addAll(vector.values().keySet());
        }
        Map<String, Double> means = new HashMap<>();
        Map<String, Double> standardDeviations = new HashMap<>();
        for (String feature : features) {
            List<Double> transformed = new ArrayList<>();
            for (RawFeatureVector vector : vectors) {
                transformed.add(logTransform(vector.values().getOrDefault(feature, 0.0)));
            }
            double mean = average(transformed);
            double std = standardDeviation(transformed, mean);
            if (std > 0.0) {
                means.put(feature, mean);
                standardDeviations.put(feature, std);
            }
        }
        return new FeaturePreprocessor(means, standardDeviations);
    }

    public StandardizedFeatureVector transform(RawFeatureVector vector) {
        Map<String, Double> transformed = new HashMap<>();
        for (String feature : standardDeviations.keySet()) {
            double value = logTransform(vector.values().getOrDefault(feature, 0.0));
            transformed.put(feature, (value - means.get(feature)) / standardDeviations.get(feature));
        }
        return new StandardizedFeatureVector(
                vector.itemId(),
                vector.view(),
                transformed,
                vector.provenance()
        );
    }

    private static double logTransform(double value) {
        return Math.log10(value + 1.0);
    }

    private static double average(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.size();
    }

    private static double standardDeviation(List<Double> values, double mean) {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double value : values) {
            double delta = value - mean;
            sum += delta * delta;
        }
        return Math.sqrt(sum / values.size());
    }
}
