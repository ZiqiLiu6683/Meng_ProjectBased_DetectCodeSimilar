package com.ziqi.codesim.pipeline;

import java.util.List;

public record Stage4Result(
        CloneType cloneType,
        ScopeType scopeType,
        ContainmentDirection containmentDirection,
        double confidence,
        ConfidenceLevel confidenceLevel,
        double scopeConfidence,
        ConfidenceLevel scopeConfidenceLevel,
        double evidenceStrength,
        double evidenceConsistency,
        double pipelineReliability,
        TypeScores typeScores,
        ScopeScores scopeScores,
        EvidenceChain evidenceChain,
        List<String> warnings
) {
}
