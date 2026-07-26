package com.ziqi.codesim.pipeline;

import java.util.List;

public record EvidenceChain(
        List<EvidenceItem> supportingEvidence,
        List<EvidenceItem> opposingEvidence,
        List<EvidenceItem> scopeEvidence,
        List<EvidenceItem> reliabilityWarnings
) {
}
