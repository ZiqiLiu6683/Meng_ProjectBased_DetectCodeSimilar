package com.ziqi.codesim.region.grow;

/**
 * One aligned node correspondence inside a {@link RegionGroup}: a left-graph node matched to a
 * right-graph node during seed-and-extend growth.
 *
 * @param leftNodeId  node id in the left graph
 * @param rightNodeId node id in the right graph
 * @param agreement   per-pair match quality in [0,1]: 1.0 if the two nodes share a semantic value
 *                    hash, 0.8 if they share a WL label, 0.5 if only their operation token matches
 */
public record AlignedPair(int leftNodeId, int rightNodeId, double agreement) {
}
