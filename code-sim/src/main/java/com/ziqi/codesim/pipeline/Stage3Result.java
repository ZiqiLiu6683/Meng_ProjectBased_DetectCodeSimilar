package com.ziqi.codesim.pipeline;

import java.util.List;
import java.util.Set;

public record Stage3Result(
        SignalStatus status,
        double magnitudeAvg,
        double matchScoreAvg,
        double centroidS3,
        double centroidS4,
        double dominantTokenStructureDivergence,
        double spreadAvg,
        double structuralExactnessAvg,
        double tokenExactGapAvg,
        double varianceS3,
        double varianceS4,
        double coverageA,
        double coverageB,
        double confirmedCoverageA,
        double confirmedCoverageB,
        double confirmedRatio,
        double partialCloneSignal,
        double partialAInB,
        double partialBInA,
        List<MergedPairFeature> mergedPairs,
        List<MethodCoverage> leastCoveredA,
        List<MethodCoverage> leastCoveredB,
        double s1,
        SignalStatus s5Status,
        double s5,
        Set<Stage3Flag> flags
) {
}
