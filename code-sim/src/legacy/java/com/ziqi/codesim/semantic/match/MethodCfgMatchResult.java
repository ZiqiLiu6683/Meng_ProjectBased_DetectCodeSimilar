package com.ziqi.codesim.semantic.match;

import java.util.List;

public record MethodCfgMatchResult(
        String leftMethodId,
        String rightMethodId,
        double methodFeatureDistance,
        double blockSimilarity,
        double edgePreservation,
        double overallSimilarity,
        List<BlockMatch> blockMatches
) {
    public MethodCfgMatchResult {
        blockMatches = List.copyOf(blockMatches);
    }
}
