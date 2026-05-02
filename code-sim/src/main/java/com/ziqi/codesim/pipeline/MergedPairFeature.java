package com.ziqi.codesim.pipeline;

public record MergedPairFeature(
        String methodAId,
        String methodBId,
        MethodPairFeature feature,
        double matchScore,
        MatchReason matchReason,
        MatchDirection direction,
        boolean confirmed,
        double generalWeight
) {
}
