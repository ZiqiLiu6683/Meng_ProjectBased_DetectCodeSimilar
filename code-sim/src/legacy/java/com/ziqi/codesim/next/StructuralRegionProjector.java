package com.ziqi.codesim.next;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Projects Phase A structural region groups back into classifiable source regions. Each region group
 * gives the set of source lines it covers on each side; this builds a left/right {@link CodeRegion}
 * from those lines (real source tokens/statements) and pairs them as a {@link RegionCandidate}, so a
 * Phase A region flows through the full T1-T4 recognizer -- Phase A SELECTS the region, the
 * recognizer CLASSIFIES it. The region's structural coverage rides along as a
 * {@code STRUCTURAL_REGION_SCAN} source so the recognizer can flag a possible Type-4 when the
 * projected sides are syntactically different (e.g. cross-method helper extraction).
 */
public final class StructuralRegionProjector {

    /** Source lines a region group covers on each side, plus its substance-weighted coverage. */
    public record RegionLineSpec(Set<Integer> leftLines, Set<Integer> rightLines, double coverage) {
        public RegionLineSpec {
            leftLines = Set.copyOf(leftLines);
            rightLines = Set.copyOf(rightLines);
        }
    }

    public List<RegionCandidate> project(String leftSource, String rightSource, List<RegionLineSpec> specs) {
        List<RegionCandidate> candidates = new ArrayList<>();
        int index = 1;
        for (RegionLineSpec spec : specs) {
            if (spec.leftLines().isEmpty() || spec.rightLines().isEmpty()) {
                continue;
            }
            CodeRegion left = NextEvidenceExtractor.regionForLines(
                    leftSource, spec.leftLines(), RegionSide.LEFT, RegionKind.CALL_EXPANDED_REGION,
                    "sregL" + index, "structural region " + index);
            CodeRegion right = NextEvidenceExtractor.regionForLines(
                    rightSource, spec.rightLines(), RegionSide.RIGHT, RegionKind.CALL_EXPANDED_REGION,
                    "sregR" + index, "structural region " + index);
            if (left.rawTokens().isEmpty() || right.rawTokens().isEmpty()) {
                continue;
            }
            candidates.add(new RegionCandidate(
                    "SR" + index,
                    left,
                    right,
                    List.of(new CandidateSource("STRUCTURAL_REGION_SCAN", spec.coverage()))));
            index++;
        }
        return candidates;
    }
}
