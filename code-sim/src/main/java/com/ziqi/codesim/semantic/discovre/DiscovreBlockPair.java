package com.ziqi.codesim.semantic.discovre;

public record DiscovreBlockPair(
        String leftBlockId,
        String rightBlockId,
        double blockDistance
) {
}
