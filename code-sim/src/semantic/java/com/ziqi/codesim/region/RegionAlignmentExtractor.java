package com.ziqi.codesim.region;

import com.ziqi.codesim.region.grow.RegionGroup;
import com.ziqi.codesim.region.model.SemanticGraph;
import com.ziqi.codesim.region.model.SemanticNode;

import java.util.HashSet;
import java.util.Set;

/**
 * Turns a Phase A {@link RegionGroup} into the plain line-level data the source-side reconstructor
 * needs, without exposing WALA/graph types across the boundary: the source lines each side's spanned
 * methods cover, plus whether the region crosses method boundaries. The reconstructor
 * ({@code NextEvidenceExtractor.reconstructAlignedRegion}) then rebuilds two source regions and
 * hands them to the recognizer.
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
        Set<Integer> leftSpan = spanLines(leftGraph, region.leftMethods());
        Set<Integer> rightSpan = spanLines(rightGraph, region.rightMethods());
        return new Input(leftSpan, rightSpan, region.isCrossMethod());
    }

    /** All source lines covered by the given methods' nodes (bounds which statements to include). */
    private static Set<Integer> spanLines(SemanticGraph graph, Set<String> methods) {
        Set<Integer> lines = new HashSet<>();
        for (String method : methods) {
            for (SemanticNode node : graph.nodesInMethod(method)) {
                if (node.hasSource()) {
                    for (int line = node.source().beginLine(); line <= node.source().endLine(); line++) {
                        lines.add(line);
                    }
                }
            }
        }
        return lines;
    }
}
