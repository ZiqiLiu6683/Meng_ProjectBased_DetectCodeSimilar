package com.ziqi.codesim.next.semantic;

/**
 * Immutable, per-request controls for the semantic pipeline.
 *
 * <p>These options deliberately do not use system properties: the web server handles multiple
 * requests concurrently, so process-global switches would let one user's choice affect another
 * user's analysis.
 */
public record AnalysisOptions(AnalysisDepth depth, T4Mode t4Mode) {

    public AnalysisOptions {
        if (depth == null || t4Mode == null) {
            throw new IllegalArgumentException("analysis depth and T4 mode are required");
        }
        if (depth == AnalysisDepth.SOURCE_AST && t4Mode != T4Mode.OFF) {
            throw new IllegalArgumentException("T4 verification requires WALA region analysis");
        }
    }

    /** Existing command-line and batch behavior remains configurable by the legacy property. */
    public static AnalysisOptions defaults() {
        return new AnalysisOptions(
                AnalysisDepth.WALA_REGIONS,
                Boolean.getBoolean("codesim.skipDynamic") ? T4Mode.SMT_ONLY : T4Mode.SMT_DYNAMIC);
    }

    public static AnalysisOptions quick() {
        return new AnalysisOptions(AnalysisDepth.SOURCE_AST, T4Mode.OFF);
    }

    public enum AnalysisDepth {
        SOURCE_AST,
        WALA_REGIONS
    }

    public enum T4Mode {
        OFF,
        SMT_ONLY,
        SMT_DYNAMIC
    }
}
