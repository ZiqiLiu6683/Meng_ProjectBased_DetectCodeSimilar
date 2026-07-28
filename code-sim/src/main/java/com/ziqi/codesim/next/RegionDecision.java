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
        List<String> decisionPath,
        /**
         * The same cascade applied per aligned statement pair, coalesced into uniformly-typed runs.
         *
         * {@code type} above stays the region's verdict and remains the authoritative one; this is
         * a finer view of the same evidence, never a second opinion. Anything counting clones must
         * pick one scale -- a T3 region containing T3 sub-regions is one finding, not several.
         * Empty when the region carries no statement positions to attribute types to.
         */
        List<SubRegion> subRegions
) {
    public RegionDecision {
        subRegions = subRegions == null ? List.of() : List.copyOf(subRegions);
    }
}
