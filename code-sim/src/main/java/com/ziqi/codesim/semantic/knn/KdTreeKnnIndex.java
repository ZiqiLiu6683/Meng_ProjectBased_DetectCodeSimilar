package com.ziqi.codesim.semantic.knn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/**
 * Exact k-d tree nearest-neighbour index, the data structure discovRE uses for
 * its numeric pre-filter (paper section III-B2). Points are standardized feature
 * vectors embedded in a fixed dimension ordering (the sorted union of feature
 * keys). Search prunes the far branch whenever the per-axis gap already exceeds
 * the current k-th best distance, so it returns exactly the same top-k as
 * {@link ExactKnnIndex} -- including identical tie-breaking at equal distances,
 * because eviction and pruning both order by (distance, candidateId) -- using the
 * same union-of-keys Euclidean metric. Verified equal over randomized tie-heavy
 * inputs.
 *
 * <p>This is an <em>exact</em> tree, not FLANN's randomized multi-tree
 * approximation; it documents the index choice without changing results.
 */
public class KdTreeKnnIndex implements KnnIndex {
    private final List<StandardizedFeatureVector> vectors;
    private final List<String> dimensions;
    private final Node root;

    public KdTreeKnnIndex(List<StandardizedFeatureVector> vectors) {
        this.vectors = List.copyOf(vectors);
        TreeSet<String> keys = new TreeSet<>();
        for (StandardizedFeatureVector vector : this.vectors) {
            keys.addAll(vector.values().keySet());
        }
        this.dimensions = List.copyOf(keys);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < this.vectors.size(); i++) {
            indices.add(i);
        }
        this.root = build(indices, 0);
    }

    @Override
    public List<BlockCandidate> query(StandardizedFeatureVector query, int k) {
        // Max-heap on (distance, candidateId): poll() evicts the largest, so the heap keeps
        // the k smallest by (distance, candidateId) -- exactly what ExactKnnIndex returns after
        // its sort, making the two backends agree even when several candidates are equidistant.
        PriorityQueue<Neighbor> best = new PriorityQueue<>(
                Comparator.comparingDouble((Neighbor neighbor) -> neighbor.distance)
                        .thenComparing(neighbor -> neighbor.candidateId)
                        .reversed());
        search(root, query, k, best);

        List<BlockCandidate> candidates = new ArrayList<>();
        for (Neighbor neighbor : best) {
            StandardizedFeatureVector candidate = vectors.get(neighbor.index);
            candidates.add(new BlockCandidate(
                    query.itemId(),
                    candidate.itemId(),
                    query.view(),
                    neighbor.distance,
                    sharedContributions(query, candidate)
            ));
        }
        candidates.sort(Comparator
                .comparingDouble(BlockCandidate::distance)
                .thenComparing(BlockCandidate::candidateBlockId));
        if (candidates.size() <= k) {
            return candidates;
        }
        return List.copyOf(candidates.subList(0, k));
    }

    private Node build(List<Integer> indices, int depth) {
        if (indices.isEmpty()) {
            return null;
        }
        if (dimensions.isEmpty()) {
            // No usable dimensions (e.g. all features had zero variance): chain the
            // points so the search still visits every one of them.
            Node node = new Node(indices.get(0), 0);
            node.left = build(new ArrayList<>(indices.subList(1, indices.size())), depth + 1);
            return node;
        }
        int axis = depth % dimensions.size();
        indices.sort(Comparator.comparingDouble(index -> coordinate(vectors.get(index), axis)));
        int mid = indices.size() / 2;
        Node node = new Node(indices.get(mid), axis);
        node.left = build(new ArrayList<>(indices.subList(0, mid)), depth + 1);
        node.right = build(new ArrayList<>(indices.subList(mid + 1, indices.size())), depth + 1);
        return node;
    }

    private void search(Node node, StandardizedFeatureVector query, int k, PriorityQueue<Neighbor> best) {
        if (node == null) {
            return;
        }
        StandardizedFeatureVector point = vectors.get(node.index);
        if (!point.itemId().equals(query.itemId())) {
            double distance = euclidean(query, point);
            best.add(new Neighbor(node.index, distance, point.itemId()));
            if (best.size() > k) {
                best.poll();
            }
        }
        if (dimensions.isEmpty()) {
            search(node.left, query, k, best);
            return;
        }
        double diff = coordinate(query, node.axis) - coordinate(point, node.axis);
        Node near = diff <= 0 ? node.left : node.right;
        Node far = diff <= 0 ? node.right : node.left;
        search(near, query, k, best);
        // The closest possible point in the far subtree differs from the query by at least
        // |diff| on this axis. Descend if that could beat OR tie the worst best-k distance;
        // the tie case (<=) is needed so an equidistant, lower-id candidate can still replace
        // a larger-id one and keep parity with ExactKnnIndex.
        if (best.size() < k || Math.abs(diff) <= best.peek().distance) {
            search(far, query, k, best);
        }
    }

    private double coordinate(StandardizedFeatureVector vector, int axis) {
        return vector.values().getOrDefault(dimensions.get(axis), 0.0);
    }

    private static double euclidean(StandardizedFeatureVector left, StandardizedFeatureVector right) {
        Set<String> features = new HashSet<>();
        features.addAll(left.values().keySet());
        features.addAll(right.values().keySet());
        double sum = 0.0;
        for (String feature : features) {
            double delta = left.values().getOrDefault(feature, 0.0) - right.values().getOrDefault(feature, 0.0);
            sum += delta * delta;
        }
        return Math.sqrt(sum);
    }

    private static List<FeatureContribution> sharedContributions(
            StandardizedFeatureVector query,
            StandardizedFeatureVector candidate) {
        List<FeatureContribution> shared = new ArrayList<>();
        for (String feature : query.provenance().keySet()) {
            if (!candidate.provenance().containsKey(feature)) {
                continue;
            }
            shared.addAll(query.provenance().get(feature));
            shared.addAll(candidate.provenance().get(feature));
        }
        return shared;
    }

    private static final class Node {
        final int index;
        final int axis;
        Node left;
        Node right;

        Node(int index, int axis) {
            this.index = index;
            this.axis = axis;
        }
    }

    private static final class Neighbor {
        final int index;
        final double distance;
        final String candidateId;

        Neighbor(int index, double distance, String candidateId) {
            this.index = index;
            this.distance = distance;
            this.candidateId = candidateId;
        }
    }
}
