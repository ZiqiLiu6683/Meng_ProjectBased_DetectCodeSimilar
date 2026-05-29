package com.ziqi.codesim.semantic.raw;

import java.util.List;

public record RawToolMethod(
        String rawMethodString,
        String rawMethodSignature,
        String rawDeclaringClass,
        String rawReturnType,
        List<String> rawParameterTypes,
        String rawIRText,
        String rawSymbolTableText,
        String rawCFGText,
        List<RawToolBlock> blocks,
        List<RawToolRecord> rawRecords
) {
    public RawToolMethod {
        rawParameterTypes = List.copyOf(rawParameterTypes);
        blocks = List.copyOf(blocks);
        rawRecords = List.copyOf(rawRecords);
    }
}
