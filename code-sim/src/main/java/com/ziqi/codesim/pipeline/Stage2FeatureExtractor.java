package com.ziqi.codesim.pipeline;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class Stage2FeatureExtractor {
    private static final double EPS = 1e-9;

    public Stage2Result compute(Stage1Result stage1) {
        if (stage1.methodsA().isEmpty() || stage1.methodsB().isEmpty()) {
            return new Stage2Result(SignalStatus.NOT_APPLICABLE, List.of());
        }
        List<MethodPairFeature> features = stage1.pairMatrix().stream()
                .map(this::computeFeature)
                .toList();
        return new Stage2Result(SignalStatus.COMPUTED, features);
    }

    public MethodPairFeature computeFeature(MethodPairRawScore raw) {
        double magnitude = Math.sqrt(raw.s3() * raw.s3() + raw.s4() * raw.s4())
                / Math.sqrt(2.0);
        double tokenStructureDivergence = (magnitude < EPS)
                ? 0.0
                : clamp((raw.s3() - raw.s4()) / (magnitude * Math.sqrt(2.0)), -1.0, 1.0);
        double structuralExactness = clamp((raw.s2() + EPS) / (raw.s4() + EPS), 0.0, 1.0);
        double tokenExactGap = clamp((raw.s3() - raw.s2()) / (raw.s3() + EPS), 0.0, 1.0);
        double spread = Math.max(raw.s2(), Math.max(raw.s3(), raw.s4()))
                - Math.min(raw.s2(), Math.min(raw.s3(), raw.s4()));
        double sizeRatio = sizeRatio(raw.sizeA(), raw.sizeB());

        Set<PairFlag> flags = EnumSet.noneOf(PairFlag.class);
        if (raw.s4Status() != SignalStatus.COMPUTED) {
            flags.add(PairFlag.INCOMPLETE_S4);
        }

        double containmentAInB = containment(raw.s3IntersectionCount(), raw.s3CountA(), flags);
        double containmentBInA = containment(raw.s3IntersectionCount(), raw.s3CountB(), flags);

        return new MethodPairFeature(
                raw.methodAId(),
                raw.methodBId(),
                raw.sizeA(),
                raw.sizeB(),
                raw.s2(),
                raw.s3(),
                raw.s4(),
                raw.s4Status(),
                raw.tedDistance(),
                magnitude,
                tokenStructureDivergence,
                structuralExactness,
                tokenExactGap,
                spread,
                sizeRatio,
                containmentAInB,
                containmentBInA,
                Set.copyOf(flags)
        );
    }

    private static double containment(int intersection, int count, Set<PairFlag> flags) {
        if (count == 0) {
            flags.add(PairFlag.LOW_TOKEN_EVIDENCE);
            return 0.0;
        }
        return clamp((double) intersection / count, 0.0, 1.0);
    }

    private static double sizeRatio(int sizeA, int sizeB) {
        int max = Math.max(sizeA, sizeB);
        if (max == 0) return 0.0;
        return (double) Math.min(sizeA, sizeB) / max;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
