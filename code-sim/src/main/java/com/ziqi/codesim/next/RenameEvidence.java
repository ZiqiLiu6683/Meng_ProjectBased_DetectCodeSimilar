package com.ziqi.codesim.next;

import java.util.Map;

public record RenameEvidence(
        Map<String, String> identifierMap,
        boolean hasConflict
) {
    public boolean detected() {
        return !identifierMap.isEmpty();
    }
}
