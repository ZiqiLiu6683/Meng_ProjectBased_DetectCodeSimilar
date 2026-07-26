package com.ziqi.codesim.semantic.backend.wala.raw;

import com.ziqi.codesim.semantic.knn.BlockCandidate;
import com.ziqi.codesim.semantic.knn.BlockCandidateSelector;
import com.ziqi.codesim.semantic.knn.KnnFeatureView;
import com.ziqi.codesim.semantic.raw.RawToolBlock;
import com.ziqi.codesim.semantic.raw.RawToolClass;
import com.ziqi.codesim.semantic.raw.RawToolMethod;
import com.ziqi.codesim.semantic.raw.RawToolProgram;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WalaKnnCandidateMain {
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
        if (args.length < 3 || args.length > 5) {
            System.err.println("Usage: WalaKnnCandidateMain <left-class-dir> <right-class-dir> <output-csv> [view] [topK]");
            System.err.println("Views: DISCOVRE_NUMERIC, RAW_HASH_BUCKET, HYBRID_NUMERIC_HASH");
            System.exit(2);
        }
        Path leftInput = Path.of(args[0]);
        Path rightInput = Path.of(args[1]);
        Path output = Path.of(args[2]);
        KnnFeatureView view = args.length >= 4 ? KnnFeatureView.valueOf(args[3]) : KnnFeatureView.HYBRID_NUMERIC_HASH;
        int topK = args.length >= 5 ? Integer.parseInt(args[4]) : 8;

        WalaRawSnapshotExtractor extractor = new WalaRawSnapshotExtractor();
        RawToolProgram left = extractor.extract(leftInput);
        RawToolProgram right = extractor.extract(rightInput);
        Map<String, RawToolBlock> leftBlocks = flattenBlocks("left", left);
        Map<String, RawToolBlock> rightBlocks = flattenBlocks("right", right);

        Map<String, List<BlockCandidate>> results = new BlockCandidateSelector().select(
                leftBlocks,
                rightBlocks,
                RAW_FILTERED_KEEP,
                view,
                topK
        );
        writeReport(output, results);
        System.out.println("Wrote KNN candidate report to " + output.toAbsolutePath());
    }

    private static Map<String, RawToolBlock> flattenBlocks(String side, RawToolProgram program) {
        Map<String, RawToolBlock> blocks = new LinkedHashMap<>();
        for (RawToolClass rawClass : program.classes()) {
            for (RawToolMethod method : rawClass.methods()) {
                for (RawToolBlock block : method.blocks()) {
                    String id = side + ":" + method.rawMethodSignature() + "#b" + block.rawBlockNumber();
                    blocks.put(id, block);
                }
            }
        }
        return blocks;
    }

    private static void writeReport(Path output, Map<String, List<BlockCandidate>> results) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("queryBlock,candidateBlock,view,distance,sharedContributionCount,topSharedChannels");
            writer.newLine();
            for (Map.Entry<String, List<BlockCandidate>> entry : results.entrySet()) {
                for (BlockCandidate candidate : entry.getValue()) {
                    writer.write(csv(candidate.queryBlockId()));
                    writer.write(',');
                    writer.write(csv(candidate.candidateBlockId()));
                    writer.write(',');
                    writer.write(candidate.view().name());
                    writer.write(',');
                    writer.write(Double.toString(candidate.distance()));
                    writer.write(',');
                    writer.write(Integer.toString(candidate.sharedContributions().size()));
                    writer.write(',');
                    writer.write(csv(topSharedChannels(candidate)));
                    writer.newLine();
                }
            }
        }
    }

    private static String topSharedChannels(BlockCandidate candidate) {
        return candidate.sharedContributions().stream()
                .map(contribution -> contribution.channel())
                .distinct()
                .limit(6)
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
