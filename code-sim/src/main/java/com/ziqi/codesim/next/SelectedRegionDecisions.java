package com.ziqi.codesim.next;

import java.util.List;

public record SelectedRegionDecisions(
        List<RegionDecision> selectedDecisions,
        RegionSelectionSummary summary
) {
}
