package com.ziqi.codesim.next;

import java.util.List;

public record CodeRegion(
        String regionId,
        RegionSide side,
        RegionKind kind,
        String displayName,
        int beginLine,
        int endLine,
        List<String> rawTokens,
        List<String> t1ComparableTokens,
        List<String> t2NormalizedTokens,
        List<String> statementTexts,
        List<String> normalizedStatementTexts
) {
    public int tokenCount() {
        return rawTokens.size();
    }

    public int statementCount() {
        return statementTexts.size();
    }
}
