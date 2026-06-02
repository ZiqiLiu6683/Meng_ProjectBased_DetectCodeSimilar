package com.ziqi.codesim.semantic.knn;

import com.ziqi.codesim.semantic.raw.RawToolBlock;
import com.ziqi.codesim.semantic.raw.RawToolInstruction;
import com.ziqi.codesim.semantic.raw.RawToolRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawBlockFeatureExtractorTest {
    @Test
    void createsNumericAndHashFeaturesWithRawProvenance() {
        RawToolBlock block = block(
                "b1",
                instruction("SSAInvokeInstruction", "invoke helper(I)I", "helper(I)I"),
                instruction("SSAReturnInstruction", "return v2", "")
        );

        RawFeatureVector vector = new RawBlockFeatureExtractor().extract(
                "left#b1",
                block,
                Set.of(
                        "instruction.rawInstructionClassName",
                        "instruction.rawInstructionText",
                        "instruction.rawDeclaredTargetText",
                        "instruction.rawDefValue",
                        "instruction.rawUseValue"
                ),
                KnnFeatureView.HYBRID_NUMERIC_HASH
        );

        assertTrue(vector.values().containsKey("numeric.instructionCount"));
        assertTrue(vector.values().containsKey("numeric.distinctInstructionClassCount"));
        assertTrue(vector.values().keySet().stream().anyMatch(key -> key.startsWith("hash.instruction.rawInstructionText.")));
        assertTrue(vector.provenance().values().stream()
                .flatMap(List::stream)
                .anyMatch(contribution -> contribution.rawValue().equals("invoke helper(I)I")));
    }

    @Test
    void exactKnnRanksCloserRawBlocksFirst() {
        RawBlockFeatureExtractor extractor = new RawBlockFeatureExtractor();
        Set<String> channels = Set.of("instruction.rawInstructionText", "instruction.rawInstructionClassName");
        RawFeatureVector query = extractor.extract(
                "query",
                block("query", instruction("SSAInvokeInstruction", "invoke helper(I)I", "helper(I)I")),
                channels,
                KnnFeatureView.HYBRID_NUMERIC_HASH
        );
        RawFeatureVector close = extractor.extract(
                "close",
                block("close", instruction("SSAInvokeInstruction", "invoke helper(I)I", "helper(I)I")),
                channels,
                KnnFeatureView.HYBRID_NUMERIC_HASH
        );
        RawFeatureVector far = extractor.extract(
                "far",
                block("far",
                        instruction("SSAConditionalBranchInstruction", "conditional branch(eq) v1,#0", ""),
                        instruction("SSAReturnInstruction", "return v1", "")),
                channels,
                KnnFeatureView.HYBRID_NUMERIC_HASH
        );

        FeaturePreprocessor preprocessor = FeaturePreprocessor.fit(List.of(query, close, far));
        ExactKnnIndex index = new ExactKnnIndex(List.of(
                preprocessor.transform(close),
                preprocessor.transform(far)
        ));

        List<BlockCandidate> candidates = index.query(preprocessor.transform(query), 2);

        assertFalse(candidates.isEmpty());
        assertTrue(candidates.get(0).candidateBlockId().equals("close"));
        assertTrue(candidates.get(0).distance() < candidates.get(1).distance());
        assertFalse(candidates.get(0).sharedContributions().isEmpty());
    }

    @Test
    void selectorReturnsTopKForEachQueryBlock() {
        BlockCandidateSelector selector = new BlockCandidateSelector();
        Map<String, List<BlockCandidate>> results = selector.select(
                Map.of("q1", block("q1", instruction("SSAInvokeInstruction", "invoke helper(I)I", "helper(I)I"))),
                Map.of(
                        "c1", block("c1", instruction("SSAInvokeInstruction", "invoke helper(I)I", "helper(I)I")),
                        "c2", block("c2", instruction("SSAReturnInstruction", "return v1", ""))
                ),
                Set.of("instruction.rawInstructionText", "instruction.rawInstructionClassName"),
                KnnFeatureView.HYBRID_NUMERIC_HASH,
                1
        );

        assertTrue(results.containsKey("q1"));
        assertTrue(results.get("q1").size() == 1);
        assertTrue(results.get("q1").get(0).candidateBlockId().equals("c1"));
    }

    private static RawToolBlock block(String id, RawToolInstruction... instructions) {
        return new RawToolBlock(
                id,
                1,
                false,
                false,
                List.of("2"),
                List.of(),
                List.of("0"),
                List.of(instructions),
                List.of(
                        record("block.rawNormalSuccessor", "2", id),
                        record("block.rawPredecessor", "0", id)
                )
        );
    }

    private static RawToolInstruction instruction(String className, String text, String target) {
        List<RawToolRecord> records = target.isBlank()
                ? List.of(
                record("instruction.rawInstructionClassName", className, text),
                record("instruction.rawInstructionText", text, text),
                record("instruction.rawDefValue", "1", text),
                record("instruction.rawUseValue", "2", text)
        )
                : List.of(
                record("instruction.rawInstructionClassName", className, text),
                record("instruction.rawInstructionText", text, text),
                record("instruction.rawDeclaredTargetText", target, text),
                record("instruction.rawDefValue", "1", text),
                record("instruction.rawUseValue", "2", text)
        );
        return new RawToolInstruction(
                className,
                text,
                1,
                List.of("1"),
                List.of("2"),
                target,
                records
        );
    }

    private static RawToolRecord record(String channel, String rawValue, String provenance) {
        return new RawToolRecord(channel, rawValue, List.of(provenance));
    }
}
