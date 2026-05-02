package com.ziqi.codesim.pipeline;

public record EvidenceItem(
        EvidenceCategory category,
        String signal,
        String value,
        String interpretation,
        String supports,
        ConfidenceLevel strength
) {
}
