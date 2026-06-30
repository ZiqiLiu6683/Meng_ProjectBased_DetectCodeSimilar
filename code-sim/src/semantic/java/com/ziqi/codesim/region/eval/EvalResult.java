package com.ziqi.codesim.region.eval;

import java.util.Set;

/**
 * Outcome of evaluating one {@link EvalCase}.
 *
 * @param evalCase        the case evaluated
 * @param detected        the expected clone was recovered (structurally or semantically)
 * @param detectedVia     "structural", "semantic", or "none"
 * @param rank            0-based rank of the structural region, or -1 (semantic/none have no rank)
 * @param coverage        substance-weighted coverage of the matched region (0 if semantic/none)
 * @param recoveredLeft   left method signatures of the matched evidence
 * @param recoveredRight  right method signatures of the matched evidence
 * @param error           non-null if the case failed to run (e.g. did not compile)
 */
public record EvalResult(
        EvalCase evalCase,
        boolean detected,
        String detectedVia,
        int rank,
        double coverage,
        Set<String> recoveredLeft,
        Set<String> recoveredRight,
        String error
) {
    public EvalResult {
        recoveredLeft = Set.copyOf(recoveredLeft);
        recoveredRight = Set.copyOf(recoveredRight);
    }

    /** A negative case where a clone was (wrongly) detected. */
    public boolean isFalsePositive() {
        return !evalCase.expectClone() && detected;
    }

    /** A positive case where the injected clone was recovered. */
    public boolean isTruePositive() {
        return evalCase.expectClone() && detected;
    }
}
