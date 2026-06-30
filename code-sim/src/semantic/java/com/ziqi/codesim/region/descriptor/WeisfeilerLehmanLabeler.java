package com.ziqi.codesim.region.descriptor;

import com.ziqi.codesim.region.model.SemanticEdge;
import com.ziqi.codesim.region.model.SemanticGraph;
import com.ziqi.codesim.region.model.SemanticNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Weisfeiler-Lehman relabeling over a {@link SemanticGraph}. Each round folds a node's typed,
 * directed dependence neighborhood into its label, so after r rounds a node's hash encodes its
 * r-hop SDG context. Because the graph is the SDG (edges are control AND data dependences), this is
 * "semantic-aware structure", not pure control-flow shape — which is the whole point of running WL
 * on the SDG rather than on a plain CFG.
 *
 * <p>Pure Java, deterministic (FNV-1a + a fixed mixing function), no WALA. Variable names never
 * enter the labels: the base label is the node's name-free {@code operationToken}.
 */
public final class WeisfeilerLehmanLabeler {

    private final int iterations;

    public WeisfeilerLehmanLabeler() {
        this(3);
    }

    /** @param iterations number of WL rounds (max neighborhood radius folded into the final label) */
    public WeisfeilerLehmanLabeler(int iterations) {
        if (iterations < 0) {
            throw new IllegalArgumentException("iterations must be >= 0: " + iterations);
        }
        this.iterations = iterations;
    }

    /** Name-free base label (radius 0): the node's operation token plus its coarse kind. */
    public static String baseLabel(SemanticNode node) {
        return node.operationToken() + "#" + node.kind();
    }

    /**
     * @return per-node list of WL hashes; index 0 is the radius-0 label, index r is the label after
     *         r rounds. Keyed by node id.
     */
    public Map<Integer, List<Long>> label(SemanticGraph graph) {
        Map<Integer, Long> current = new HashMap<>();
        Map<Integer, List<Long>> history = new HashMap<>();
        for (SemanticNode node : graph.nodes()) {
            long base = fnv1a(baseLabel(node));
            current.put(node.id(), base);
            List<Long> hist = new ArrayList<>(iterations + 1);
            hist.add(base);
            history.put(node.id(), hist);
        }

        for (int round = 1; round <= iterations; round++) {
            Map<Integer, Long> next = new HashMap<>();
            for (SemanticNode node : graph.nodes()) {
                int id = node.id();
                List<Long> neighborhood = new ArrayList<>();
                for (SemanticEdge edge : graph.outgoing(id)) {
                    neighborhood.add(edgeContribution(current.get(edge.toId()), edge, false));
                }
                for (SemanticEdge edge : graph.incoming(id)) {
                    neighborhood.add(edgeContribution(current.get(edge.fromId()), edge, true));
                }
                // Sort so the label is invariant to neighbor ordering (a multiset, not a sequence).
                Collections.sort(neighborhood);
                long combined = current.get(id);
                for (long contribution : neighborhood) {
                    combined = mix(combined, contribution);
                }
                next.put(id, combined);
            }
            current = next;
            for (Map.Entry<Integer, Long> entry : current.entrySet()) {
                history.get(entry.getKey()).add(entry.getValue());
            }
        }
        return history;
    }

    /** Fold a neighbor's current label together with the edge kind and direction. */
    private static long edgeContribution(long neighborLabel, SemanticEdge edge, boolean incoming) {
        long edgeCode = ((long) edge.kind().ordinal() << 1) | (incoming ? 1L : 0L);
        return mix(neighborLabel, edgeCode);
    }

    private static long fnv1a(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix(long a, long b) {
        long h = a ^ (b + 0x9e3779b97f4a7c15L + (a << 6) + (a >>> 2));
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return h;
    }
}
