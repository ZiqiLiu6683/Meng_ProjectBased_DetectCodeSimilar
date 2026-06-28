package com.ziqi.codesim.next;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Human-readable, per-file clone-type breakdown for one file pair.
 *
 * <p>It deliberately does NOT reduce the pair to one label. For each file it
 * reports how much of the file is affected and splits that by clone type (from
 * {@link FileCloneSummary#evidenceBreakdown()}), then lists the accepted regions
 * with their type and tags. The single dominant/overall labels are demoted to a
 * trailing note, since a hard file can legitimately contain several clone types
 * at once.
 */
public class NextBreakdownReportFormatter {

    public String format(NextPipelineResult result, String leftName, String rightName) {
        FileCloneSummary summary = result.fileSummary();
        List<EvidenceBreakdown> breakdown = summary.evidenceBreakdown();
        StringBuilder out = new StringBuilder();

        appendFile(out, "File A", leftName, summary.matchedCoverageLeft(), breakdown, true);
        out.append('\n');
        appendFile(out, "File B", rightName, summary.matchedCoverageRight(), breakdown, false);

        out.append("\nRegions\n");
        // Show the same regions that drive the per-type percentages: when there is any
        // granular (non whole-file) evidence, the whole-file match is redundant context
        // and is excluded from coverage, so we exclude it from the listing too.
        List<RegionDecision> regions = userFacingRegions(result.selectedRegionDecisions());
        if (regions.isEmpty()) {
            out.append("  (no clone-like regions)\n");
        } else {
            for (RegionDecision decision : regions) {
                out.append(String.format(Locale.ROOT, "  %-4s %s -> %s%s%n",
                        decision.type().name(),
                        decision.candidate().left().displayName(),
                        decision.candidate().right().displayName(),
                        tagSuffix(decision.tags())));
            }
        }

        out.append(String.format(Locale.ROOT,
                "%nnote: dominant=%s, relationship=%s, priority=%s (single-label view, secondary)%n",
                summary.dominantRegionType(),
                summary.overallRelationship(),
                summary.inspectionPriority()));
        return out.toString();
    }

    private static void appendFile(StringBuilder out,
                                   String label,
                                   String name,
                                   double affected,
                                   List<EvidenceBreakdown> breakdown,
                                   boolean left) {
        int affectedPercent = (int) Math.round(affected * 100.0);
        out.append(String.format(Locale.ROOT, "%s  (%s)   affected: %d%%%n", label, name, affectedPercent));

        // Use the mutually-exclusive partition (each line attributed to its strongest type)
        // so the per-type percentages do not overlap, ordered by coverage, strongest first.
        List<EvidenceBreakdown> sorted = new ArrayList<>(breakdown);
        sorted.sort(Comparator.comparingDouble(
                (EvidenceBreakdown b) -> left ? b.exclusiveAffectedLeftRatio() : b.exclusiveAffectedRightRatio())
                .reversed());
        List<EvidenceBreakdown> kept = new ArrayList<>();
        List<Double> ratios = new ArrayList<>();
        for (EvidenceBreakdown item : sorted) {
            double ratio = left ? item.exclusiveAffectedLeftRatio() : item.exclusiveAffectedRightRatio();
            if (ratio > 0.0) {
                kept.add(item);
                ratios.add(ratio);
            }
        }
        if (kept.isEmpty()) {
            out.append("  (no clone evidence)\n");
            return;
        }
        // Largest-remainder rounding so the per-type integer percentages sum exactly to the
        // file's affected percentage (plain per-value rounding can be off by 1).
        int[] percents = largestRemainderPercents(ratios, affectedPercent);
        for (int i = 0; i < kept.size(); i++) {
            EvidenceBreakdown item = kept.get(i);
            out.append(String.format(Locale.ROOT, "  %-4s %3d%%   (%d region%s)%n",
                    item.type().name(),
                    percents[i],
                    item.regionCount(),
                    item.regionCount() == 1 ? "" : "s"));
        }
    }

    private static List<RegionDecision> userFacingRegions(List<RegionDecision> selected) {
        boolean hasGranular = selected.stream().anyMatch(decision -> !isFilePair(decision));
        if (!hasGranular) {
            return selected;
        }
        List<RegionDecision> granular = new ArrayList<>();
        for (RegionDecision decision : selected) {
            if (!isFilePair(decision)) {
                granular.add(decision);
            }
        }
        return granular;
    }

    private static boolean isFilePair(RegionDecision decision) {
        return decision.candidate().left().kind() == RegionKind.FILE
                && decision.candidate().right().kind() == RegionKind.FILE;
    }

    // Hamilton / largest-remainder method: floor each value, then hand the leftover points
    // to the largest fractional remainders, so the integer percentages sum exactly to target.
    private static int[] largestRemainderPercents(List<Double> ratios, int target) {
        int n = ratios.size();
        int[] result = new int[n];
        double[] remainders = new double[n];
        int floorSum = 0;
        for (int i = 0; i < n; i++) {
            double raw = ratios.get(i) * 100.0;
            result[i] = (int) Math.floor(raw);
            remainders[i] = raw - result[i];
            floorSum += result[i];
        }
        int leftover = target - floorSum;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        if (leftover > 0) {
            Arrays.sort(order, (a, b) -> Double.compare(remainders[b], remainders[a]));
            for (int i = 0; i < leftover && i < n; i++) {
                result[order[i]]++;
            }
        } else if (leftover < 0) {
            Arrays.sort(order, (a, b) -> Double.compare(remainders[a], remainders[b]));
            for (int i = 0; i < -leftover && i < n; i++) {
                result[order[i]]--;
            }
        }
        return result;
    }

    private static String tagSuffix(Set<RegionTag> tags) {
        if (tags.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (RegionTag tag : tags) {
            names.add(tag.name().toLowerCase(Locale.ROOT));
        }
        names.sort(Comparator.naturalOrder());
        return "   [" + String.join(", ", names) + "]";
    }
}
