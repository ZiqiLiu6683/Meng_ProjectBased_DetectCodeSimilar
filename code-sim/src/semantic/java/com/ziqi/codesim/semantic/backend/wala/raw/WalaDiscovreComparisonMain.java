package com.ziqi.codesim.semantic.backend.wala.raw;

import com.ziqi.codesim.semantic.backend.wala.WalaAnalysisBackend;
import com.ziqi.codesim.semantic.discovre.DiscovreCfgMatchResult;
import com.ziqi.codesim.semantic.discovre.DiscovreCfgMatcher;
import com.ziqi.codesim.semantic.knn.BlockCandidate;
import com.ziqi.codesim.semantic.knn.BlockCandidateSelector;
import com.ziqi.codesim.semantic.knn.KnnFeatureView;
import com.ziqi.codesim.semantic.knn.MethodNumericKnnPreFilter;
import com.ziqi.codesim.semantic.match.MethodCfgMatchResult;
import com.ziqi.codesim.semantic.match.StaticCfgMatcher;
import com.ziqi.codesim.semantic.model.AnalyzedMethod;
import com.ziqi.codesim.semantic.raw.RawToolBlock;
import com.ziqi.codesim.semantic.raw.RawToolClass;
import com.ziqi.codesim.semantic.raw.RawToolMethod;
import com.ziqi.codesim.semantic.raw.RawToolProgram;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class WalaDiscovreComparisonMain {
    private static final Set<String> RAW_FILTERED_KEEP = Set.of(
            "block.rawExceptionalSuccessor",
            "block.rawNormalSuccessor",
            "block.rawPredecessor",
            "instruction.rawDeclaredTargetText",
            "instruction.rawDefValue",
            "instruction.rawInstructionClassName",
            "instruction.rawInstructionIndex",
            "instruction.rawInstructionText",
            "instruction.rawUseValue",
            "method.rawCFGText"
    );

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 8) {
            System.err.println("Usage: WalaDiscovreComparisonMain <left-class-dir> <right-class-dir> <output-csv> [view] [blockTopK] [methodTopK] [methodCandidateSource:numeric|block] [structuralScorer:discovre|mode1]");
            System.exit(2);
        }
        Path leftInput = Path.of(args[0]);
        Path rightInput = Path.of(args[1]);
        Path output = Path.of(args[2]);
        KnnFeatureView view = args.length >= 4 ? KnnFeatureView.valueOf(args[3]) : KnnFeatureView.HYBRID_NUMERIC_HASH;
        int blockTopK = args.length >= 5 ? Integer.parseInt(args[4]) : 8;
        int methodTopK = args.length >= 6 ? Integer.parseInt(args[5]) : blockTopK;
        // discovRE pre-filter source for the method candidate stage:
        //   numeric (default, paper section III-B): function-level numeric kNN
        //   block (legacy): method candidates bootstrapped from block-level kNN
        String methodCandidateSource = args.length >= 7 ? args[6].toLowerCase(java.util.Locale.ROOT) : "numeric";
        // Structural scorer for the second (expensive) stage:
        //   discovre (default): approximate maximum common subgraph (MCS) over the CFG
        //   mode1: StaticCfgMatcher's combined numeric + block + edge-preservation score
        String structuralScorer = args.length >= 8 ? args[7].toLowerCase(java.util.Locale.ROOT) : "discovre";

        WalaAnalysisBackend backend = new WalaAnalysisBackend();
        Map<String, AnalyzedMethod> leftMethods = methodsBySignature(backend.analyze(leftInput).methods());
        Map<String, AnalyzedMethod> rightMethods = methodsBySignature(backend.analyze(rightInput).methods());

        WalaRawSnapshotExtractor extractor = new WalaRawSnapshotExtractor();
        Map<String, RawToolBlock> leftRawBlocks = flattenRawBlocks("left", extractor.extract(leftInput));
        Map<String, RawToolBlock> rightRawBlocks = flattenRawBlocks("right", extractor.extract(rightInput));
        Map<String, List<BlockCandidate>> knnCandidates = new BlockCandidateSelector().select(
                leftRawBlocks,
                rightRawBlocks,
                RAW_FILTERED_KEEP,
                view,
                blockTopK
        );
        Map<String, List<MethodCandidate>> methodCandidates = "block".equals(methodCandidateSource)
                ? methodCandidates(leftMethods.keySet(), rightMethods.keySet(), knnCandidates, methodTopK)
                : numericMethodCandidates(leftMethods, rightMethods, methodTopK);

        DiscovreCfgMatcher matcher = new DiscovreCfgMatcher();
        StaticCfgMatcher mode1Matcher = new StaticCfgMatcher();
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("leftMethodSignature,rightMethodSignature,view,blockTopK,methodTopK,methodCandidateSource,methodCandidateScore,structuralScorer,"
                    + "exhaustiveSimilarity,constrainedSimilarity,similarityDelta,"
                    + "exhaustiveDistance,constrainedDistance,exhaustiveCandidatePairs,constrainedCandidatePairs,"
                    + "candidateReduction,exhaustiveMatchedBlocks,constrainedMatchedBlocks");
            writer.newLine();
            for (String leftSignature : leftMethods.keySet()) {
                AnalyzedMethod left = leftMethods.get(leftSignature);
                for (MethodCandidate methodCandidate : methodCandidates.getOrDefault(leftSignature, List.of())) {
                    AnalyzedMethod right = rightMethods.get(methodCandidate.rightMethodSignature());
                    if (right == null) {
                        continue;
                    }
                    StructuralScores scores = scoreStructural(
                            structuralScorer,
                            left,
                            right,
                            allowedPairsFor(leftSignature, methodCandidate.rightMethodSignature(), knnCandidates),
                            matcher,
                            mode1Matcher
                    );
                    double reduction = scores.exhaustiveCandidatePairs() == 0
                            ? 0.0
                            : 1.0 - ((double) scores.constrainedCandidatePairs() / scores.exhaustiveCandidatePairs());
                    writer.write(csv(leftSignature));
                    writer.write(',');
                    writer.write(csv(methodCandidate.rightMethodSignature()));
                    writer.write(',');
                    writer.write(view.name());
                    writer.write(',');
                    writer.write(Integer.toString(blockTopK));
                    writer.write(',');
                    writer.write(Integer.toString(methodTopK));
                    writer.write(',');
                    writer.write(methodCandidateSource);
                    writer.write(',');
                    writer.write(Double.toString(methodCandidate.score()));
                    writer.write(',');
                    writer.write(structuralScorer);
                    writer.write(',');
                    writer.write(Double.toString(scores.exhaustiveSimilarity()));
                    writer.write(',');
                    writer.write(Double.toString(scores.constrainedSimilarity()));
                    writer.write(',');
                    writer.write(Double.toString(scores.exhaustiveSimilarity() - scores.constrainedSimilarity()));
                    writer.write(',');
                    writer.write(Double.toString(scores.exhaustiveDistance()));
                    writer.write(',');
                    writer.write(Double.toString(scores.constrainedDistance()));
                    writer.write(',');
                    writer.write(Integer.toString(scores.exhaustiveCandidatePairs()));
                    writer.write(',');
                    writer.write(Integer.toString(scores.constrainedCandidatePairs()));
                    writer.write(',');
                    writer.write(Double.toString(reduction));
                    writer.write(',');
                    writer.write(Integer.toString(scores.exhaustiveMatchedBlocks()));
                    writer.write(',');
                    writer.write(Integer.toString(scores.constrainedMatchedBlocks()));
                    writer.newLine();
                }
            }
        }
        System.out.println("Wrote WALA discovRE comparison report to " + output.toAbsolutePath());
    }

    private static Map<String, List<MethodCandidate>> numericMethodCandidates(
            Map<String, AnalyzedMethod> leftMethods,
            Map<String, AnalyzedMethod> rightMethods,
            int methodTopK) {
        Map<String, List<MethodNumericKnnPreFilter.MethodCandidate>> selected =
                new MethodNumericKnnPreFilter().select(
                        new ArrayList<>(leftMethods.values()),
                        new ArrayList<>(rightMethods.values()),
                        methodTopK);
        Map<String, List<MethodCandidate>> converted = new LinkedHashMap<>();
        for (Map.Entry<String, List<MethodNumericKnnPreFilter.MethodCandidate>> entry : selected.entrySet()) {
            List<MethodCandidate> candidates = new ArrayList<>();
            for (MethodNumericKnnPreFilter.MethodCandidate candidate : entry.getValue()) {
                candidates.add(new MethodCandidate(candidate.rightMethodSignature(), candidate.score()));
            }
            converted.put(entry.getKey(), candidates);
        }
        return converted;
    }

    private static Map<String, List<MethodCandidate>> methodCandidates(
            Set<String> leftMethodSignatures,
            Set<String> rightMethodSignatures,
            Map<String, List<BlockCandidate>> knnCandidates,
            int methodTopK) {
        Map<String, Map<String, Double>> scores = new LinkedHashMap<>();
        for (Map.Entry<String, List<BlockCandidate>> entry : knnCandidates.entrySet()) {
            String leftSignature = methodSignature(entry.getKey());
            if (!leftMethodSignatures.contains(leftSignature)) {
                continue;
            }
            Map<String, Double> byRightMethod = scores.computeIfAbsent(leftSignature, ignored -> new LinkedHashMap<>());
            for (BlockCandidate candidate : entry.getValue()) {
                String rightSignature = methodSignature(candidate.candidateBlockId());
                if (!rightMethodSignatures.contains(rightSignature)) {
                    continue;
                }
                byRightMethod.merge(rightSignature, 1.0 / (1.0 + candidate.distance()), Double::sum);
            }
        }

        for (String leftSignature : leftMethodSignatures) {
            if (rightMethodSignatures.contains(leftSignature)) {
                scores.computeIfAbsent(leftSignature, ignored -> new LinkedHashMap<>())
                        .merge(leftSignature, Double.MAX_VALUE / 4.0, Math::max);
            }
        }

        Map<String, List<MethodCandidate>> selected = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Double>> entry : scores.entrySet()) {
            List<MethodCandidate> candidates = entry.getValue().entrySet().stream()
                    .map(score -> new MethodCandidate(score.getKey(), score.getValue()))
                    .sorted(Comparator
                            .comparingDouble(MethodCandidate::score).reversed()
                            .thenComparing(MethodCandidate::rightMethodSignature))
                    .limit(methodTopK)
                    .toList();
            selected.put(entry.getKey(), candidates);
        }
        return selected;
    }

    private static StructuralScores scoreStructural(
            String structuralScorer,
            AnalyzedMethod left,
            AnalyzedMethod right,
            Map<String, Set<String>> allowedPairs,
            DiscovreCfgMatcher discovreMatcher,
            StaticCfgMatcher mode1Matcher) {
        if ("mode1".equals(structuralScorer)) {
            MethodCfgMatchResult result = mode1Matcher.match(left, right);
            double similarity = result.overallSimilarity();
            double distance = 1.0 - similarity;
            int matchedBlocks = result.blockMatches().size();
            // StaticCfgMatcher scores all block pairs and has no constrained variant, so the
            // exhaustive and constrained columns carry the same Mode-1 values (reduction = 0).
            int candidatePairs = left.cfg().blocks().size() * right.cfg().blocks().size();
            return new StructuralScores(
                    similarity, similarity, distance, distance,
                    candidatePairs, candidatePairs, matchedBlocks, matchedBlocks);
        }
        DiscovreCfgMatchResult exhaustive = discovreMatcher.match(left, right);
        DiscovreCfgMatchResult constrained = discovreMatcher.match(left, right, allowedPairs);
        return new StructuralScores(
                exhaustive.similarity(), constrained.similarity(),
                exhaustive.distance(), constrained.distance(),
                exhaustive.candidatePairCount(), constrained.candidatePairCount(),
                exhaustive.matchedBlockCount(), constrained.matchedBlockCount());
    }

    private record StructuralScores(
            double exhaustiveSimilarity,
            double constrainedSimilarity,
            double exhaustiveDistance,
            double constrainedDistance,
            int exhaustiveCandidatePairs,
            int constrainedCandidatePairs,
            int exhaustiveMatchedBlocks,
            int constrainedMatchedBlocks) {
    }

    private static Map<String, AnalyzedMethod> methodsBySignature(List<AnalyzedMethod> methods) {
        Map<String, AnalyzedMethod> bySignature = new LinkedHashMap<>();
        for (AnalyzedMethod method : methods) {
            bySignature.put(method.signature(), method);
        }
        return bySignature;
    }

    private static Map<String, RawToolBlock> flattenRawBlocks(String side, RawToolProgram program) {
        Map<String, RawToolBlock> blocks = new LinkedHashMap<>();
        for (RawToolClass rawClass : program.classes()) {
            for (RawToolMethod method : rawClass.methods()) {
                for (RawToolBlock block : method.blocks()) {
                    blocks.put(rawBlockId(side, method.rawMethodSignature(), block.rawBlockNumber()), block);
                }
            }
        }
        return blocks;
    }

    private static Map<String, Set<String>> allowedPairsFor(
            String leftMethodSignature,
            String rightMethodSignature,
            Map<String, List<BlockCandidate>> knnCandidates) {
        Map<String, Set<String>> allowed = new HashMap<>();
        String leftPrefix = "left:" + leftMethodSignature + "#";
        String rightPrefix = "right:" + rightMethodSignature + "#";
        for (Map.Entry<String, List<BlockCandidate>> entry : knnCandidates.entrySet()) {
            if (!entry.getKey().startsWith(leftPrefix)) {
                continue;
            }
            String leftBlock = blockId(entry.getKey());
            Set<String> rightBlocks = entry.getValue().stream()
                    .map(BlockCandidate::candidateBlockId)
                    .filter(id -> id.startsWith(rightPrefix))
                    .map(WalaDiscovreComparisonMain::blockId)
                    .collect(Collectors.toSet());
            if (!rightBlocks.isEmpty()) {
                allowed.put(leftBlock, rightBlocks);
            }
        }
        return allowed;
    }

    private static String rawBlockId(String side, String methodSignature, int blockNumber) {
        return side + ":" + methodSignature + "#b" + blockNumber;
    }

    private static String blockId(String rawBlockId) {
        int index = rawBlockId.lastIndexOf("#b");
        return index < 0 ? rawBlockId : rawBlockId.substring(index + 1);
    }

    private static String methodSignature(String rawBlockId) {
        int sideIndex = rawBlockId.indexOf(':');
        int blockIndex = rawBlockId.lastIndexOf("#b");
        if (sideIndex < 0 || blockIndex < 0 || sideIndex + 1 >= blockIndex) {
            return rawBlockId;
        }
        return rawBlockId.substring(sideIndex + 1, blockIndex);
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private record MethodCandidate(String rightMethodSignature, double score) {
    }
}
