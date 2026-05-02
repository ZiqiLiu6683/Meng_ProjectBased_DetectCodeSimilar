package com.ziqi.codesim.pipeline;

public record MethodDescriptor(
        String methodId,
        String displayName,
        String methodName,
        String signature,
        String declaringType,
        int occurrenceIndex,
        int tokenCount,
        int treeSize
) {
}
