package com.ziqi.codesim.semantic.knn;

import java.util.List;

public record BlockCandidate(
        String queryBlockId,
        String candidateBlockId,
        KnnFeatureView view,
        double distance,
        List<FeatureContribution> sharedContributions
) {
    public BlockCandidate {
        sharedContributions = List.copyOf(sharedContributions);
    }
}
