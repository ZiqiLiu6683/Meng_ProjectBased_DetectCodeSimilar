package com.ziqi.codesim.semantic.model;

import java.util.List;

public record ControlFlowGraphUnit(
        List<BasicBlockUnit> blocks,
        List<ControlFlowEdge> edges
) {
    public ControlFlowGraphUnit {
        blocks = List.copyOf(blocks);
        edges = List.copyOf(edges);
    }
}
