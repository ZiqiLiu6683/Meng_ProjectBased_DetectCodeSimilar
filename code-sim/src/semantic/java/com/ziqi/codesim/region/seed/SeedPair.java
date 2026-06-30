package com.ziqi.codesim.region.seed;

import java.util.Set;

/**
 * A candidate matched node pair across two files: an anchor the region grower (M4) expands from.
 * Always cross-file (left id belongs to the left graph, right id to the right graph) by construction
 * of {@link SeedMatcher}.
 *
 * @param leftNodeId  node id in the left graph
 * @param rightNodeId node id in the right graph
 * @param strength    ranking score in [0,1]; higher = stronger anchor (exact > approximate). NOT an
 *                    accept/reject threshold — M4 simply grows strongest-first
 * @param evidence    which signals fired, e.g. {@code SEM}, {@code WL@3}, {@code KNN:0.82}
 */
public record SeedPair(int leftNodeId, int rightNodeId, double strength, Set<String> evidence) {

    public SeedPair {
        evidence = Set.copyOf(evidence);
    }
}
