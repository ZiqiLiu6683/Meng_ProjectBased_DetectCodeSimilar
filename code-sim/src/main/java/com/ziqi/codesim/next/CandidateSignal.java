package com.ziqi.codesim.next;

public record CandidateSignal(
        String leftRegionId,
        String rightRegionId,
        String channel,
        double score
) {
}
