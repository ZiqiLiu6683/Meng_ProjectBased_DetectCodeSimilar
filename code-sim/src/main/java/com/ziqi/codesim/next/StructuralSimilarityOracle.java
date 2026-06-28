package com.ziqi.codesim.next;

import java.util.OptionalDouble;

/**
 * Supplies a structural (control-flow) similarity for a whole-method region pair, used as the
 * second discovRE stage: after syntactic recognition proposes a clone type, the recognizer
 * consults this oracle to confirm or veto whole-method clones.
 *
 * <p>Returns an empty value when no structural evidence applies (non-method regions, methods the
 * backend did not analyze, or the source-only pipeline with no CFG backend). In that case the
 * recognizer keeps its syntactic verdict unchanged.
 */
public interface StructuralSimilarityOracle {

    /** Oracle that never has structural evidence; used by the source-only pipeline. */
    StructuralSimilarityOracle NONE = (left, right) -> OptionalDouble.empty();

    OptionalDouble similarity(CodeRegion left, CodeRegion right);
}
