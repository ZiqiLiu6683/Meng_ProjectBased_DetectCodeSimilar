package com.ziqi.codesim.region.grow;

import com.ziqi.codesim.region.model.SourceSpan;

import java.util.List;
import java.util.Set;

/**
 * A boundary-free, cross-method clone region group: the maximal aligned subgraph grown from a seed.
 * It is a candidate, not a verdict — Phase B scores it and (later) the type recognizer judges it.
 *
 * @param alignment   aligned node correspondences (left node ↔ right node) forming this region
 * @param strength    match quality in [0,1]: mean per-pair agreement
 * @param coverage    substance-weighted size: how much real computation (arithmetic, calls,
 *                    branches, field/array ops) the region covers, vs boilerplate/plumbing. Lets a
 *                    real clone outrank a trivial constructor/getter match of equal agreement
 * @param leftSpan    source line range covered on the left file (union over aligned left nodes)
 * @param rightSpan   source line range covered on the right file
 * @param leftMethods declaring-method signatures the left side spans (>1 means it crosses methods)
 * @param rightMethods declaring-method signatures the right side spans
 */
public record RegionGroup(
        List<AlignedPair> alignment,
        double strength,
        double coverage,
        SourceSpan leftSpan,
        SourceSpan rightSpan,
        Set<String> leftMethods,
        Set<String> rightMethods
) {
    public RegionGroup {
        alignment = List.copyOf(alignment);
        leftMethods = Set.copyOf(leftMethods);
        rightMethods = Set.copyOf(rightMethods);
    }

    /**
     * Ranking score: substantive coverage scaled mildly by match quality. Coverage dominates so a
     * large real clone ranks above a small clean boilerplate match; agreement only modulates (with a
     * 0.5 floor) so a correct cross-method clone is not buried just because its across-call nodes
     * agree less.
     */
    public double priority() {
        return coverage * (0.5 + 0.5 * strength);
    }

    public int size() {
        return alignment.size();
    }

    /** True when either side spans more than one method: the boundary-free / helper-extraction case. */
    public boolean isCrossMethod() {
        return leftMethods.size() > 1 || rightMethods.size() > 1;
    }
}
