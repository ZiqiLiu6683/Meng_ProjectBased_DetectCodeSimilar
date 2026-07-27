package com.ziqi.codesim.next;

import java.util.List;

public record CodeRegion(
        String regionId,
        RegionSide side,
        RegionKind kind,
        String displayName,
        int beginLine,
        int endLine,
        /**
         * The contiguous line runs this region is actually made of, in ascending order.
         *
         * {@code beginLine}/{@code endLine} are only the minimum and maximum over those runs. For
         * {@link RegionKind#CALL_EXPANDED_REGION} -- which growth may spread across two methods --
         * the bounding box can contain code that is not part of the region and was never looked at
         * when the clone type was decided. The token lists below are built from these runs, so this
         * is the field that says what the verdict covers.
         */
        List<LineSegment> segments,
        List<String> rawTokens,
        List<String> t1ComparableTokens,
        List<String> t2NormalizedTokens,
        List<String> statementTexts,
        List<String> normalizedStatementTexts
) {
    public CodeRegion {
        segments = segments == null ? List.of() : LineSegment.normalize(segments);
    }

    /**
     * Backwards-compatible form for callers that only know a contiguous span. The span becomes a
     * single segment, which is correct for every region kind built from a single AST node.
     */
    public CodeRegion(String regionId, RegionSide side, RegionKind kind, String displayName,
                      int beginLine, int endLine, List<String> rawTokens,
                      List<String> t1ComparableTokens, List<String> t2NormalizedTokens,
                      List<String> statementTexts, List<String> normalizedStatementTexts) {
        this(regionId, side, kind, displayName, beginLine, endLine,
                beginLine >= 1 && endLine >= beginLine
                        ? List.of(new LineSegment(beginLine, endLine))
                        : List.of(),
                rawTokens, t1ComparableTokens, t2NormalizedTokens,
                statementTexts, normalizedStatementTexts);
    }

    public int tokenCount() {
        return rawTokens.size();
    }

    public int statementCount() {
        return statementTexts.size();
    }

    /** Lines the verdict actually covers; at most the bounding-box span, often fewer. */
    public int coveredLineCount() {
        return LineSegment.lineCount(segments);
    }

    /** True when the region is one unbroken run, i.e. the bounding box is the whole story. */
    public boolean isContiguous() {
        return segments.size() <= 1;
    }
}
