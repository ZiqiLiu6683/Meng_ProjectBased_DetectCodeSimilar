package com.ziqi.codesim.region.eval;

import java.util.List;

/**
 * One injection-based evaluation case: a donor file and a transformed counterpart with known
 * ground truth (the clone type, whether a clone is present, and which methods the recovered region
 * should span on each side). The harness checks whether the region selector recovers it.
 *
 * @param id               short identifier
 * @param transformation   the injected transformation (e.g. {@code T2_RENAME}, {@code HELPER_EXTRACT})
 * @param leftSource       donor source (compilable standalone)
 * @param rightSource      transformed source (compilable standalone)
 * @param expectClone      ground truth: true if a real clone was injected, false for a negative
 * @param leftMethodHints  method-name substrings the recovered region should span on the left
 * @param rightMethodHints method-name substrings the recovered region should span on the right
 */
public record EvalCase(
        String id,
        String transformation,
        String leftSource,
        String rightSource,
        boolean expectClone,
        List<String> leftMethodHints,
        List<String> rightMethodHints
) {
    public EvalCase {
        leftMethodHints = List.copyOf(leftMethodHints);
        rightMethodHints = List.copyOf(rightMethodHints);
    }
}
