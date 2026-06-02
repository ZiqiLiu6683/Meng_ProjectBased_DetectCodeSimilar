package com.ziqi.codesim.semantic.discovre;

import com.ziqi.codesim.semantic.model.BasicBlockUnit;
import com.ziqi.codesim.semantic.model.InstructionCategory;
import com.ziqi.codesim.semantic.model.InstructionUnit;

public class DiscovreBlockFeatureExtractor {
    public DiscovreBlockFeatures extract(BasicBlockUnit block) {
        int arithmetic = 0;
        int calls = 0;
        int logic = 0;
        int transfer = 0;
        int strings = 0;
        int numeric = 0;
        for (InstructionUnit instruction : block.instructions()) {
            InstructionCategory category = instruction.category();
            if (category == InstructionCategory.ARITHMETIC) {
                arithmetic++;
            }
            if (category == InstructionCategory.CALL) {
                calls++;
            }
            if (category == InstructionCategory.LOGIC || category == InstructionCategory.COMPARISON) {
                logic++;
            }
            if (category == InstructionCategory.BRANCH || category == InstructionCategory.RETURN) {
                transfer++;
            }
            strings += instruction.stringReferences().size();
            numeric += instruction.constants().size();
        }
        return new DiscovreBlockFeatures(
                arithmetic,
                calls,
                block.instructions().size(),
                logic,
                transfer,
                strings,
                numeric
        );
    }
}
