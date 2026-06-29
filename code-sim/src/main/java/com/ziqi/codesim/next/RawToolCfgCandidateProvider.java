package com.ziqi.codesim.next;

import com.ziqi.codesim.semantic.knn.BlockCandidate;
import com.ziqi.codesim.semantic.knn.BlockCandidateSelector;
import com.ziqi.codesim.semantic.knn.KnnFeatureView;
import com.ziqi.codesim.semantic.raw.RawToolBlock;
import com.ziqi.codesim.semantic.raw.RawToolMethod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RawToolCfgCandidateProvider implements CandidateSignalProvider {
    private static final String CHANNEL = "CFG_KNN_SCAN";
    private final List<RawToolMethod> leftMethods;
    private final List<RawToolMethod> rightMethods;
    private final Set<String> selectedChannels;
    private final KnnFeatureView view;
    private final int topK;
    private final BlockCandidateSelector selector;

    public RawToolCfgCandidateProvider(List<RawToolMethod> leftMethods,
                                       List<RawToolMethod> rightMethods,
                                       Set<String> selectedChannels,
                                       KnnFeatureView view,
                                       int topK) {
        this(leftMethods, rightMethods, selectedChannels, view, topK, new BlockCandidateSelector());
    }

    RawToolCfgCandidateProvider(List<RawToolMethod> leftMethods,
                                List<RawToolMethod> rightMethods,
                                Set<String> selectedChannels,
                                KnnFeatureView view,
                                int topK,
                                BlockCandidateSelector selector) {
        this.leftMethods = List.copyOf(leftMethods);
        this.rightMethods = List.copyOf(rightMethods);
        this.selectedChannels = Set.copyOf(selectedChannels);
        this.view = view;
        this.topK = topK;
        this.selector = selector;
    }

    @Override
    public List<CandidateSignal> findSignals(EvidencePackage evidencePackage) {
        if (leftMethods.isEmpty() || rightMethods.isEmpty()) {
            return List.of();
        }

        BlockIndex leftIndex = blockIndex(leftMethods, "L");
        BlockIndex rightIndex = blockIndex(rightMethods, "R");
        Map<String, List<BlockCandidate>> blockCandidates = selector.select(
                leftIndex.blocks(),
                rightIndex.blocks(),
                selectedChannels,
                view,
                topK
        );
        Map<MethodPairKey, MethodDistanceAccumulator> distances = new HashMap<>();
        for (List<BlockCandidate> candidates : blockCandidates.values()) {
            for (BlockCandidate candidate : candidates) {
                String leftMethodKey = leftIndex.methodByBlockId().get(candidate.queryBlockId());
                String rightMethodKey = rightIndex.methodByBlockId().get(candidate.candidateBlockId());
                if (leftMethodKey == null || rightMethodKey == null) {
                    continue;
                }
                distances.computeIfAbsent(new MethodPairKey(leftMethodKey, rightMethodKey),
                                ignored -> new MethodDistanceAccumulator())
                        .add(candidate.distance());
            }
        }

        MethodRegionIndex leftRegionIndex = MethodRegionIndex.build(evidencePackage.leftRegions());
        MethodRegionIndex rightRegionIndex = MethodRegionIndex.build(evidencePackage.rightRegions());
        List<CandidateSignal> signals = new ArrayList<>();
        for (Map.Entry<MethodPairKey, MethodDistanceAccumulator> entry : distances.entrySet()) {
            Optional<CodeRegion> leftRegion = leftRegionIndex.resolve(entry.getKey().leftMethodKey());
            Optional<CodeRegion> rightRegion = rightRegionIndex.resolve(entry.getKey().rightMethodKey());
            if (leftRegion.isEmpty() || rightRegion.isEmpty()) {
                continue;
            }
            double score = entry.getValue().score();
            signals.add(new CandidateSignal(leftRegion.get().regionId(), rightRegion.get().regionId(), CHANNEL, score));
            linkedBodyRegion(evidencePackage.leftRegions(), leftRegion.get())
                    .ifPresent(leftBody -> signals.add(new CandidateSignal(
                            leftBody.regionId(), rightRegion.get().regionId(), CHANNEL, score)));
            linkedBodyRegion(evidencePackage.rightRegions(), rightRegion.get())
                    .ifPresent(rightBody -> signals.add(new CandidateSignal(
                            leftRegion.get().regionId(), rightBody.regionId(), CHANNEL, score)));
        }
        return signals.stream()
                .sorted(Comparator.comparing(CandidateSignal::leftRegionId)
                        .thenComparing(CandidateSignal::rightRegionId)
                        .thenComparing(CandidateSignal::channel))
                .toList();
    }

    private static BlockIndex blockIndex(List<RawToolMethod> methods, String prefix) {
        Map<String, RawToolBlock> blocks = new LinkedHashMap<>();
        Map<String, String> methodByBlockId = new HashMap<>();
        for (int methodIndex = 0; methodIndex < methods.size(); methodIndex++) {
            RawToolMethod method = methods.get(methodIndex);
            String methodKey = methodKey(method);
            for (RawToolBlock block : method.blocks()) {
                String blockId = prefix + methodIndex + ":B" + block.rawBlockNumber();
                blocks.put(blockId, block);
                methodByBlockId.put(blockId, methodKey);
            }
        }
        return new BlockIndex(blocks, methodByBlockId);
    }

    // Resolves a WALA bytecode method key (e.g. "com.example.Foo.bar(ILjava/lang/String;)V") to the
    // matching source-side METHOD region (display name "Foo.bar(int,String)"). Overloads are kept
    // distinct by an erased-type signature; only when that is inconclusive does it fall back to
    // arity, then to a unique simple name. If the simple name is still ambiguous it returns empty
    // rather than guessing the wrong overload (no fabricated pairing).
    private static final class MethodRegionIndex {
        private final Map<String, List<CodeRegion>> bySignature = new HashMap<>();
        private final Map<String, List<CodeRegion>> byNameAndArity = new HashMap<>();
        private final Map<String, List<CodeRegion>> bySimpleName = new HashMap<>();

        static MethodRegionIndex build(List<CodeRegion> regions) {
            MethodRegionIndex index = new MethodRegionIndex();
            for (CodeRegion region : regions) {
                if (region.kind() != RegionKind.METHOD) {
                    continue;
                }
                String name = MethodSignatureKeys.simpleName(region.displayName());
                List<String> types = MethodSignatureKeys.sourceParamTypes(region.displayName());
                add(index.bySignature, MethodSignatureKeys.signatureKey(name, types), region);
                add(index.byNameAndArity, MethodSignatureKeys.normalize(name) + "/" + types.size(), region);
                add(index.bySimpleName, MethodSignatureKeys.normalize(name), region);
            }
            return index;
        }

        Optional<CodeRegion> resolve(String rawMethodKey) {
            String name = MethodSignatureKeys.simpleName(rawMethodKey);
            List<String> types = MethodSignatureKeys.bytecodeParamTypes(rawMethodKey);
            List<CodeRegion> bySig = bySignature.get(MethodSignatureKeys.signatureKey(name, types));
            if (bySig != null && !bySig.isEmpty()) {
                return Optional.of(bySig.get(0));
            }
            List<CodeRegion> byArity = byNameAndArity.get(MethodSignatureKeys.normalize(name) + "/" + types.size());
            if (byArity != null && byArity.size() == 1) {
                return Optional.of(byArity.get(0));
            }
            List<CodeRegion> bySimple = bySimpleName.get(MethodSignatureKeys.normalize(name));
            if (bySimple != null && bySimple.size() == 1) {
                return Optional.of(bySimple.get(0));
            }
            return Optional.empty();
        }

        private static void add(Map<String, List<CodeRegion>> map, String key, CodeRegion region) {
            map.computeIfAbsent(key, ignored -> new ArrayList<>()).add(region);
        }
    }

    private static Optional<CodeRegion> linkedBodyRegion(List<CodeRegion> regions, CodeRegion methodRegion) {
        String bodyId = methodRegion.regionId() + ":body";
        return regions.stream()
                .filter(r -> r.kind() == RegionKind.METHOD_BODY_REGION)
                .filter(r -> r.regionId().equals(bodyId))
                .findFirst();
    }

    private static String methodKey(RawToolMethod method) {
        String declaring = method.rawDeclaringClass();
        String signature = method.rawMethodSignature();
        if (signature.contains(".")) {
            return signature;
        }
        if (declaring == null || declaring.isBlank()) {
            return signature;
        }
        return declaring + "." + signature;
    }

    private record BlockIndex(
            Map<String, RawToolBlock> blocks,
            Map<String, String> methodByBlockId
    ) {
    }

    private record MethodPairKey(String leftMethodKey, String rightMethodKey) {
    }

    private static final class MethodDistanceAccumulator {
        private double sum;
        private int count;
        private double min = Double.POSITIVE_INFINITY;

        void add(double distance) {
            sum += distance;
            count++;
            min = Math.min(min, distance);
        }

        double score() {
            if (count == 0) {
                return 0.0;
            }
            double avg = sum / count;
            double closeness = 1.0 / (1.0 + avg);
            double bestCloseness = 1.0 / (1.0 + min);
            return 0.65 * closeness + 0.35 * bestCloseness;
        }
    }
}
