package com.ziqi.codesim.semantic.raw;

import java.util.List;

public record RawToolBlock(
        String rawBlockString,
        int rawBlockNumber,
        boolean rawIsEntry,
        boolean rawIsExit,
        List<String> rawNormalSuccessors,
        List<String> rawExceptionalSuccessors,
        List<String> rawPredecessors,
        List<RawToolInstruction> instructions,
        List<RawToolRecord> rawRecords
) {
    public RawToolBlock {
        rawNormalSuccessors = List.copyOf(rawNormalSuccessors);
        rawExceptionalSuccessors = List.copyOf(rawExceptionalSuccessors);
        rawPredecessors = List.copyOf(rawPredecessors);
        instructions = List.copyOf(instructions);
        rawRecords = List.copyOf(rawRecords);
    }
}
