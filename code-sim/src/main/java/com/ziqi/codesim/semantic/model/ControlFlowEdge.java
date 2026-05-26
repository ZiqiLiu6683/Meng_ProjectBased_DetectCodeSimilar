package com.ziqi.codesim.semantic.model;

public record ControlFlowEdge(
        String fromBlockId,
        String toBlockId,
        String kind
) {
}
