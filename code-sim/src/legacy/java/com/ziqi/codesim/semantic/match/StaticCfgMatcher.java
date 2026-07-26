package com.ziqi.codesim.semantic.match;

import com.ziqi.codesim.semantic.feature.BlockNumericFeatures;
import com.ziqi.codesim.semantic.feature.MethodNumericFeatures;
import com.ziqi.codesim.semantic.feature.NumericFeatureDistance;
import com.ziqi.codesim.semantic.feature.SemanticFeatureExtractor;
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

public class StaticCfgMatcher {
    private final SemanticFeatureExtractor featureExtractor;
    private final NumericFeatureDistance distance;

    public StaticCfgMatcher() {
        this(new SemanticFeatureExtractor(), new NumericFeatureDistance());
    }

    public StaticCfgMatcher(
            SemanticFeatureExtractor featureExtractor,
            NumericFeatureDistance distance) {
        this.featureExtractor = featureExtractor;
        this.distance = distance;
    }

    public MethodCfgMatchResult match(AnalyzedMethod left, AnalyzedMethod right) {
        MethodNumericFeatures leftMethodFeatures = featureExtractor.methodFeatures(left);
        MethodNumericFeatures rightMethodFeatures = featureExtractor.methodFeatures(right);
        double methodDistance = distance.methodDistance(leftMethodFeatures, rightMethodFeatures);
        List<BlockMatch> blockMatches = matchBlocks(left, right);
        double blockSimilarity = weightedBlockSimilarity(left, right, blockMatches);
        double edgePreservation = edgePreservation(left.cfg().edges(), right.cfg().edges(), blockMatches);
        double overall = (0.30 * (1.0 - methodDistance))
                + (0.45 * blockSimilarity)
                + (0.25 * edgePreservation);
        return new MethodCfgMatchResult(
                left.methodId(),
                right.methodId(),
                methodDistance,
                blockSimilarity,
                edgePreservation,
                clamp01(overall),
                blockMatches
        );
    }

    private List<BlockMatch> matchBlocks(AnalyzedMethod left, AnalyzedMethod right) {
        List<BlockPairCandidate> candidates = new ArrayList<>();
        for (BasicBlockUnit leftBlock : left.cfg().blocks()) {
            BlockNumericFeatures leftFeatures = featureExtractor.blockFeatures(leftBlock, left.cfg().edges());
            for (BasicBlockUnit rightBlock : right.cfg().blocks()) {
                BlockNumericFeatures rightFeatures = featureExtractor.blockFeatures(rightBlock, right.cfg().edges());
                double blockDistance = distance.blockDistance(leftFeatures, rightFeatures);
                candidates.add(new BlockPairCandidate(
                        leftBlock.blockId(),
                        rightBlock.blockId(),
                        blockDistance,
                        1.0 - blockDistance
                ));
            }
        }
        candidates.sort(Comparator
                .comparingDouble(BlockPairCandidate::similarity)
                .reversed()
                .thenComparing(BlockPairCandidate::leftBlockId)
                .thenComparing(BlockPairCandidate::rightBlockId));

        Set<String> usedLeft = new HashSet<>();
        Set<String> usedRight = new HashSet<>();
        List<BlockMatch> matches = new ArrayList<>();
        for (BlockPairCandidate candidate : candidates) {
            if (usedLeft.contains(candidate.leftBlockId()) || usedRight.contains(candidate.rightBlockId())) {
                continue;
            }
            usedLeft.add(candidate.leftBlockId());
            usedRight.add(candidate.rightBlockId());
            matches.add(new BlockMatch(
                    candidate.leftBlockId(),
                    candidate.rightBlockId(),
                    candidate.distance(),
                    candidate.similarity()
            ));
        }
        return matches;
    }

    private static double weightedBlockSimilarity(
            AnalyzedMethod left,
            AnalyzedMethod right,
            List<BlockMatch> matches) {
        int denominator = Math.max(left.cfg().blocks().size(), right.cfg().blocks().size());
        if (denominator == 0) return 1.0;
        double sum = 0.0;
        for (BlockMatch match : matches) {
            sum += match.similarity();
        }
        return clamp01(sum / denominator);
    }

    private static double edgePreservation(
            List<ControlFlowEdge> leftEdges,
            List<ControlFlowEdge> rightEdges,
            List<BlockMatch> matches) {
        int denominator = Math.max(leftEdges.size(), rightEdges.size());
        if (denominator == 0) return 1.0;

        Map<String, String> blockMap = new HashMap<>();
        for (BlockMatch match : matches) {
            blockMap.put(match.leftBlockId(), match.rightBlockId());
        }
        Set<String> rightEdgeKeys = new HashSet<>();
        for (ControlFlowEdge edge : rightEdges) {
            rightEdgeKeys.add(edgeKey(edge.fromBlockId(), edge.toBlockId(), edge.kind()));
        }

        int preserved = 0;
        for (ControlFlowEdge edge : leftEdges) {
            String mappedFrom = blockMap.get(edge.fromBlockId());
            String mappedTo = blockMap.get(edge.toBlockId());
            if (mappedFrom == null || mappedTo == null) {
                continue;
            }
            if (rightEdgeKeys.contains(edgeKey(mappedFrom, mappedTo, edge.kind()))) {
                preserved++;
            }
        }
        return clamp01((double) preserved / denominator);
    }

    private static String edgeKey(String from, String to, String kind) {
        return from + "->" + to + ":" + kind;
    }

    private static double clamp01(double value) {
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private record BlockPairCandidate(
            String leftBlockId,
            String rightBlockId,
            double distance,
            double similarity
    ) {
    }
}
