package com.ziqi.codesim.next;

import java.util.List;

public record RankedRegionCandidate(
        RegionCandidate candidate,
        double rankScore,
        List<String> rankingReasons
) {
}
