package com.ziqi.codesim.next;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits candidate signals for method pairs that a Phase A structural region group aligns, so
 * cross-method (helper-extraction) pairs are proposed to the recognizer even when their tokens
 * differ. Paired with {@link RegionGroupStructuralOracle}, this lets the pipeline both propose and
 * flag structurally-aligned clones that the source/CFG-KNN discovery and Phase B semantics miss.
 */
public final class StructuralRegionCandidateProvider implements CandidateSignalProvider {

    private static final String CHANNEL = "STRUCTURAL_REGION_SCAN";

    private final List<StructuralMethodPair> pairs;

    public StructuralRegionCandidateProvider(List<StructuralMethodPair> pairs) {
        this.pairs = List.copyOf(pairs);
    }

    @Override
    public List<CandidateSignal> findSignals(EvidencePackage evidencePackage) {
        Map<String, String> leftRegions = methodRegionsByKey(evidencePackage.leftRegions());
        Map<String, String> rightRegions = methodRegionsByKey(evidencePackage.rightRegions());
        List<CandidateSignal> signals = new ArrayList<>();
        for (StructuralMethodPair pair : pairs) {
            String leftRegionId = leftRegions.get(rawKey(pair.leftRawSignature()));
            String rightRegionId = rightRegions.get(rawKey(pair.rightRawSignature()));
            if (leftRegionId != null && rightRegionId != null) {
                signals.add(new CandidateSignal(leftRegionId, rightRegionId, CHANNEL, pair.coverage()));
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
