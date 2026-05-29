package com.ziqi.codesim.semantic.raw;

import java.util.List;

public record RawToolProgram(
        String toolName,
        String toolVersion,
        String inputId,
        List<RawToolClass> classes,
        List<RawToolRecord> rawRecords
) {
    public RawToolProgram {
        classes = List.copyOf(classes);
        rawRecords = List.copyOf(rawRecords);
    }
}
