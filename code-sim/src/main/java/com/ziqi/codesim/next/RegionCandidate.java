package com.ziqi.codesim.next;

import java.util.List;

public record RegionCandidate(
        String candidateId,
        CodeRegion left,
        CodeRegion right,
        List<CandidateSource> sources
) {
}
