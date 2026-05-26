package com.ziqi.codesim.semantic.feature;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NumericFeatureDistance {
    public double methodDistance(MethodNumericFeatures a, MethodNumericFeatures b) {
        double numeric = average(
                normalizedAbs(a.basicBlockCount(), b.basicBlockCount()),
                normalizedAbs(a.cfgEdgeCount(), b.cfgEdgeCount()),
                normalizedAbs(a.branchCount(), b.branchCount()),
                normalizedAbs(a.returnCount(), b.returnCount()),
                normalizedAbs(a.callCount(), b.callCount()),
                normalizedAbs(a.internalCallCount(), b.internalCallCount()),
                normalizedAbs(a.externalCallCount(), b.externalCallCount()),
                normalizedAbs(a.arithmeticCount(), b.arithmeticCount()),
                normalizedAbs(a.logicCount(), b.logicCount()),
                normalizedAbs(a.comparisonCount(), b.comparisonCount()),
                normalizedAbs(a.assignmentCount(), b.assignmentCount()),
                normalizedAbs(a.allocationCount(), b.allocationCount()),
                normalizedAbs(a.fieldAccessCount(), b.fieldAccessCount()),
                normalizedAbs(a.arrayAccessCount(), b.arrayAccessCount()),
                normalizedAbs(a.constantCount(), b.constantCount()),
                normalizedAbs(a.stringReferenceCount(), b.stringReferenceCount()),
                normalizedAbs(a.instructionCount(), b.instructionCount()),
                normalizedAbs(a.parameterCount(), b.parameterCount())
        );
        return average(numeric, histogramDistance(a.operationHistogram(), b.operationHistogram()));
    }

    public double blockDistance(BlockNumericFeatures a, BlockNumericFeatures b) {
        double numeric = average(
                normalizedAbs(a.instructionCount(), b.instructionCount()),
                normalizedAbs(a.predecessorCount(), b.predecessorCount()),
                normalizedAbs(a.successorCount(), b.successorCount()),
                normalizedAbs(a.branchCount(), b.branchCount()),
                normalizedAbs(a.returnCount(), b.returnCount()),
                normalizedAbs(a.callCount(), b.callCount()),
                normalizedAbs(a.arithmeticCount(), b.arithmeticCount()),
                normalizedAbs(a.comparisonCount(), b.comparisonCount()),
                normalizedAbs(a.assignmentCount(), b.assignmentCount()),
                normalizedAbs(a.constantCount(), b.constantCount()),
                normalizedAbs(a.stringReferenceCount(), b.stringReferenceCount())
        );
        return average(numeric, histogramDistance(a.operationHistogram(), b.operationHistogram()));
    }

    private static double normalizedAbs(int a, int b) {
        int max = Math.max(a, b);
        if (max == 0) return 0.0;
        return (double) Math.abs(a - b) / max;
    }

    private static double histogramDistance(Map<String, Integer> a, Map<String, Integer> b) {
        Set<String> keys = new HashSet<>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        if (keys.isEmpty()) return 0.0;
        double diff = 0.0;
        double total = 0.0;
        for (String key : keys) {
            int av = a.getOrDefault(key, 0);
            int bv = b.getOrDefault(key, 0);
            diff += Math.abs(av - bv);
            total += Math.max(av, bv);
        }
        return total == 0.0 ? 0.0 : diff / total;
    }

    private static double average(double... values) {
        if (values.length == 0) return 0.0;
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }
}
