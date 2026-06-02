package com.ziqi.codesim.semantic.discovre;

import java.util.List;

public record DiscovreCfgMatchResult(
        String leftMethodId,
        String rightMethodId,
        double distance,
        double similarity,
        int matchedBlockCount,
        int candidatePairCount,
        int iterationCount,
        int iterationBudget,
        List<DiscovreBlockPair> matchedPairs
) {
    public DiscovreCfgMatchResult {
        matchedPairs = List.copyOf(matchedPairs);
    }
}
