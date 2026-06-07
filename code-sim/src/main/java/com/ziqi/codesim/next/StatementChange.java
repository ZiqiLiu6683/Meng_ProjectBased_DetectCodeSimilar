package com.ziqi.codesim.next;

public record StatementChange(
        StatementChangeKind kind,
        String leftText,
        String rightText
) {
}
