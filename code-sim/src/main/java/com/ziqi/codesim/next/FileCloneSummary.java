package com.ziqi.codesim.next;

import java.util.Map;
import java.util.List;
import java.util.Set;

public record FileCloneSummary(
        FileRelationship overallRelationship,
        CloneRegionType dominantRegionType,
        RelationshipShape relationshipShape,
        InspectionPriority inspectionPriority,
        double matchedCoverageLeft,
        double matchedCoverageRight,
        double unrelatedCodeRatio,
        List<EvidenceBreakdown> evidenceBreakdown,
        Map<CloneRegionType, Integer> regionTypeCounts,
        Map<CloneRegionType, Double> regionTypeCoverage,
        Set<RegionTag> fileTags
) {
}
