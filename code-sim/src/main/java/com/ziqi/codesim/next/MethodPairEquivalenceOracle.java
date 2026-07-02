package com.ziqi.codesim.next;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link SemanticEquivalenceOracle} backed by the method pairs Phase B proved equivalent. The
 * proven pairs arrive as raw WALA method signatures (e.g. {@code A.twice(I)I} ↔ {@code B.doubled(I)I});
 * candidate regions carry source-style display names (e.g. {@code A.twice(int)}). Both are
 * normalized to the same {@link MethodSignatureKeys#signatureKey} form, so a region pair is
 * confirmed equivalent iff its two methods correspond to a proven pair — regardless of renaming.
 */
public final class MethodPairEquivalenceOracle implements SemanticEquivalenceOracle {

    private static final String SEP = "##";

    private final Set<String> equivalentKeys;

    private MethodPairEquivalenceOracle(Set<String> equivalentKeys) {
        this.equivalentKeys = equivalentKeys;
    }

    /**
     * @param rawSignaturePairs each element is {@code {leftRawSignature, rightRawSignature}}, a
     *                          method pair Phase B proved semantically equivalent
     */
    public static MethodPairEquivalenceOracle fromRawSignaturePairs(List<String[]> rawSignaturePairs) {
        Set<String> keys = new HashSet<>();
        for (String[] pair : rawSignaturePairs) {
            keys.add(rawKey(pair[0]) + SEP + rawKey(pair[1]));
        }
        return new MethodPairEquivalenceOracle(Set.copyOf(keys));
    }

    @Override
    public boolean provenEquivalent(CodeRegion left, CodeRegion right) {
        if (left.kind() != RegionKind.METHOD || right.kind() != RegionKind.METHOD) {
            return false;
        }
        return equivalentKeys.contains(displayKey(left.displayName()) + SEP + displayKey(right.displayName()));
    }

    private static String rawKey(String rawSignature) {
        return MethodSignatureKeys.signatureKey(
                MethodSignatureKeys.simpleName(rawSignature),
                MethodSignatureKeys.bytecodeParamTypes(rawSignature));
    }

    private static String displayKey(String displayName) {
        return MethodSignatureKeys.signatureKey(
                MethodSignatureKeys.simpleName(displayName),
                MethodSignatureKeys.sourceParamTypes(displayName));
    }
}
