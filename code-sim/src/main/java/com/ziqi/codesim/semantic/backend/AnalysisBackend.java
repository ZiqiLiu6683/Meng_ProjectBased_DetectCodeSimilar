package com.ziqi.codesim.semantic.backend;

import com.ziqi.codesim.semantic.model.AnalyzedProgram;

import java.nio.file.Path;

public interface AnalysisBackend {
    AnalyzedProgram analyze(Path input) throws AnalysisException;
}
