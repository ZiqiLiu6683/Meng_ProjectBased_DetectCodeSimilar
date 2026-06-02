package com.ziqi.codesim.semantic.discovre;

public record DiscovreBlockFeatures(
        int arithmeticInstructions,
        int calls,
        int instructions,
        int logicInstructions,
        int transferInstructions,
        int stringConstants,
        int numericConstants
) {
}
