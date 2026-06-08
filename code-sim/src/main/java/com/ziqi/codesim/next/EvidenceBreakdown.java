package com.ziqi.codesim.next;

public record EvidenceBreakdown(
        CloneRegionType type,
        int regionCount,
        double affectedLeftRatio,
        double affectedRightRatio
) {
}
