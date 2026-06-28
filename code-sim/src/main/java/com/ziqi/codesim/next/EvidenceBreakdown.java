package com.ziqi.codesim.next;

public record EvidenceBreakdown(
        CloneRegionType type,
        int regionCount,
        // Coverage by this type. Regions of different types can overlap (e.g. a T1
        // exact-copy region nested inside a T3 method), so these ratios may sum to
        // more than the file's total affected ratio.
        double affectedLeftRatio,
        double affectedRightRatio,
        // Mutually-exclusive partition: each affected line is attributed to its single
        // strongest type (T1 > T2 > T3 > ...). These ratios do NOT overlap, so summing
        // them over all types equals the file's total affected ratio.
        double exclusiveAffectedLeftRatio,
        double exclusiveAffectedRightRatio
) {
}
