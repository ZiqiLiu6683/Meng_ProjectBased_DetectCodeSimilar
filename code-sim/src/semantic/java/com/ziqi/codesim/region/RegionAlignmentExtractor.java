package com.ziqi.codesim.region;

import com.ziqi.codesim.region.grow.AlignedPair;
import com.ziqi.codesim.region.grow.RegionGroup;
import com.ziqi.codesim.region.model.SemanticGraph;
import com.ziqi.codesim.region.model.SemanticNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Turns a Phase A {@link RegionGroup} into the plain line-level data the source-side reconstructor
 * needs, without exposing WALA/graph types across the boundary: the source lines the REGION actually
 * covers on each side, plus whether the region crosses method boundaries. The reconstructor
 * ({@code NextEvidenceExtractor.reconstructAlignedRegion}) then rebuilds two source regions and
 * hands them to the recognizer.
 *
 * <p>Region-vs-region, not method-vs-method: the span is the aligned EXTENT of the region on each
 * side -- per method, the line range from its first to its last aligned node -- NOT the whole
 * method. So a fragment clone inside a big method is compared as a fragment (unrelated code before or
 * after the aligned extent is excluded), while a statement inserted BETWEEN aligned statements stays
 * inside the extent and is still seen as Type-3 edit evidence. For a whole-method clone the extent
 * is the whole method, so nothing changes there.
 *
 * <p>Line granularity is the honest ceiling here: node source spans are line-based, so two source
 * statements on the SAME line cannot be told apart (they collapse to one line). Real code is
 * overwhelmingly one statement per line, where this is exact; matching is by statement text anyway.
 */
public final class RegionAlignmentExtractor {

    /** Plain line data extracted from a region group; consumed by the source-side reconstructor. */
    public record Input(Set<Integer> leftSpanLines, Set<Integer> rightSpanLines, boolean crossMethod) {
    }

    public Input extract(RegionGroup region, SemanticGraph leftGraph, SemanticGraph rightGraph) {
        Set<Integer> leftSpan = regionExtentLines(region, leftGraph, true);
        Set<Integer> rightSpan = regionExtentLines(region, rightGraph, false);
        return new Input(leftSpan, rightSpan, region.isCrossMethod());
    }

    /**
     * The lines of the region's aligned EXTENT on one side: for each method the region touches, the
     * range from its earliest to its latest aligned node line (inclusive), unioned across methods.
     * This scopes the comparison to the region, while filling the gaps between aligned statements so
     * inserts/deletes within the extent remain visible.
     */
    private static Set<Integer> regionExtentLines(RegionGroup region, SemanticGraph graph, boolean leftSide) {
        Map<String, int[]> extentByMethod = new HashMap<>();
        for (AlignedPair pair : region.alignment()) {
            int nodeId = leftSide ? pair.leftNodeId() : pair.rightNodeId();
            SemanticNode node = graph.node(nodeId).orElse(null);
            if (node == null || !node.hasSource()) {
                continue;
            }
            int begin = node.source().beginLine();
            int end = node.source().endLine();
            extentByMethod.merge(node.methodSignature(), new int[]{begin, end}, (cur, add) -> {
                cur[0] = Math.min(cur[0], add[0]);
                cur[1] = Math.max(cur[1], add[1]);
                return cur;
            });
        }
        Set<Integer> lines = new HashSet<>();
        for (int[] extent : extentByMethod.values()) {
            for (int line = extent[0]; line <= extent[1]; line++) {
                lines.add(line);
            }
        }
        return lines;
    }
}
