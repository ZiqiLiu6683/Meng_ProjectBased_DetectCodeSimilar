package com.ziqi.codesim.semantic.knn;

import com.ziqi.codesim.semantic.raw.RawToolBlock;
import com.ziqi.codesim.semantic.raw.RawToolInstruction;
import com.ziqi.codesim.semantic.raw.RawToolRecord;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

public class RawBlockFeatureExtractor {
    private static final Map<String, Integer> DEFAULT_BUCKETS = Map.of(
            "instruction.rawInstructionClassName", 32,
            "instruction.rawInstructionText", 128,
            "instruction.rawDeclaredTargetText", 64,
            "instruction.rawDefValue", 32,
            "instruction.rawUseValue", 32,
            "block.rawNormalSuccessor", 32,
            "block.rawPredecessor", 32,
            "block.rawExceptionalSuccessor", 32
    );

    public RawFeatureVector extract(
            String itemId,
            RawToolBlock block,
            Set<String> selectedChannels,
            KnnFeatureView view) {
        Map<String, Double> values = new HashMap<>();
        Map<String, List<FeatureContribution>> provenance = new HashMap<>();
        if (view == KnnFeatureView.DISCOVRE_NUMERIC || view == KnnFeatureView.HYBRID_NUMERIC_HASH) {
            addNumericFeatures(block, selectedChannels, values, provenance);
        }
        if (view == KnnFeatureView.RAW_HASH_BUCKET || view == KnnFeatureView.HYBRID_NUMERIC_HASH) {
            addHashBucketFeatures(block, selectedChannels, values, provenance);
        }
        return new RawFeatureVector(itemId, view, values, provenance);
    }

    private static void addNumericFeatures(
            RawToolBlock block,
            Set<String> selectedChannels,
            Map<String, Double> values,
            Map<String, List<FeatureContribution>> provenance) {
        add(values, "numeric.instructionCount", block.instructions().size());
        add(values, "numeric.normalSuccessorCount", block.rawNormalSuccessors().size());
        add(values, "numeric.exceptionalSuccessorCount", block.rawExceptionalSuccessors().size());
        add(values, "numeric.predecessorCount", block.rawPredecessors().size());

        Set<String> instructionClasses = new HashSet<>();
        Set<String> instructionTexts = new HashSet<>();
        Set<String> declaredTargets = new HashSet<>();
        int defCount = 0;
        int useCount = 0;
        int declaredTargetCount = 0;
        for (RawToolInstruction instruction : block.instructions()) {
            instructionClasses.add(instruction.rawInstructionClassName());
            instructionTexts.add(instruction.rawInstructionText());
            defCount += instruction.rawDefValues().size();
            useCount += instruction.rawUseValues().size();
            if (!instruction.rawDeclaredTargetText().isBlank()) {
                declaredTargetCount++;
                declaredTargets.add(instruction.rawDeclaredTargetText());
            }
            for (RawToolRecord record : instruction.rawRecords()) {
                if (selectedChannels.contains(record.channel())) {
                    addProvenance(provenance, numericFeatureFor(record.channel()), record);
                }
            }
        }
        add(values, "numeric.distinctInstructionClassCount", instructionClasses.size());
        add(values, "numeric.distinctInstructionTextCount", instructionTexts.size());
        add(values, "numeric.declaredTargetCount", declaredTargetCount);
        add(values, "numeric.distinctDeclaredTargetCount", declaredTargets.size());
        add(values, "numeric.defValueCount", defCount);
        add(values, "numeric.useValueCount", useCount);
        for (RawToolRecord record : block.rawRecords()) {
            if (selectedChannels.contains(record.channel())) {
                addProvenance(provenance, numericFeatureFor(record.channel()), record);
            }
        }
    }

    private static void addHashBucketFeatures(
            RawToolBlock block,
            Set<String> selectedChannels,
            Map<String, Double> values,
            Map<String, List<FeatureContribution>> provenance) {
        for (RawToolRecord record : allBlockRecords(block)) {
            if (!selectedChannels.contains(record.channel())) {
                continue;
            }
            int bucketCount = DEFAULT_BUCKETS.getOrDefault(record.channel(), 32);
            int bucket = stableBucket(record.channel() + "\0" + record.rawValue(), bucketCount);
            String featureName = "hash." + record.channel() + "." + bucket;
            add(values, featureName, 1.0);
            addProvenance(provenance, featureName, record);
        }
    }

    private static List<RawToolRecord> allBlockRecords(RawToolBlock block) {
        List<RawToolRecord> records = new ArrayList<>(block.rawRecords());
        for (RawToolInstruction instruction : block.instructions()) {
            records.addAll(instruction.rawRecords());
        }
        return records;
    }

    private static String numericFeatureFor(String channel) {
        return switch (channel) {
            case "instruction.rawInstructionClassName" -> "numeric.distinctInstructionClassCount";
            case "instruction.rawInstructionText" -> "numeric.distinctInstructionTextCount";
            case "instruction.rawDeclaredTargetText" -> "numeric.declaredTargetCount";
            case "instruction.rawDefValue" -> "numeric.defValueCount";
            case "instruction.rawUseValue" -> "numeric.useValueCount";
            case "block.rawNormalSuccessor" -> "numeric.normalSuccessorCount";
            case "block.rawExceptionalSuccessor" -> "numeric.exceptionalSuccessorCount";
            case "block.rawPredecessor" -> "numeric.predecessorCount";
            default -> "numeric.instructionCount";
        };
    }

    private static int stableBucket(String value, int bucketCount) {
        CRC32 crc32 = new CRC32();
        crc32.update(value.getBytes(StandardCharsets.UTF_8));
        return (int) (crc32.getValue() % bucketCount);
    }

    private static void add(Map<String, Double> values, String key, double amount) {
        values.merge(key, amount, Double::sum);
    }

    private static void addProvenance(
            Map<String, List<FeatureContribution>> provenance,
            String featureName,
            RawToolRecord record) {
        provenance.computeIfAbsent(featureName, ignored -> new ArrayList<>())
                .add(new FeatureContribution(featureName, record.channel(), record.rawValue(), record.provenance()));
    }
}
