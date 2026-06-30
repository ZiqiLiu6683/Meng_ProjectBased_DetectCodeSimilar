package com.ziqi.codesim.region.eval;

import java.util.Set;

/**
 * Outcome of evaluating one {@link EvalCase}.
 *
 * @param evalCase        the case evaluated
 * @param detected        a region within the top-K linked the expected methods with enough coverage
 * @param rank            0-based rank of that region in the selector output, or -1 if none
 * @param coverage        substance-weighted coverage of the matched region (0 if none)
 * @param recoveredLeft   left method signatures spanned by the matched region
 * @param recoveredRight  right method signatures spanned by the matched region
 * @param error           non-null if the case failed to run (e.g. did not compile)
 */
public record EvalResult(
        EvalCase evalCase,
        boolean detected,
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
