package com.ziqi.codesim.semantic.raw;

import java.util.ArrayList;
import java.util.List;

public class RawToolRecordCollector {
    public List<RawToolRecord> collect(RawToolProgram program) {
        List<RawToolRecord> records = new ArrayList<>();
        records.addAll(program.rawRecords());
        for (RawToolClass rawClass : program.classes()) {
            records.addAll(rawClass.rawRecords());
            for (RawToolMethod method : rawClass.methods()) {
                records.addAll(method.rawRecords());
                for (RawToolBlock block : method.blocks()) {
                    records.addAll(block.rawRecords());
                    for (RawToolInstruction instruction : block.instructions()) {
                        records.addAll(instruction.rawRecords());
                    }
                }
            }
        }
        return records;
    }
}
