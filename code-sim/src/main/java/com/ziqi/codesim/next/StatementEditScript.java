package com.ziqi.codesim.next;

import java.util.List;

public record StatementEditScript(
        List<StatementChange> changes
) {
    public boolean hasInserted() {
        return changes.stream().anyMatch(c -> c.kind() == StatementChangeKind.INSERTED);
    }

    public boolean hasDeleted() {
        return changes.stream().anyMatch(c -> c.kind() == StatementChangeKind.DELETED);
    }

    public boolean hasModified() {
        return changes.stream().anyMatch(c -> c.kind() == StatementChangeKind.MODIFIED);
    }

    public boolean hasAnyChange() {
        return !changes.isEmpty();
    }
}
