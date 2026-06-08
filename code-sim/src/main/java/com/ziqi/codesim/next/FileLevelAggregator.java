package com.ziqi.codesim.next;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileLevelAggregator {
    private static final double FULL_COVERAGE_THRESHOLD = 0.80;
    private static final double PARTIAL_COVERAGE_THRESHOLD = 0.20;

    public FileCloneSummary aggregate(EvidencePackage evidencePackage,
                                      List<RegionDecision> decisions) {
        CodeRegion leftFile = fileRegion(evidencePackage.leftRegions());
        CodeRegion rightFile = fileRegion(evidencePackage.rightRegions());
        List<RegionDecision> cloneDecisions = decisions.stream()
                .filter(FileLevelAggregator::isAcceptedClone)
                .toList();

        Map<CloneRegionType, Integer> typeCounts = initCounts();
        Map<CloneRegionType, Set<Integer>> coveredLeftLinesByType = initLineSets();
        Map<CloneRegionType, Set<Integer>> coveredRightLinesByType = initLineSets();
        Set<RegionTag> fileTags = EnumSet.noneOf(RegionTag.class);
        Set<Integer> coveredLeftLines = new HashSet<>();
        Set<Integer> coveredRightLines = new HashSet<>();

        for (RegionDecision decision : cloneDecisions) {
            typeCounts.merge(decision.type(), 1, Integer::sum);
            fileTags.addAll(decision.tags());
            addLines(coveredLeftLines, decision.candidate().left());
            addLines(coveredRightLines, decision.candidate().right());
            addLines(coveredLeftLinesByType.get(decision.type()), decision.candidate().left());
            addLines(coveredRightLinesByType.get(decision.type()), decision.candidate().right());
        }

        double matchedCoverageLeft = lineCoverage(coveredLeftLines, leftFile);
        double matchedCoverageRight = lineCoverage(coveredRightLines, rightFile);
        Map<CloneRegionType, Double> typeCoverage = typeCoverage(
                coveredLeftLinesByType,
                coveredRightLinesByType,
                leftFile,
                rightFile
        );
        double unrelatedCodeRatio = 1.0 - Math.min(matchedCoverageLeft, matchedCoverageRight);
        CloneRegionType dominantType = dominantType(typeCoverage);
        FileRelationship relationship = relationship(
                dominantType,
                typeCounts,
                matchedCoverageLeft,
                matchedCoverageRight
        );
        RelationshipShape relationshipShape = relationshipShape(
                cloneDecisions,
                matchedCoverageLeft,
                matchedCoverageRight
        );
        InspectionPriority inspectionPriority = inspectionPriority(
                relationshipShape,
                cloneDecisions,
                matchedCoverageLeft,
                matchedCoverageRight
        );
        List<EvidenceBreakdown> evidenceBreakdown = evidenceBreakdown(
                typeCounts,
                coveredLeftLinesByType,
                coveredRightLinesByType,
                leftFile,
                rightFile
        );

        return new FileCloneSummary(
                relationship,
                dominantType,
                relationshipShape,
                inspectionPriority,
                matchedCoverageLeft,
                matchedCoverageRight,
                unrelatedCodeRatio,
                List.copyOf(evidenceBreakdown),
                Map.copyOf(typeCounts),
                Map.copyOf(typeCoverage),
                Set.copyOf(fileTags)
        );
    }

    private static boolean isAcceptedClone(RegionDecision decision) {
        return decision.type() != CloneRegionType.NON_CLONE;
    }

    private static FileRelationship relationship(CloneRegionType dominantType,
                                                 Map<CloneRegionType, Integer> typeCounts,
                                                 double coverageLeft,
                                                 double coverageRight) {
        int acceptedTypeCount = acceptedTypeCount(typeCounts);
        double minCoverage = Math.min(coverageLeft, coverageRight);
        if (dominantType == CloneRegionType.NON_CLONE || acceptedTypeCount == 0) {
            return FileRelationship.NON_CLONE;
        }
        if (acceptedTypeCount > 1) {
            return FileRelationship.MIXED_CLONE_TYPES;
        }
        if (dominantType == CloneRegionType.T4_CONFIRMED
                || dominantType == CloneRegionType.POSSIBLE_T4_CANDIDATE) {
            return FileRelationship.POSSIBLE_SEMANTIC_RELATION;
        }
        if (minCoverage >= FULL_COVERAGE_THRESHOLD) {
            return switch (dominantType) {
                case T1 -> FileRelationship.FULL_FILE_T1;
                case T2 -> FileRelationship.FULL_FILE_T2;
                case T3 -> FileRelationship.FULL_FILE_T3;
                default -> FileRelationship.MIXED_CLONE_TYPES;
            };
        }
        if (minCoverage >= PARTIAL_COVERAGE_THRESHOLD) {
            return switch (dominantType) {
                case T2 -> FileRelationship.PARTIAL_T2;
                case T3 -> FileRelationship.PARTIAL_T3;
                case T1 -> FileRelationship.PARTIAL_T2;
                default -> FileRelationship.POSSIBLE_SEMANTIC_RELATION;
            };
        }
        return FileRelationship.MOSTLY_NON_CLONE;
    }

    private static RelationshipShape relationshipShape(List<RegionDecision> cloneDecisions,
                                                       double coverageLeft,
                                                       double coverageRight) {
        if (cloneDecisions.isEmpty() || Math.max(coverageLeft, coverageRight) < PARTIAL_COVERAGE_THRESHOLD) {
            return RelationshipShape.NO_SIGNIFICANT_OVERLAP;
        }
        boolean hasCompleteUnit = cloneDecisions.stream()
                .anyMatch(FileLevelAggregator::hasCompleteComparableUnit);
        if (!hasCompleteUnit) {
            return RelationshipShape.LOCAL_SIMILARITIES_ONLY;
        }
        if (coverageLeft >= FULL_COVERAGE_THRESHOLD && coverageRight >= FULL_COVERAGE_THRESHOLD) {
            return RelationshipShape.FULL_OVERLAP;
        }
        if (coverageLeft >= FULL_COVERAGE_THRESHOLD && coverageRight >= PARTIAL_COVERAGE_THRESHOLD) {
            return RelationshipShape.LEFT_EMBEDDED_IN_RIGHT;
        }
        if (coverageRight >= FULL_COVERAGE_THRESHOLD && coverageLeft >= PARTIAL_COVERAGE_THRESHOLD) {
            return RelationshipShape.RIGHT_EMBEDDED_IN_LEFT;
        }
        if (coverageLeft >= PARTIAL_COVERAGE_THRESHOLD && coverageRight >= PARTIAL_COVERAGE_THRESHOLD) {
            return RelationshipShape.PARTIAL_OVERLAP;
        }
        return RelationshipShape.LOCAL_SIMILARITIES_ONLY;
    }

    private static InspectionPriority inspectionPriority(RelationshipShape relationshipShape,
                                                         List<RegionDecision> cloneDecisions,
                                                         double coverageLeft,
                                                         double coverageRight) {
        if (relationshipShape == RelationshipShape.NO_SIGNIFICANT_OVERLAP || cloneDecisions.isEmpty()) {
            return InspectionPriority.NONE;
        }
        if (relationshipShape == RelationshipShape.LOCAL_SIMILARITIES_ONLY) {
            return Math.max(coverageLeft, coverageRight) >= FULL_COVERAGE_THRESHOLD
                    ? InspectionPriority.MEDIUM
                    : InspectionPriority.LOW;
        }
        double minCoverage = Math.min(coverageLeft, coverageRight);
        if (relationshipShape == RelationshipShape.FULL_OVERLAP && minCoverage >= FULL_COVERAGE_THRESHOLD) {
            return InspectionPriority.HIGH;
        }
        if (relationshipShape == RelationshipShape.LEFT_EMBEDDED_IN_RIGHT
                || relationshipShape == RelationshipShape.RIGHT_EMBEDDED_IN_LEFT) {
            return InspectionPriority.HIGH;
        }
        if (minCoverage >= PARTIAL_COVERAGE_THRESHOLD) {
            return InspectionPriority.MEDIUM;
        }
        return InspectionPriority.LOW;
    }

    private static boolean hasCompleteComparableUnit(RegionDecision decision) {
        return isCompleteComparableUnit(decision.candidate().left().kind())
                || isCompleteComparableUnit(decision.candidate().right().kind());
    }

    private static boolean isCompleteComparableUnit(RegionKind kind) {
        return kind == RegionKind.FILE
                || kind == RegionKind.METHOD
                || kind == RegionKind.METHOD_BODY_REGION
                || kind == RegionKind.CALL_EXPANDED_REGION;
    }

    private static int acceptedTypeCount(Map<CloneRegionType, Integer> typeCounts) {
        int count = 0;
        for (Map.Entry<CloneRegionType, Integer> entry : typeCounts.entrySet()) {
            if (entry.getKey() != CloneRegionType.NON_CLONE && entry.getValue() > 0) {
                count++;
            }
        }
        return count;
    }

    private static CloneRegionType dominantType(Map<CloneRegionType, Double> typeCoverage) {
        CloneRegionType bestType = CloneRegionType.NON_CLONE;
        double bestCoverage = 0.0;
        for (Map.Entry<CloneRegionType, Double> entry : typeCoverage.entrySet()) {
            if (entry.getKey() == CloneRegionType.NON_CLONE) {
                continue;
            }
            if (entry.getValue() > bestCoverage) {
                bestCoverage = entry.getValue();
                bestType = entry.getKey();
            }
        }
        return bestType;
    }

    private static Map<CloneRegionType, Integer> initCounts() {
        Map<CloneRegionType, Integer> counts = new EnumMap<>(CloneRegionType.class);
        for (CloneRegionType type : CloneRegionType.values()) {
            counts.put(type, 0);
        }
        return counts;
    }

    private static Map<CloneRegionType, Double> initCoverage() {
        Map<CloneRegionType, Double> coverage = new EnumMap<>(CloneRegionType.class);
        for (CloneRegionType type : CloneRegionType.values()) {
            coverage.put(type, 0.0);
        }
        return coverage;
    }

    private static Map<CloneRegionType, Set<Integer>> initLineSets() {
        Map<CloneRegionType, Set<Integer>> lines = new EnumMap<>(CloneRegionType.class);
        for (CloneRegionType type : CloneRegionType.values()) {
            lines.put(type, new HashSet<>());
        }
        return lines;
    }

    private static Map<CloneRegionType, Double> typeCoverage(
            Map<CloneRegionType, Set<Integer>> coveredLeftLinesByType,
            Map<CloneRegionType, Set<Integer>> coveredRightLinesByType,
            CodeRegion leftFile,
            CodeRegion rightFile) {
        Map<CloneRegionType, Double> coverage = initCoverage();
        for (CloneRegionType type : CloneRegionType.values()) {
            double left = lineCoverage(coveredLeftLinesByType.get(type), leftFile);
            double right = lineCoverage(coveredRightLinesByType.get(type), rightFile);
            coverage.put(type, Math.max(left, right));
        }
        return coverage;
    }

    private static List<EvidenceBreakdown> evidenceBreakdown(
            Map<CloneRegionType, Integer> typeCounts,
            Map<CloneRegionType, Set<Integer>> coveredLeftLinesByType,
            Map<CloneRegionType, Set<Integer>> coveredRightLinesByType,
            CodeRegion leftFile,
            CodeRegion rightFile) {
        return typeCounts.entrySet().stream()
                .filter(entry -> entry.getKey() != CloneRegionType.NON_CLONE)
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new EvidenceBreakdown(
                        entry.getKey(),
                        entry.getValue(),
                        lineCoverage(coveredLeftLinesByType.get(entry.getKey()), leftFile),
                        lineCoverage(coveredRightLinesByType.get(entry.getKey()), rightFile)
                ))
                .sorted(Comparator
                        .comparingDouble((EvidenceBreakdown breakdown) ->
                                Math.max(breakdown.affectedLeftRatio(), breakdown.affectedRightRatio()))
                        .reversed()
                        .thenComparing(breakdown -> breakdown.type().name()))
                .toList();
    }

    private static void addLines(Set<Integer> covered, CodeRegion region) {
        if (region.beginLine() < 0 || region.endLine() < region.beginLine()) {
            return;
        }
        for (int line = region.beginLine(); line <= region.endLine(); line++) {
            covered.add(line);
        }
    }

    private static double lineCoverage(Set<Integer> coveredLines, CodeRegion fileRegion) {
        double span = regionLineSpan(fileRegion);
        if (span <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, coveredLines.size() / span);
    }

    private static double regionLineSpan(CodeRegion region) {
        if (region.beginLine() < 0 || region.endLine() < region.beginLine()) {
            return Math.max(1, region.tokenCount());
        }
        return region.endLine() - region.beginLine() + 1.0;
    }

    private static CodeRegion fileRegion(List<CodeRegion> regions) {
        return regions.stream()
                .filter(region -> region.kind() == RegionKind.FILE)
                .findFirst()
                .orElseThrow();
    }
}
