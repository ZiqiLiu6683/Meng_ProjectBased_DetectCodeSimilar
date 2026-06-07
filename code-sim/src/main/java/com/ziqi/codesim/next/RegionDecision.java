package com.ziqi.codesim.next;

import java.util.List;
import java.util.Set;

public record RegionDecision(
        RegionCandidate candidate,
        CloneRegionType type,
        CloneStrength strength,
        double syntacticSimilarity,
        RenameEvidence renameEvidence,
        StatementEditScript statementEditScript,
        Set<RegionTag> tags,
        List<String> decisionPath
) {
}
