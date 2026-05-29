package com.ziqi.codesim.semantic.raw;

import java.util.List;

public record RawToolClass(
        String rawClassString,
        String rawClassName,
        String rawClassLoader,
        List<RawToolMethod> methods,
        List<RawToolRecord> rawRecords
) {
    public RawToolClass {
        methods = List.copyOf(methods);
        rawRecords = List.copyOf(rawRecords);
    }
}
