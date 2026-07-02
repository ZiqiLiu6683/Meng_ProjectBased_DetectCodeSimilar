package com.ziqi.codesim.region.seed;

import com.ziqi.codesim.region.model.NodeDescriptor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Finds cross-file candidate seed node pairs from two files' node descriptors. Two layers, merged:
 *
 * <ul>
 *   <li><b>Bucket layer</b> (threshold-free, high precision): group nodes by exact channel hashes
 *       and emit cross-file pairs that share a bucket. Channels: the semantic value hash (non-zero
 *       only) and the Weisfeiler-Lehman label at the top radii. Bucket membership is exact, so
 *       there is no distance cutoff.</li>
 *   <li><b>KNN layer</b> (recall): for each node take the top-k opposite-file nodes by Jaccard
 *       similarity over the multiset of WL labels across radii, in both directions. This recovers
 *       edited (Type-3) anchors whose outer-radius labels diverge but inner ones still agree. The
 *       only knob is k (a count, not a similarity threshold).</li>
 * </ul>
 *
 * <p>Same-file pairs are impossible by construction: matching only ever happens between the two
 * descriptor maps. Output is sorted strongest-first; strength ranks anchors, it is not an
 * accept/reject threshold. Pure Java.
 */
public final class SeedMatcher {

    private static final double STRENGTH_SEMANTIC = 1.0;
    private static final double STRENGTH_WL_TOP = 0.9;
    private static final double STRENGTH_WL_NEXT = 0.75;
    private static final double STRENGTH_KNN_SCALE = 0.6;

    private final int knnK;
    private final boolean useKnn;

    /**
     * Default matcher: bucket-only. An ablation (KnnAblationTest, 2026-07-02) showed the KNN recall
     * layer changed no detection on the structural corpus — region growth expands from any single
     * exact-hash anchor, making KNN's extra seeds redundant at region level. KNN is kept but off by
     * default so we stop paying its O(|A|x|B|) cost; re-enable with {@link #withKnn()} if a future
     * corpus proves it earns its keep.
     */
    public SeedMatcher() {
        this(4, false);
    }

    public SeedMatcher(int knnK) {
        this(knnK, true);
    }

    public SeedMatcher(int knnK, boolean useKnn) {
        if (knnK < 1) {
            throw new IllegalArgumentException("knnK must be >= 1: " + knnK);
        }
        this.knnK = knnK;
        this.useKnn = useKnn;
    }

    /** Bucket-only matcher (exact-hash seeds, no quadratic KNN recall layer) -- same as the default. */
    public static SeedMatcher bucketOnly() {
        return new SeedMatcher(4, false);
    }

    /** Bucket + KNN recall matcher (adds the quadratic recall layer). */
    public static SeedMatcher withKnn() {
        return new SeedMatcher(4, true);
    }

    public List<SeedPair> match(Map<Integer, NodeDescriptor> left, Map<Integer, NodeDescriptor> right) {
        Map<PairKey, Accumulator> accumulators = new HashMap<>();
        bucketBySemantic(left, right, accumulators);
        bucketByWl(left, right, accumulators);
        if (useKnn) {
            knn(left, right, accumulators);
        }

        List<SeedPair> seeds = new ArrayList<>(accumulators.size());
        for (Map.Entry<PairKey, Accumulator> entry : accumulators.entrySet()) {
            seeds.add(new SeedPair(entry.getKey().left, entry.getKey().right,
                    entry.getValue().strength, entry.getValue().evidence));
        }
        seeds.sort(Comparator.comparingDouble(SeedPair::strength).reversed()
                .thenComparingInt(SeedPair::leftNodeId)
                .thenComparingInt(SeedPair::rightNodeId));
        return seeds;
    }

    private static void bucketBySemantic(Map<Integer, NodeDescriptor> left,
                                         Map<Integer, NodeDescriptor> right,
                                         Map<PairKey, Accumulator> accumulators) {
        Map<Long, List<Integer>> leftByHash = new HashMap<>();
        for (NodeDescriptor descriptor : left.values()) {
            if (descriptor.semanticValueHash() != 0L) {
                leftByHash.computeIfAbsent(descriptor.semanticValueHash(), k -> new ArrayList<>())
                        .add(descriptor.nodeId());
            }
        }
        for (NodeDescriptor rightDescriptor : right.values()) {
            if (rightDescriptor.semanticValueHash() == 0L) {
                continue;
            }
            for (int leftId : leftByHash.getOrDefault(rightDescriptor.semanticValueHash(), List.of())) {
                record(accumulators, leftId, rightDescriptor.nodeId(), STRENGTH_SEMANTIC, "SEM");
            }
        }
    }

    private static void bucketByWl(Map<Integer, NodeDescriptor> left,
                                   Map<Integer, NodeDescriptor> right,
                                   Map<PairKey, Accumulator> accumulators) {
        int maxRadius = maxRadius(left);
        if (maxRadius < 0) {
            return;
        }
        // Bucket on the two most specific radii only; lower-radius agreement is handled by the
        // Jaccard KNN layer, which keeps these buckets precise.
        bucketByWlRadius(left, right, accumulators, maxRadius, STRENGTH_WL_TOP);
        if (maxRadius >= 1) {
            bucketByWlRadius(left, right, accumulators, maxRadius - 1, STRENGTH_WL_NEXT);
        }
    }

    private static void bucketByWlRadius(Map<Integer, NodeDescriptor> left,
                                         Map<Integer, NodeDescriptor> right,
                                         Map<PairKey, Accumulator> accumulators,
                                         int radius, double strength) {
        Map<Long, List<Integer>> leftByHash = new HashMap<>();
        for (NodeDescriptor descriptor : left.values()) {
            leftByHash.computeIfAbsent(descriptor.wlHashAt(radius), k -> new ArrayList<>())
                    .add(descriptor.nodeId());
        }
        for (NodeDescriptor rightDescriptor : right.values()) {
            for (int leftId : leftByHash.getOrDefault(rightDescriptor.wlHashAt(radius), List.of())) {
                record(accumulators, leftId, rightDescriptor.nodeId(), strength, "WL@" + radius);
            }
        }
    }

    private void knn(Map<Integer, NodeDescriptor> left,
                     Map<Integer, NodeDescriptor> right,
                     Map<PairKey, Accumulator> accumulators) {
        Map<Integer, Set<Long>> leftSets = wlLabelSets(left);
        Map<Integer, Set<Long>> rightSets = wlLabelSets(right);
        knnDirected(leftSets, rightSets, accumulators, true);
        knnDirected(rightSets, leftSets, accumulators, false);
    }

    /** For each source node, emit its top-k Jaccard neighbours in the target file. */
    private void knnDirected(Map<Integer, Set<Long>> source,
                             Map<Integer, Set<Long>> target,
                             Map<PairKey, Accumulator> accumulators,
                             boolean sourceIsLeft) {
        for (Map.Entry<Integer, Set<Long>> sourceEntry : source.entrySet()) {
            List<Neighbour> neighbours = new ArrayList<>();
            for (Map.Entry<Integer, Set<Long>> targetEntry : target.entrySet()) {
                double similarity = jaccard(sourceEntry.getValue(), targetEntry.getValue());
                if (similarity > 0.0) {
                    neighbours.add(new Neighbour(targetEntry.getKey(), similarity));
                }
            }
            neighbours.sort(Comparator.comparingDouble((Neighbour n) -> n.similarity).reversed()
                    .thenComparingInt(n -> n.nodeId));
            for (int i = 0; i < Math.min(knnK, neighbours.size()); i++) {
                Neighbour neighbour = neighbours.get(i);
                int leftId = sourceIsLeft ? sourceEntry.getKey() : neighbour.nodeId;
                int rightId = sourceIsLeft ? neighbour.nodeId : sourceEntry.getKey();
                record(accumulators, leftId, rightId,
                        STRENGTH_KNN_SCALE * neighbour.similarity,
                        "KNN:" + String.format(java.util.Locale.ROOT, "%.2f", neighbour.similarity));
            }
        }
    }

    private static Map<Integer, Set<Long>> wlLabelSets(Map<Integer, NodeDescriptor> descriptors) {
        Map<Integer, Set<Long>> sets = new LinkedHashMap<>();
        for (NodeDescriptor descriptor : descriptors.values()) {
            sets.put(descriptor.nodeId(), new HashSet<>(descriptor.wlHashes()));
        }
        return sets;
    }

    private static double jaccard(Set<Long> a, Set<Long> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        Set<Long> smaller = a.size() <= b.size() ? a : b;
        Set<Long> larger = smaller == a ? b : a;
        for (long value : smaller) {
            if (larger.contains(value)) {
                intersection++;
            }
        }
        int union = a.size() + b.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private static int maxRadius(Map<Integer, NodeDescriptor> descriptors) {
        for (NodeDescriptor descriptor : descriptors.values()) {
            return descriptor.wlHashes().size() - 1;
        }
        return -1;
    }

    private static void record(Map<PairKey, Accumulator> accumulators,
                               int leftId, int rightId, double strength, String evidence) {
        Accumulator accumulator = accumulators.computeIfAbsent(
                new PairKey(leftId, rightId), k -> new Accumulator());
        accumulator.strength = Math.max(accumulator.strength, strength);
        accumulator.evidence.add(evidence);
    }

    private record PairKey(int left, int right) {
    }

    private record Neighbour(int nodeId, double similarity) {
    }

    private static final class Accumulator {
        private double strength;
        private final Set<String> evidence = new TreeSet<>();
    }
}
