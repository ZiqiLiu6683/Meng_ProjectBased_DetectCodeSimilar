package com.ziqi.codesim.pipeline;

import java.util.Set;

public record MethodPairFeature(
        String methodAId,
        String methodBId,
        int sizeA,
        int sizeB,
        double s2,
        double s3,
        double s4,
        SignalStatus s4Status,
        int tedDistance,
        double magnitude,
        double tokenStructureDivergence,
        double structuralExactness,
        double tokenExactGap,
        double spread,
        double sizeRatio,
        double containmentAInB,
        double containmentBInA,
        Set<PairFlag> flags
) {
}
