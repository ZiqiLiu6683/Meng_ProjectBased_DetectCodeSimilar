package com.ziqi.codesim.next;

import java.util.Map;
import java.util.Set;

public record FileCloneSummary(
        FileRelationship overallRelationship,
        CloneRegionType dominantRegionType,
        double matchedCoverageLeft,
        double matchedCoverageRight,
        double unrelatedCodeRatio,
        Map<CloneRegionType, Integer> regionTypeCounts,
        Map<CloneRegionType, Double> regionTypeCoverage,
        Set<RegionTag> fileTags
) {
}
