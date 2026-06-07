package com.ziqi.codesim.next;

public record RegionSelectionSummary(
        int acceptedRegionCount,
        int selectedRegionCount,
        int suppressedRegionCount
) {
}
