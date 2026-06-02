package com.ziqi.codesim.semantic.discovre;

import com.ziqi.codesim.semantic.model.AnalyzedMethod;
import com.ziqi.codesim.semantic.model.BasicBlockUnit;
import com.ziqi.codesim.semantic.model.ControlFlowEdge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        int budget = 16 * Math.max(left.cfg().blocks().size(), right.cfg().blocks().size());
        SearchState state = new SearchState(left, right, budget);
        search(candidatePairs, 0, new ArrayList<>(), new HashSet<>(), new HashSet<>(), state);
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

    private void search(
            List<DiscovreBlockPair> candidates,
            int start,
            List<DiscovreBlockPair> current,
            Set<String> usedLeft,
            Set<String> usedRight,
            SearchState state) {
        if (state.iterations >= state.budget) {
            updateBest(current, state);
            return;
        }
        state.iterations++;
        updateBest(current, state);
        for (int i = start; i < candidates.size(); i++) {
            DiscovreBlockPair pair = candidates.get(i);
            if (usedLeft.contains(pair.leftBlockId()) || usedRight.contains(pair.rightBlockId())) {
                continue;
            }
            if (!preservesExistingEdges(pair, current, state.leftEdges, state.rightEdges)) {
                continue;
            }
            current.add(pair);
            usedLeft.add(pair.leftBlockId());
            usedRight.add(pair.rightBlockId());
            search(candidates, i + 1, current, usedLeft, usedRight, state);
            current.remove(current.size() - 1);
            usedLeft.remove(pair.leftBlockId());
            usedRight.remove(pair.rightBlockId());
            if (state.iterations >= state.budget) {
                return;
            }
        }
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
