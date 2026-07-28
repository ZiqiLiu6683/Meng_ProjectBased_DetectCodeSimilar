package com.ziqi.codesim.next;

import java.util.List;

/**
 * A uniformly-typed run of statements inside one Phase A region.
 *
 * A grown region is maximal: it keeps extending while the two sides correspond, so it stops where
 * the correspondence breaks, NOT where the KIND of difference changes. A single region therefore
 * routinely holds several relationships at once -- measured on the region corpus, 59% of emitted
 * regions contained both rename evidence and statement edits, and every one of them was reported
 * as T3 alone, hiding the renamed and identical parts inside it.
 *
 * The region keeps its own type; this is an additional, finer view of the same evidence. The type
 * here is produced by the SAME T1 -> T2 -> T3 cascade, applied to one aligned statement pair
 * instead of to the whole region, so the strict cascade holds at both scales and no new
 * classification rule is introduced.
 *
 * <p><b>The two scales do not mean the same thing.</b> A region typed T2 means the whole region
 * differs only by identifiers. A sub-region typed T2 means that run of statements differs only by
 * identifiers, while the region around it may differ in other ways.
 *
 * @param type  the cascade's verdict for this run; a statement present on only one side is T3,
 *              since a statement-level edit is exactly what separates T3 from T2
 * @param left  lines on the left side; empty when the statements exist only on the right
 * @param right lines on the right side; empty when the statements exist only on the left
 * @param reason the cascade step that decided it, for the same auditability as decisionPath
 */
public record SubRegion(
        CloneRegionType type,
        List<LineSegment> left,
        List<LineSegment> right,
        String reason
) {
    public SubRegion {
        left = left == null ? List.of() : LineSegment.normalize(left);
        right = right == null ? List.of() : LineSegment.normalize(right);
    }
}
