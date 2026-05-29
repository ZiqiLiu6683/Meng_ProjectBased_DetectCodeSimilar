package com.ziqi.codesim.semantic.raw;

import java.util.List;

public record RawToolRecord(
        String channel,
        String rawValue,
        List<String> provenance
) {
    public RawToolRecord {
        provenance = List.copyOf(provenance);
    }
}
