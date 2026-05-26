package com.ziqi.codesim.semantic.model;

import java.util.List;

public record AnalyzedMethod(
        String methodId,
        String declaringType,
        String signature,
        String returnType,
        List<String> parameterTypes,
        ControlFlowGraphUnit cfg
) {
    public AnalyzedMethod {
        parameterTypes = List.copyOf(parameterTypes);
    }
}
