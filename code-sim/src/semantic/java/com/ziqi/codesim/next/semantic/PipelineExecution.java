package com.ziqi.codesim.next.semantic;

import com.ziqi.codesim.next.NextPipelineResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One observable execution of the pairwise pipeline.
 *
 * <p>The detector result and the execution provenance deliberately travel together: benchmark
 * callers must be able to distinguish the WALA path from the source-only fallback and must know
 * which stage failed. This record is also used by the batch JSON schema; keep field meanings stable
 * and version the enclosing batch record when they change.
 */
public record PipelineExecution(
        NextPipelineResult result,
        AnalysisMode analysisMode,
        Map<String, StageOutcome> stages,
        String fallbackStage,
        String fallbackReason,
        Map<String, CompilationProvenance> compilations
) {
    public PipelineExecution {
        stages = Collections.unmodifiableMap(new LinkedHashMap<>(stages));
        fallbackStage = fallbackStage == null ? "" : fallbackStage;
        fallbackReason = fallbackReason == null ? "" : fallbackReason;
        compilations = compilations == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(compilations));
    }

    public PipelineExecution(NextPipelineResult result, AnalysisMode analysisMode,
                             Map<String, StageOutcome> stages, String fallbackStage,
                             String fallbackReason) {
        this(result, analysisMode, stages, fallbackStage, fallbackReason, Map.of());
    }

    public enum AnalysisMode {
        SOURCE_PLUS_WALA_SMT,
        SOURCE_PLUS_WALA_SMT_DYNAMIC,
        SOURCE_PLUS_PROJECT_CONTEXT_WALA_SMT,
        SOURCE_PLUS_PROJECT_CONTEXT_WALA_SMT_DYNAMIC,
        SOURCE_PLUS_STUBBED_WALA_SMT,
        SOURCE_PLUS_STUBBED_WALA_SMT_DYNAMIC,
        SOURCE_ONLY_FALLBACK
    }

    public enum StageStatus {
        SUCCESS,
        FAILED,
        SKIPPED_CONFIG,
        NOT_REACHED
    }

    public record StageOutcome(StageStatus status, long durationMs, String detail) {
        public StageOutcome {
            detail = detail == null ? "" : detail;
        }
    }

    public record CompilationProvenance(
            String mode,
            String cacheKey,
            boolean cacheHit,
            int generatedStubCount,
            int javaRelease,
            int supportClasspathEntries,
            String diagnosticSummary
    ) {
        public CompilationProvenance {
            mode = mode == null ? "" : mode;
            cacheKey = cacheKey == null ? "" : cacheKey;
            diagnosticSummary = diagnosticSummary == null ? "" : diagnosticSummary;
        }
    }
}
