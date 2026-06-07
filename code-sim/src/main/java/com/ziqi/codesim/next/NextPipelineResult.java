package com.ziqi.codesim.next;

import java.util.List;

public record NextPipelineResult(
        EvidencePackage evidencePackage,
        List<RegionCandidate> candidates,
        List<RegionDecision> regionDecisions
) {
}
