package com.ziqi.codesim.next;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link StructuralRegionOracle} backed by Phase A region groups. Each {@link StructuralMethodPair}
 * says "a region group aligns left method X and right method Y with this coverage"; a cross-method
 * region group (helper extraction) contributes several. Method signatures are normalized to
 * {@link MethodSignatureKeys#signatureKey} form so region-carried WALA signatures match the
 * source-style display names on candidate regions regardless of renaming.
 */
public final class RegionGroupStructuralOracle implements StructuralRegionOracle {

    private static final String SEP = "##";

    private final Map<String, Double> coverageByPair;

    private RegionGroupStructuralOracle(Map<String, Double> coverageByPair) {
        this.coverageByPair = coverageByPair;
    }

    public static RegionGroupStructuralOracle from(List<StructuralMethodPair> pairs) {
        Map<String, Double> coverage = new HashMap<>();
        for (StructuralMethodPair pair : pairs) {
            String key = rawKey(pair.leftRawSignature()) + SEP + rawKey(pair.rightRawSignature());
            coverage.merge(key, pair.coverage(), Math::max);
        }
        return new RegionGroupStructuralOracle(Map.copyOf(coverage));
    }

    @Override
    public double regionCoverage(CodeRegion left, CodeRegion right) {
        if (left.kind() != RegionKind.METHOD || right.kind() != RegionKind.METHOD) {
            return 0.0;
        }
        return coverageByPair.getOrDefault(
                displayKey(left.displayName()) + SEP + displayKey(right.displayName()), 0.0);
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
