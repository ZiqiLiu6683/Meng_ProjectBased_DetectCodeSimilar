package com.ziqi.codesim.semantic.feature;

import java.util.Map;

public record BlockNumericFeatures(
        int instructionCount,
        int predecessorCount,
        int successorCount,
        int branchCount,
        int returnCount,
        int callCount,
        int arithmeticCount,
        int comparisonCount,
        int assignmentCount,
        int constantCount,
        int stringReferenceCount,
        Map<String, Integer> operationHistogram
) {
    public BlockNumericFeatures {
        operationHistogram = Map.copyOf(operationHistogram);
    }
}
