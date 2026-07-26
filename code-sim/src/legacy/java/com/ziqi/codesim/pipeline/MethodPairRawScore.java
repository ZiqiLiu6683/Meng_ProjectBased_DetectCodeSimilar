package com.ziqi.codesim.pipeline;

public record MethodPairRawScore(
        String methodAId,
        String methodBId,
        int sizeA,
        int sizeB,
        double s2,
        double s3,
        double s4,
        SignalStatus s4Status,
        int tedDistance,
        int s3IntersectionCount,
        int s3CountA,
        int s3CountB
) {
}
