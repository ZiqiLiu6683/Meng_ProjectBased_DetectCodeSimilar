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
        // discovRE robust features added: loop estimate via non-trivial strongly-connected
        // components, and local-variable size (bytecode max_locals; SSA value count fallback).
        int loopComponentCount,
        int localValueCount,
        Map<String, Integer> operationHistogram
) {
    public MethodNumericFeatures {
        operationHistogram = Map.copyOf(operationHistogram);
    }
}
