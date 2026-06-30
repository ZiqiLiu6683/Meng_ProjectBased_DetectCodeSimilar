package com.ziqi.codesim.region.descriptor;

import com.ziqi.codesim.region.model.NodeDescriptor;
import com.ziqi.codesim.region.model.SemanticGraph;
import com.ziqi.codesim.region.model.SemanticNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link NodeDescriptor} for every node of a {@link SemanticGraph} by combining the
 * lexical channel (name-free base label) with the structural channel (WL-on-SDG hashes). Pure Java,
 * no WALA. The semantic value-hash channel will be folded in here as a third component.
 */
public final class NodeDescriptorBuilder {

    private final WeisfeilerLehmanLabeler labeler;

    public NodeDescriptorBuilder() {
        this(new WeisfeilerLehmanLabeler());
    }

    public NodeDescriptorBuilder(WeisfeilerLehmanLabeler labeler) {
        this.labeler = labeler;
    }

    /** @return node id -> descriptor, in graph node order. */
    public Map<Integer, NodeDescriptor> build(SemanticGraph graph) {
        Map<Integer, List<Long>> wlHashes = labeler.label(graph);
        Map<Integer, NodeDescriptor> descriptors = new LinkedHashMap<>();
        for (SemanticNode node : graph.nodes()) {
            descriptors.put(node.id(), new NodeDescriptor(
                    node.id(),
                    WeisfeilerLehmanLabeler.baseLabel(node),
                    wlHashes.get(node.id()),
                    node.semanticValueHash()
            ));
        }
        return descriptors;
    }
}
