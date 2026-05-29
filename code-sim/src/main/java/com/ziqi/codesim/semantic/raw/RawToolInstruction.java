package com.ziqi.codesim.semantic.raw;

import java.util.List;

public record RawToolInstruction(
        String rawInstructionClassName,
        String rawInstructionText,
        int rawInstructionIndex,
        List<String> rawDefValues,
        List<String> rawUseValues,
        String rawDeclaredTargetText,
        List<RawToolRecord> rawRecords
) {
    public RawToolInstruction {
        rawDefValues = List.copyOf(rawDefValues);
        rawUseValues = List.copyOf(rawUseValues);
        rawRecords = List.copyOf(rawRecords);
    }
}
