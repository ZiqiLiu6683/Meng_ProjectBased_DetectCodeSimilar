package com.ziqi.codesim.semantic.feature;

import java.util.Map;

public record MethodNumericFeatures(
        int basicBlockCount,
        int cfgEdgeCount,
        int branchCount,
        int returnCount,
        int callCount,
        int internalCallCount,
        int externalCallCount,
        int arithmeticCount,
        int logicCount,
        int comparisonCount,
        int assignmentCount,
        int allocationCount,
        int fieldAccessCount,
        int arrayAccessCount,
        int constantCount,
        int stringReferenceCount,
        int instructionCount,
        int parameterCount,
        Map<String, Integer> operationHistogram
) {
    public MethodNumericFeatures {
        operationHistogram = Map.copyOf(operationHistogram);
    }
}
