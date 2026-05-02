package com.ziqi.codesim.pipeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

public class Stage3Aggregator {
    private static final int MIN_METHOD_SIZE = 10;
    private static final int LEAST_COVERED_LIMIT = 3;

    public Stage3Result compute(Stage1Result stage1, Stage2Result stage2) {
        if (stage2.status() == SignalStatus.NOT_APPLICABLE
                || stage1.methodsA().isEmpty()
                || stage1.methodsB().isEmpty()) {
            return notApplicable(stage1);
        }

        Map<String, MethodDescriptor> methodsA = toMethodMap(stage1.methodsA());
        Map<String, MethodDescriptor> methodsB = toMethodMap(stage1.methodsB());
        Map<PairKey, MethodPairFeature> featureByKey = stage2.pairFeatures().stream()
                .collect(Collectors.toMap(
                        f -> new PairKey(f.methodAId(), f.methodBId()),
                        f -> f
                ));

        Map<String, ScoredPair> forward = bestByA(stage2.pairFeatures());
        Map<String, ScoredPair> backward = bestByB(stage2.pairFeatures());
        List<MergedPairFeature> mergedPairs = mergeMatches(forward, backward, featureByKey);
        Set<Stage3Flag> flags = EnumSet.noneOf(Stage3Flag.class);
        assignGeneralWeights(mergedPairs, flags);

        if (mergedPairs.isEmpty()) {
            flags.add(Stage3Flag.LOW_EVIDENCE);
        }

        double totalWeight = mergedPairs.stream().mapToDouble(MergedPairFeature::generalWeight).sum();
        boolean useEqualWeights = totalWeight == 0.0 && !mergedPairs.isEmpty();
        if (useEqualWeights) {
            flags.add(Stage3Flag.LOW_EVIDENCE);
            totalWeight = mergedPairs.size();
        }

        Weighting weighting = new Weighting(totalWeight, useEqualWeights);
        CoverageResult coverage = computeCoverage(mergedPairs, methodsA, methodsB);
        double confirmedRatio = mergedPairs.isEmpty() ? 0.0
                : (double) mergedPairs.stream().filter(MergedPairFeature::confirmed).count()
                / mergedPairs.size();

        double partialAInB = weightedAverage(mergedPairs,
                p -> p.feature().containmentAInB() * (1.0 - p.feature().sizeRatio()),
                weighting);
        double partialBInA = weightedAverage(mergedPairs,
                p -> p.feature().containmentBInA() * (1.0 - p.feature().sizeRatio()),
                weighting);
        double partialCloneSignal = Math.max(partialAInB, partialBInA);

        double centroidS3 = weightedAverage(mergedPairs, p -> p.feature().s3(), weighting);
        double centroidS4 = weightedAverage(mergedPairs, p -> p.feature().s4(), weighting);

        if (Math.abs(coverage.coverageA() - coverage.coverageB()) >= 0.25) {
            flags.add(Stage3Flag.ASYMMETRIC_COVERAGE);
        }
        if (partialCloneSignal >= 0.45) {
            flags.add(Stage3Flag.PARTIAL_PATTERN_PRESENT);
        }
        if (confirmedRatio < 0.5 && !mergedPairs.isEmpty()) {
            flags.add(Stage3Flag.MANY_UNCONFIRMED_MATCHES);
        }

        double varianceS3 = weightedVariance(mergedPairs, p -> p.feature().s3(), centroidS3, weighting);
        double varianceS4 = weightedVariance(mergedPairs, p -> p.feature().s4(), centroidS4, weighting);
        if (varianceS3 >= 0.10 || varianceS4 >= 0.10) {
            flags.add(Stage3Flag.HIGH_VARIANCE);
        }

        return new Stage3Result(
                SignalStatus.COMPUTED,
                weightedAverage(mergedPairs, p -> p.feature().magnitude(), weighting),
                weightedAverage(mergedPairs, MergedPairFeature::matchScore, weighting),
                centroidS3,
                centroidS4,
                weightedAverage(mergedPairs, p -> p.feature().tokenStructureDivergence(), weighting),
                weightedAverage(mergedPairs, p -> p.feature().spread(), weighting),
                weightedAverage(mergedPairs, p -> p.feature().structuralExactness(), weighting),
                weightedAverage(mergedPairs, p -> p.feature().tokenExactGap(), weighting),
                varianceS3,
                varianceS4,
                coverage.coverageA(),
                coverage.coverageB(),
                coverage.confirmedCoverageA(),
                coverage.confirmedCoverageB(),
                confirmedRatio,
                partialCloneSignal,
                partialAInB,
                partialBInA,
                mergedPairs,
                leastCovered(coverage.coverageByA()),
                leastCovered(coverage.coverageByB()),
                stage1.s1(),
                stage1.s5Status(),
                stage1.s5(),
                Set.copyOf(flags)
        );
    }

    private static Stage3Result notApplicable(Stage1Result stage1) {
        return new Stage3Result(
                SignalStatus.NOT_APPLICABLE,
                0, 0,
                0, 0,
                0, 0,
                0, 0,
                0, 0,
                0, 0,
                0, 0,
                0,
                0, 0, 0,
                List.of(),
                List.of(),
                List.of(),
                stage1.s1(),
                stage1.s5Status(),
                stage1.s5(),
                Set.of()
        );
    }

    private static Map<String, MethodDescriptor> toMethodMap(List<MethodDescriptor> methods) {
        Map<String, MethodDescriptor> map = new LinkedHashMap<>();
        for (MethodDescriptor method : methods) {
            map.put(method.methodId(), method);
        }
        return map;
    }

    private static Map<String, ScoredPair> bestByA(List<MethodPairFeature> features) {
        Map<String, ScoredPair> best = new HashMap<>();
        for (MethodPairFeature feature : features) {
            best.merge(feature.methodAId(), score(feature), Stage3Aggregator::better);
        }
        return best;
    }

    private static Map<String, ScoredPair> bestByB(List<MethodPairFeature> features) {
        Map<String, ScoredPair> best = new HashMap<>();
        for (MethodPairFeature feature : features) {
            best.merge(feature.methodBId(), score(feature), Stage3Aggregator::better);
        }
        return best;
    }

    private static ScoredPair score(MethodPairFeature feature) {
        double maxContainment = Math.max(feature.containmentAInB(), feature.containmentBInA());
        double containmentScore = maxContainment * Math.sqrt(feature.sizeRatio());
        double matchScore = Math.max(feature.magnitude(), containmentScore);
        MatchReason reason = containmentScore > feature.magnitude()
                ? MatchReason.CONTAINMENT_DOMINANT
                : MatchReason.MAGNITUDE_DOMINANT;
        return new ScoredPair(feature, matchScore, reason);
    }

    private static ScoredPair better(ScoredPair a, ScoredPair b) {
        int cmp = Double.compare(a.matchScore(), b.matchScore());
        if (cmp != 0) return cmp > 0 ? a : b;
        cmp = Double.compare(a.feature().magnitude(), b.feature().magnitude());
        if (cmp != 0) return cmp > 0 ? a : b;
        cmp = Double.compare(a.feature().structuralExactness(), b.feature().structuralExactness());
        if (cmp != 0) return cmp > 0 ? a : b;
        cmp = Double.compare(a.feature().sizeRatio(), b.feature().sizeRatio());
        if (cmp != 0) return cmp > 0 ? a : b;
        String keyA = a.feature().methodAId() + "\0" + a.feature().methodBId();
        String keyB = b.feature().methodAId() + "\0" + b.feature().methodBId();
        return keyA.compareTo(keyB) <= 0 ? a : b;
    }

    private static List<MergedPairFeature> mergeMatches(
            Map<String, ScoredPair> forward,
            Map<String, ScoredPair> backward,
            Map<PairKey, MethodPairFeature> featureByKey) {
        Map<PairKey, DirectionAccumulator> directions = new LinkedHashMap<>();
        for (ScoredPair pair : forward.values()) {
            PairKey key = new PairKey(pair.feature().methodAId(), pair.feature().methodBId());
            directions.computeIfAbsent(key, ignored -> new DirectionAccumulator()).forward = true;
        }
        for (ScoredPair pair : backward.values()) {
            PairKey key = new PairKey(pair.feature().methodAId(), pair.feature().methodBId());
            directions.computeIfAbsent(key, ignored -> new DirectionAccumulator()).backward = true;
        }

        List<MergedPairFeature> merged = new ArrayList<>();
        for (Map.Entry<PairKey, DirectionAccumulator> entry : directions.entrySet()) {
            MethodPairFeature feature = featureByKey.get(entry.getKey());
            ScoredPair scored = score(feature);
            DirectionAccumulator direction = entry.getValue();
            boolean confirmed = direction.forward && direction.backward;
            MatchDirection matchDirection = confirmed
                    ? MatchDirection.BIDIRECTIONAL
                    : direction.forward ? MatchDirection.A_TO_B_ONLY : MatchDirection.B_TO_A_ONLY;
            merged.add(new MergedPairFeature(
                    feature.methodAId(),
                    feature.methodBId(),
                    feature,
                    scored.matchScore(),
                    scored.reason(),
                    matchDirection,
                    confirmed,
                    0.0
            ));
        }
        return merged;
    }

    private static void assignGeneralWeights(List<MergedPairFeature> pairs, Set<Stage3Flag> flags) {
        for (int i = 0; i < pairs.size(); i++) {
            MergedPairFeature pair = pairs.get(i);
            double reliability = Math.min(1.0,
                    (double) Math.min(pair.feature().sizeA(), pair.feature().sizeB()) / MIN_METHOD_SIZE);
            double weight = pair.matchScore() * reliability;
            if (weight == 0.0) {
                flags.add(Stage3Flag.LOW_EVIDENCE);
            }
            pairs.set(i, new MergedPairFeature(
                    pair.methodAId(),
                    pair.methodBId(),
                    pair.feature(),
                    pair.matchScore(),
                    pair.matchReason(),
                    pair.direction(),
                    pair.confirmed(),
                    weight
            ));
        }
    }

    private static CoverageResult computeCoverage(List<MergedPairFeature> pairs,
                                                  Map<String, MethodDescriptor> methodsA,
                                                  Map<String, MethodDescriptor> methodsB) {
        Map<String, Double> coverageByA = new HashMap<>();
        Map<String, Double> coverageByB = new HashMap<>();
        for (String id : methodsA.keySet()) coverageByA.put(id, 0.0);
        for (String id : methodsB.keySet()) coverageByB.put(id, 0.0);

        Set<String> confirmedA = new HashSet<>();
        Set<String> confirmedB = new HashSet<>();
        for (MergedPairFeature pair : pairs) {
            coverageByA.merge(pair.methodAId(), pair.matchScore(), Math::max);
            coverageByB.merge(pair.methodBId(), pair.matchScore(), Math::max);
            if (pair.confirmed()) {
                confirmedA.add(pair.methodAId());
                confirmedB.add(pair.methodBId());
            }
        }

        double coverageA = sizeWeightedCoverage(coverageByA, methodsA);
        double coverageB = sizeWeightedCoverage(coverageByB, methodsB);
        double confirmedCoverageA = sizeShare(confirmedA, methodsA);
        double confirmedCoverageB = sizeShare(confirmedB, methodsB);
        return new CoverageResult(
                coverageA,
                coverageB,
                confirmedCoverageA,
                confirmedCoverageB,
                coverageByA,
                coverageByB
        );
    }

    private static double sizeWeightedCoverage(Map<String, Double> coverage,
                                               Map<String, MethodDescriptor> methods) {
        double num = 0.0;
        double den = 0.0;
        for (MethodDescriptor method : methods.values()) {
            num += method.treeSize() * coverage.getOrDefault(method.methodId(), 0.0);
            den += method.treeSize();
        }
        return den == 0.0 ? 0.0 : num / den;
    }

    private static double sizeShare(Set<String> included, Map<String, MethodDescriptor> methods) {
        double num = 0.0;
        double den = 0.0;
        for (MethodDescriptor method : methods.values()) {
            if (included.contains(method.methodId())) {
                num += method.treeSize();
            }
            den += method.treeSize();
        }
        return den == 0.0 ? 0.0 : num / den;
    }

    private static List<MethodCoverage> leastCovered(Map<String, Double> coverage) {
        return coverage.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(LEAST_COVERED_LIMIT)
                .map(e -> new MethodCoverage(e.getKey(), e.getValue()))
                .toList();
    }

    private static double weightedAverage(List<MergedPairFeature> pairs,
                                          ToDoubleFunction<MergedPairFeature> value,
                                          Weighting weighting) {
        if (pairs.isEmpty()) return 0.0;
        if (weighting.useEqualWeights()) {
            return pairs.stream().mapToDouble(value).average().orElse(0.0);
        }
        double num = 0.0;
        for (MergedPairFeature pair : pairs) {
            num += value.applyAsDouble(pair) * pair.generalWeight();
        }
        return weighting.totalWeight() == 0.0 ? 0.0 : num / weighting.totalWeight();
    }

    private static double weightedVariance(List<MergedPairFeature> pairs,
                                           ToDoubleFunction<MergedPairFeature> value,
                                           double mean,
                                           Weighting weighting) {
        return weightedAverage(pairs, p -> {
            double delta = value.applyAsDouble(p) - mean;
            return delta * delta;
        }, weighting);
    }

    private record PairKey(String methodAId, String methodBId) {
    }

    private record ScoredPair(MethodPairFeature feature, double matchScore, MatchReason reason) {
    }

    private static class DirectionAccumulator {
        boolean forward;
        boolean backward;
    }

    private record CoverageResult(
            double coverageA,
            double coverageB,
            double confirmedCoverageA,
            double confirmedCoverageB,
            Map<String, Double> coverageByA,
            Map<String, Double> coverageByB
    ) {
    }

    private record Weighting(double totalWeight, boolean useEqualWeights) {
    }
}
