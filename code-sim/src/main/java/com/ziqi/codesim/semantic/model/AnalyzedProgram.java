package com.ziqi.codesim.semantic.model;

import java.util.List;

public record AnalyzedProgram(
        String programId,
        List<AnalyzedMethod> methods
) {
    public AnalyzedProgram {
        methods = List.copyOf(methods);
    }
}
