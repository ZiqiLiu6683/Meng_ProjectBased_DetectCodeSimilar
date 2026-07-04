package com.ziqi.codesim.next;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link DynamicEquivalenceOracle} backed by the method pairs the dynamic layer found to agree on
 * every sampled input. Mirrors {@link MethodPairEquivalenceOracle} in shape (raw WALA signatures
 * normalized to rename-invariant keys), but the verdict it supplies is EVIDENCE, not proof -- so the
 * recognizer maps it to {@link CloneRegionType#T4_DYNAMIC_EVIDENCE} rather than T4_CONFIRMED.
 */
public final class MethodPairDynamicOracle implements DynamicEquivalenceOracle {

    private static final String SEP = "##";

    private final Set<String> likelyEquivalentKeys;

    private MethodPairDynamicOracle(Set<String> likelyEquivalentKeys) {
        this.likelyEquivalentKeys = likelyEquivalentKeys;
    }

    /**
     * @param rawSignaturePairs each element is {@code {leftRawSignature, rightRawSignature}}, a
     *                          method pair the dynamic checker found likely-equivalent by I/O sampling
     */
    public static MethodPairDynamicOracle fromRawSignaturePairs(List<String[]> rawSignaturePairs) {
        Set<String> keys = new HashSet<>();
        for (String[] pair : rawSignaturePairs) {
            keys.add(rawKey(pair[0]) + SEP + rawKey(pair[1]));
        }
        return new MethodPairDynamicOracle(Set.copyOf(keys));
    }

    @Override
    public boolean likelyEquivalent(CodeRegion left, CodeRegion right) {
        if (left.kind() != RegionKind.METHOD || right.kind() != RegionKind.METHOD) {
            return false;
        }
        return likelyEquivalentKeys.contains(displayKey(left.displayName()) + SEP + displayKey(right.displayName()));
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
