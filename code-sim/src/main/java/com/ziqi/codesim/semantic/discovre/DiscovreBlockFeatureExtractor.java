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
            // discovRE Table III "Transfer" = data-transfer instructions (load/store/move),
            // NOT redirections (branch/return). In WALA SSA the data-movement categories are
            // ASSIGNMENT (broad data-def bucket), FIELD_ACCESS (get/put) and ARRAY_ACCESS
            // (array load/store). Branch/return are redirections and are captured structurally
            // by the CFG edges, so they are intentionally excluded from the block content distance.
            if (category == InstructionCategory.ASSIGNMENT
                    || category == InstructionCategory.FIELD_ACCESS
                    || category == InstructionCategory.ARRAY_ACCESS) {
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
