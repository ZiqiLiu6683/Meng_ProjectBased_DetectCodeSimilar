package com.ziqi.codesim.semantic.discovre;

import com.ziqi.codesim.semantic.model.AnalyzedMethod;
import com.ziqi.codesim.semantic.model.BasicBlockUnit;
import com.ziqi.codesim.semantic.model.ControlFlowEdge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * discovRE structural similarity via an approximate maximum common subgraph (MCS)
 * over the two control-flow graphs (NDSS'16, section III-C2).
 *
 * <p>The search follows McGregor's formulation: the left graph's basic blocks are
 * mapped one at a time to a compatible right block (or to "no match"), maintaining
 * neighbourhood/edge consistency with the blocks already mapped. The basic-block
 * distance d_BB is used both to prune incompatible pairs (threshold 0.5) and to
 * order the expansion so the closest equivalence is tried first. A branch-and-bound
 * cut and an iteration budget keep the otherwise exponential search tractable, which
 * yields the paper's relaxed maximal common subgraph (mCS). The final distance uses
 * the paper's mCS formula: 1 - (|common| - sum d_BB) / max(|G1|, |G2|).
 */
public class DiscovreCfgMatcher {
    private static final double BLOCK_DISTANCE_PRUNE_THRESHOLD = 0.5;
    private final DiscovreBlockFeatureExtractor featureExtractor;
    private final DiscovreBlockDistance blockDistance;

    public DiscovreCfgMatcher() {
        this(new DiscovreBlockFeatureExtractor(), new DiscovreBlockDistance());
    }

    public DiscovreCfgMatcher(
            DiscovreBlockFeatureExtractor featureExtractor,
            DiscovreBlockDistance blockDistance) {
        this.featureExtractor = featureExtractor;
        this.blockDistance = blockDistance;
    }

    public DiscovreCfgMatchResult match(AnalyzedMethod left, AnalyzedMethod right) {
        return match(left, right, Map.of());
    }

    public DiscovreCfgMatchResult match(
            AnalyzedMethod left,
            AnalyzedMethod right,
            Map<String, Set<String>> allowedBlockPairs) {
        List<DiscovreBlockPair> candidatePairs = candidatePairs(left, right, allowedBlockPairs);
        // McGregor maps the left (G1) blocks one by one to a compatible right (G2) block
        // or to "no match", so group the pruned candidate pairs by left block. candidatePairs
        // is already sorted by ascending d_BB, so each per-left list keeps that order and the
        // closest equivalence is expanded first.
        List<String> leftOrder = new ArrayList<>();
        for (BasicBlockUnit leftBlock : left.cfg().blocks()) {
            leftOrder.add(leftBlock.blockId());
        }
        Map<String, List<DiscovreBlockPair>> candidatesByLeft = new LinkedHashMap<>();
        for (DiscovreBlockPair pair : candidatePairs) {
            candidatesByLeft.computeIfAbsent(pair.leftBlockId(), ignored -> new ArrayList<>()).add(pair);
        }
        int budget = 16 * Math.max(left.cfg().blocks().size(), right.cfg().blocks().size());
        SearchState state = new SearchState(left, right, budget);
        mcGregorExpand(leftOrder, 0, candidatesByLeft, new ArrayList<>(), new HashSet<>(), state);
        List<DiscovreBlockPair> bestPairs = state.bestPairs;
        double totalBlockDistance = bestPairs.stream().mapToDouble(DiscovreBlockPair::blockDistance).sum();
        int denominator = Math.max(left.cfg().blocks().size(), right.cfg().blocks().size());
        double common = bestPairs.size() - totalBlockDistance;
        double distance = denominator == 0 ? 0.0 : 1.0 - (common / denominator);
        distance = clamp01(distance);
        return new DiscovreCfgMatchResult(
                left.methodId(),
                right.methodId(),
                distance,
                1.0 - distance,
                bestPairs.size(),
                candidatePairs.size(),
                state.iterations,
                budget,
                bestPairs
        );
    }

    private List<DiscovreBlockPair> candidatePairs(
            AnalyzedMethod left,
            AnalyzedMethod right,
            Map<String, Set<String>> allowedBlockPairs) {
        Map<String, DiscovreBlockFeatures> leftFeatures = blockFeatures(left);
        Map<String, DiscovreBlockFeatures> rightFeatures = blockFeatures(right);
        List<DiscovreBlockPair> pairs = new ArrayList<>();
        boolean constrained = !allowedBlockPairs.isEmpty();
        for (BasicBlockUnit leftBlock : left.cfg().blocks()) {
            Set<String> allowedRightBlocks = allowedBlockPairs.get(leftBlock.blockId());
            if (constrained && allowedRightBlocks == null) {
                continue;
            }
            for (BasicBlockUnit rightBlock : right.cfg().blocks()) {
                if (allowedRightBlocks != null && !allowedRightBlocks.contains(rightBlock.blockId())) {
                    continue;
                }
                double distance = blockDistance.distance(
                        leftFeatures.get(leftBlock.blockId()),
                        rightFeatures.get(rightBlock.blockId())
                );
                if (distance <= BLOCK_DISTANCE_PRUNE_THRESHOLD) {
                    pairs.add(new DiscovreBlockPair(leftBlock.blockId(), rightBlock.blockId(), distance));
                }
            }
        }
        pairs.sort(Comparator
                .comparingDouble(DiscovreBlockPair::blockDistance)
                .thenComparing(DiscovreBlockPair::leftBlockId)
                .thenComparing(DiscovreBlockPair::rightBlockId));
        return pairs;
    }

    private Map<String, DiscovreBlockFeatures> blockFeatures(AnalyzedMethod method) {
        Map<String, DiscovreBlockFeatures> features = new HashMap<>();
        for (BasicBlockUnit block : method.cfg().blocks()) {
            features.put(block.blockId(), featureExtractor.extract(block));
        }
        return features;
    }

    private void mcGregorExpand(
            List<String> leftOrder,
            int index,
            Map<String, List<DiscovreBlockPair>> candidatesByLeft,
            List<DiscovreBlockPair> current,
            Set<String> usedRight,
            SearchState state) {
        if (state.iterations >= state.budget) {
            updateBest(current, state);
            return;
        }
        state.iterations++;
        updateBest(current, state);
        if (index >= leftOrder.size()) {
            return;
        }
        // Branch-and-bound: each remaining left block can add at most 1.0 to the mCS score,
        // so abandon this branch if even a perfect completion cannot beat the best so far.
        double optimistic = mcsScore(current) + (leftOrder.size() - index);
        if (optimistic <= mcsScore(state.bestPairs)) {
            return;
        }
        String leftBlock = leftOrder.get(index);
        // Option 1: map this left block to a compatible right block (closest d_BB first).
        for (DiscovreBlockPair pair : candidatesByLeft.getOrDefault(leftBlock, List.of())) {
            if (usedRight.contains(pair.rightBlockId())) {
                continue;
            }
            if (!preservesExistingEdges(pair, current, state.leftEdges, state.rightEdges)) {
                continue;
            }
            current.add(pair);
            usedRight.add(pair.rightBlockId());
            mcGregorExpand(leftOrder, index + 1, candidatesByLeft, current, usedRight, state);
            current.remove(current.size() - 1);
            usedRight.remove(pair.rightBlockId());
            if (state.iterations >= state.budget) {
                return;
            }
        }
        // Option 2: leave this left block unmatched (McGregor's null assignment).
        mcGregorExpand(leftOrder, index + 1, candidatesByLeft, current, usedRight, state);
    }

    private static boolean preservesExistingEdges(
            DiscovreBlockPair next,
            List<DiscovreBlockPair> current,
            Set<String> leftEdges,
            Set<String> rightEdges) {
        for (DiscovreBlockPair existing : current) {
            if (leftEdges.contains(edgeKey(existing.leftBlockId(), next.leftBlockId()))
                    && !rightEdges.contains(edgeKey(existing.rightBlockId(), next.rightBlockId()))) {
                return false;
            }
            if (leftEdges.contains(edgeKey(next.leftBlockId(), existing.leftBlockId()))
                    && !rightEdges.contains(edgeKey(next.rightBlockId(), existing.rightBlockId()))) {
                return false;
            }
        }
        return true;
    }

    private static void updateBest(List<DiscovreBlockPair> current, SearchState state) {
        double currentScore = mcsScore(current);
        double bestScore = mcsScore(state.bestPairs);
        if (currentScore > bestScore) {
            state.bestPairs = List.copyOf(current);
        }
    }

    private static double mcsScore(List<DiscovreBlockPair> pairs) {
        return pairs.size() - pairs.stream().mapToDouble(DiscovreBlockPair::blockDistance).sum();
    }

    private static Set<String> edgeKeys(List<ControlFlowEdge> edges) {
        Set<String> keys = new HashSet<>();
        for (ControlFlowEdge edge : edges) {
            keys.add(edgeKey(edge.fromBlockId(), edge.toBlockId()));
        }
        return keys;
    }

    private static String edgeKey(String from, String to) {
        return from + "->" + to;
    }

    private static double clamp01(double value) {
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private static class SearchState {
        final AnalyzedMethod left;
        final AnalyzedMethod right;
        final Set<String> leftEdges;
        final Set<String> rightEdges;
        final int budget;
        int iterations;
        List<DiscovreBlockPair> bestPairs = List.of();

        SearchState(AnalyzedMethod left, AnalyzedMethod right, int budget) {
            this.left = left;
            this.right = right;
            this.leftEdges = edgeKeys(left.cfg().edges());
            this.rightEdges = edgeKeys(right.cfg().edges());
            this.budget = budget;
        }
    }
}
