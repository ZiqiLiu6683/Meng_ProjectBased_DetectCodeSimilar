package com.ziqi.codesim.next;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One contiguous 1-based inclusive run of source lines.
 *
 * A region is not necessarily contiguous. {@link CodeRegion#beginLine()} and
 * {@link CodeRegion#endLine()} are the minimum and maximum over the region's statements, so a
 * region reported as "lines 12-100" may actually consist of a few statements near 12 and a few
 * near 100 with unrelated code between them -- and the clone type was decided on those statements
 * only, not on everything inside the bounding box. Consumers that treat the bounding box as the
 * region's content will over-state what was matched: a highlight will colour code the verdict never
 * looked at, and a localisation score will compare a dense reference against a sparse prediction.
 *
 * {@link CodeRegion#segments()} carries the runs the verdict was actually computed from.
 */
public record LineSegment(int begin, int end) {

    public LineSegment {
        if (end < begin) {
            throw new IllegalArgumentException("end < begin: " + begin + ".." + end);
        }
    }

    public int lineCount() {
        return end - begin + 1;
    }

    public boolean contains(int line) {
        return line >= begin && line <= end;
    }

    /**
     * Sort and coalesce, joining runs that overlap or merely touch. Adjacent statements produce
     * adjacent runs, and reporting {@code 5-7} and {@code 8-10} separately would suggest a gap at
     * line 8 that does not exist.
     */
    public static List<LineSegment> normalize(List<LineSegment> segments) {
        List<LineSegment> sorted = new ArrayList<>(segments);
        sorted.removeIf(segment -> segment.begin() < 1);
        sorted.sort(Comparator.comparingInt(LineSegment::begin).thenComparingInt(LineSegment::end));
        List<LineSegment> merged = new ArrayList<>();
        for (LineSegment segment : sorted) {
            if (merged.isEmpty()) {
                merged.add(segment);
                continue;
            }
            LineSegment last = merged.get(merged.size() - 1);
            if (segment.begin() <= last.end() + 1) {
                merged.set(merged.size() - 1,
                        new LineSegment(last.begin(), Math.max(last.end(), segment.end())));
            } else {
                merged.add(segment);
            }
        }
        return List.copyOf(merged);
    }

    /** Total covered lines, counting each line once. */
    public static int lineCount(List<LineSegment> segments) {
        int total = 0;
        for (LineSegment segment : normalize(segments)) {
            total += segment.lineCount();
        }
        return total;
    }
}
