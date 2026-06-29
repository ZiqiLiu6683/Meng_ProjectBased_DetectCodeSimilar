package com.ziqi.codesim.next;

import java.util.List;
import java.util.Set;

public record RegionDecision(
        RegionCandidate candidate,
        CloneRegionType type,
        CloneStrength strength,
        double syntacticSimilarity,
        // Raw method-CFG structural similarity (approximate MCS); NaN when no method-level
        // structural evidence applies. Reported as-is for the user to weigh; never used to
        // change the type.
        double structuralSimilarity,
        RenameEvidence renameEvidence,
        StatementEditScript statementEditScript,
        Set<RegionTag> tags,
        List<String> decisionPath
) {
}
