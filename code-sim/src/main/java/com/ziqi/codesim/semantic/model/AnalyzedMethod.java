package com.ziqi.codesim.semantic.model;

import java.util.List;

public record AnalyzedMethod(
        String methodId,
        String declaringType,
        String signature,
        String returnType,
        List<String> parameterTypes,
        ControlFlowGraphUnit cfg,
        // Number of SSA values / local slots, a proxy for discovRE's "size of local
        // variables" robust numeric feature.
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
