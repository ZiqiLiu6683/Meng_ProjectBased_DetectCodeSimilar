package com.ziqi.codesim.semantic.match;

public record BlockMatch(
        String leftBlockId,
        String rightBlockId,
        double distance,
        double similarity
) {
}
