package com.ziqi.codesim.region.semantic;

/**
 * A cross-file method pair that Phase B proved semantically equivalent — a Type-4 clone found by
 * SMT even when Phase A's structural matching aligned nothing (e.g. {@code x*2} vs {@code y+y}).
 *
 * @param leftMethod  left method signature
 * @param rightMethod right method signature
 * @param verdict     always {@link EquivalenceVerdict#EQUIVALENT} for an emitted match
 */
public record SemanticMethodMatch(String leftMethod, String rightMethod, EquivalenceVerdict verdict) {
}
