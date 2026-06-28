package com.ziqi.codesim.semantic.model;

import java.util.List;

public record AnalyzedMethod(
        String methodId,
        String declaringType,
        String signature,
        String returnType,
        List<String> parameterTypes,
        ControlFlowGraphUnit cfg,
        // discovRE's "size of local variables" feature: the JVM method's local variable slot
        // count (bytecode max_locals); falls back to the SSA value count if unavailable.
        int localValueCount
) {
    public AnalyzedMethod {
        parameterTypes = List.copyOf(parameterTypes);
    }

    // Backward-compatible constructor for callers that do not supply a local-value count.
    public AnalyzedMethod(
            String methodId,
            String declaringType,
            String signature,
            String returnType,
            List<String> parameterTypes,
            ControlFlowGraphUnit cfg) {
        this(methodId, declaringType, signature, returnType, parameterTypes, cfg, 0);
    }
}
