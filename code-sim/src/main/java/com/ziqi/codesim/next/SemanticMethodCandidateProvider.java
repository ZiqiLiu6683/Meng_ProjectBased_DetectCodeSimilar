package com.ziqi.codesim.next;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits candidate signals for method pairs Phase B proved semantically equivalent, so a Type-4
 * clone whose sides share no tokens/structure still enters the candidate pool (the recognizer only
 * ever judges pairs that were proposed). Paired with {@link MethodPairEquivalenceOracle}, this is
 * what lets the pipeline both discover and confirm behaviourally-equivalent, structurally different
 * code.
 */
public final class SemanticMethodCandidateProvider implements CandidateSignalProvider {

    private static final String DEFAULT_CHANNEL = "SEMANTIC_EQUIV_SCAN";

    private final List<String[]> equivalentRawPairs;
    private final String channel;

    /** @param equivalentRawPairs each {@code {leftRawSignature, rightRawSignature}} proven equivalent */
    public SemanticMethodCandidateProvider(List<String[]> equivalentRawPairs) {
        this(equivalentRawPairs, DEFAULT_CHANNEL);
    }

    /**
     * @param equivalentRawPairs each {@code {leftRawSignature, rightRawSignature}} the source layer matched
     * @param channel the provenance channel to stamp on emitted signals (e.g. {@code DYNAMIC_EQUIV_SCAN}
     *                for pairs the dynamic layer matched rather than SMT proved)
     */
    public SemanticMethodCandidateProvider(List<String[]> equivalentRawPairs, String channel) {
        this.equivalentRawPairs = List.copyOf(equivalentRawPairs);
        this.channel = channel;
    }

    @Override
    public List<CandidateSignal> findSignals(EvidencePackage evidencePackage) {
        Map<String, String> leftRegions = methodRegionsByKey(evidencePackage.leftRegions());
        Map<String, String> rightRegions = methodRegionsByKey(evidencePackage.rightRegions());
        List<CandidateSignal> signals = new ArrayList<>();
        for (String[] pair : equivalentRawPairs) {
            String leftRegionId = leftRegions.get(rawKey(pair[0]));
            String rightRegionId = rightRegions.get(rawKey(pair[1]));
            if (leftRegionId != null && rightRegionId != null) {
                signals.add(new CandidateSignal(leftRegionId, rightRegionId, channel, 1.0));
            }
        }
        return signals;
    }

    private static Map<String, String> methodRegionsByKey(List<CodeRegion> regions) {
        Map<String, String> byKey = new HashMap<>();
        for (CodeRegion region : regions) {
            if (region.kind() == RegionKind.METHOD) {
                byKey.putIfAbsent(displayKey(region.displayName()), region.regionId());
            }
        }
        return byKey;
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
