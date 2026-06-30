package com.ziqi.codesim.region.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Our own, WALA-free representation of one file's System Dependence Graph: instruction-level nodes
 * spanning every analyzed method, connected by intra- and inter-procedural dependence edges. This
 * is the substrate Phase A grows boundary-free, cross-method region groups on.
 *
 * <p>The graph is immutable once built (use {@link Builder}). Adjacency lists are computed once on
 * construction for O(1) successor/predecessor lookups during region growth.
 */
public final class SemanticGraph {

    private final String fileLabel;
    private final List<SemanticNode> nodes;
    private final List<SemanticEdge> edges;
    private final Map<Integer, SemanticNode> nodeById;
    private final Map<Integer, List<SemanticEdge>> outgoing;
    private final Map<Integer, List<SemanticEdge>> incoming;

    private SemanticGraph(String fileLabel, List<SemanticNode> nodes, List<SemanticEdge> edges) {
        this.fileLabel = fileLabel;
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
        Map<Integer, SemanticNode> byId = new LinkedHashMap<>();
        for (SemanticNode node : this.nodes) {
            byId.put(node.id(), node);
        }
        this.nodeById = Collections.unmodifiableMap(byId);
        Map<Integer, List<SemanticEdge>> out = new LinkedHashMap<>();
        Map<Integer, List<SemanticEdge>> in = new LinkedHashMap<>();
        for (SemanticEdge edge : this.edges) {
            out.computeIfAbsent(edge.fromId(), k -> new ArrayList<>()).add(edge);
            in.computeIfAbsent(edge.toId(), k -> new ArrayList<>()).add(edge);
        }
        this.outgoing = freezeAdjacency(out);
        this.incoming = freezeAdjacency(in);
    }

    private static Map<Integer, List<SemanticEdge>> freezeAdjacency(Map<Integer, List<SemanticEdge>> map) {
        Map<Integer, List<SemanticEdge>> frozen = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<SemanticEdge>> entry : map.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    public String fileLabel() {
        return fileLabel;
    }

    public List<SemanticNode> nodes() {
        return nodes;
    }

    public List<SemanticEdge> edges() {
        return edges;
    }

    public Optional<SemanticNode> node(int id) {
        return Optional.ofNullable(nodeById.get(id));
    }

    public List<SemanticEdge> outgoing(int nodeId) {
        return outgoing.getOrDefault(nodeId, List.of());
    }

    public List<SemanticEdge> incoming(int nodeId) {
        return incoming.getOrDefault(nodeId, List.of());
    }

    /** Distinct declaring-method signatures present in the graph, in first-seen order. */
    public Set<String> methodSignatures() {
        Set<String> signatures = new LinkedHashSet<>();
        for (SemanticNode node : nodes) {
            signatures.add(node.methodSignature());
        }
        return Collections.unmodifiableSet(signatures);
    }

    public List<SemanticNode> nodesInMethod(String methodSignature) {
        List<SemanticNode> result = new ArrayList<>();
        for (SemanticNode node : nodes) {
            if (node.methodSignature().equals(methodSignature)) {
                result.add(node);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public long interproceduralEdgeCount() {
        return edges.stream().filter(SemanticEdge::isInterprocedural).count();
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    public static Builder builder(String fileLabel) {
        return new Builder(fileLabel);
    }

    /** Mutable accumulator used by {@code SdgBuilder}; produces an immutable {@link SemanticGraph}. */
    public static final class Builder {
        private final String fileLabel;
        private final List<SemanticNode> nodes = new ArrayList<>();
        private final List<SemanticEdge> edges = new ArrayList<>();

        private Builder(String fileLabel) {
            this.fileLabel = fileLabel;
        }

        public Builder addNode(SemanticNode node) {
            nodes.add(node);
            return this;
        }

        public Builder addEdge(SemanticEdge edge) {
            edges.add(edge);
            return this;
        }

        public SemanticGraph build() {
            return new SemanticGraph(fileLabel, nodes, edges);
        }
    }
}
