package com.ziqi.codesim.next;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
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
        return format(result, leftName, rightName, "both");
    }

    public String format(NextPipelineResult result, String leftName, String rightName, String view) {
        return format(result, leftName, rightName, view, false);
    }

    public String format(NextPipelineResult result, String leftName, String rightName,
                         String view, boolean showAll) {
        FileCloneSummary summary = result.fileSummary();
        List<EvidenceBreakdown> breakdown = summary.evidenceBreakdown();
        StringBuilder out = new StringBuilder();

        appendFile(out, "File A", leftName, summary.matchedCoverageLeft(), breakdown, true);
        out.append('\n');
        appendFile(out, "File B", rightName, summary.matchedCoverageRight(), breakdown, false);

        // Two views over the same regions, so the user can inspect the problems at whichever
        // granularity they care about and judge for themselves. Method-level = matches that
        // involve a whole method/unit; block-level = pure fragment matches (statement windows,
        // block sequences, control regions). The whole-file match is excluded as redundant
        // context when finer evidence exists.
        List<RegionDecision> regions = userFacingRegions(result.selectedRegionDecisions());
        // De-duplicated (overlapping/contained) regions are not dropped, just held back: shown
        // only with --show-all, otherwise summarized as a count so no information is silently lost.
        List<RegionDecision> redundant = userFacingRegions(suppressedRegions(result));
        int leftSpan = lineSpan(fileRegion(result, true));
        int rightSpan = lineSpan(fileRegion(result, false));
        boolean showMethod = !"block".equalsIgnoreCase(view);
        boolean showBlock = !"method".equalsIgnoreCase(view);
        if (showMethod) {
            appendRegionSection(out, "Method-level matches", regions, redundant, true, showAll, leftSpan, rightSpan);
        }
        if (showBlock) {
            appendRegionSection(out, "Block-level matches", regions, redundant, false, showAll, leftSpan, rightSpan);
        }

        out.append(String.format(Locale.ROOT,
                "%nnote: inspection priority = %s (guidance for what to review first; the system intentionally assigns no single file-level clone type)%n",
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

    private static void appendRegionSection(StringBuilder out, String title,
                                            List<RegionDecision> regions,
                                            List<RegionDecision> redundant,
                                            boolean methodLevel,
                                            boolean showAll,
                                            int leftSpan,
                                            int rightSpan) {
        List<RegionDecision> inView = new ArrayList<>();
        for (RegionDecision decision : regions) {
            if (isMethodLevel(decision) == methodLevel) {
                inView.add(decision);
            }
        }
        out.append('\n').append(title);
        // Per-view coverage: how much of each file this view's regions touch, computed as a union
        // of covered lines over the file's line span (no double-counting of overlapping regions).
        if (!inView.isEmpty() && (leftSpan > 0 || rightSpan > 0)) {
            out.append(String.format(Locale.ROOT, "   (covers A %d%% / B %d%%)",
                    percent(coveredLineCount(inView, true), leftSpan),
                    percent(coveredLineCount(inView, false), rightSpan)));
        }
        out.append('\n');
        for (RegionDecision decision : inView) {
            appendRegionLine(out, decision, "");
        }
        if (inView.isEmpty()) {
            out.append("  (none)\n");
        }
        // Held-back (de-duplicated) regions for this view: shown with --show-all, otherwise counted.
        List<RegionDecision> redundantHere = redundant.stream()
                .filter(decision -> isMethodLevel(decision) == methodLevel)
                .toList();
        if (!redundantHere.isEmpty()) {
            if (showAll) {
                for (RegionDecision decision : redundantHere) {
                    appendRegionLine(out, decision, " [redundant]");
                }
            } else {
                out.append(String.format(Locale.ROOT,
                        "  (+%d redundant/contained region%s hidden; use --show-all to list)%n",
                        redundantHere.size(), redundantHere.size() == 1 ? "" : "s"));
            }
        }
    }

    // Union of source lines covered by a view's regions on one side, so overlapping regions are
    // counted once. Mirrors FileLevelAggregator's line-set coverage so the numbers are consistent.
    private static int coveredLineCount(List<RegionDecision> regions, boolean left) {
        Set<Integer> lines = new HashSet<>();
        for (RegionDecision decision : regions) {
            CodeRegion region = left ? decision.candidate().left() : decision.candidate().right();
            if (region.beginLine() < 0 || region.endLine() < region.beginLine()) {
                continue;
            }
            for (int line = region.beginLine(); line <= region.endLine(); line++) {
                lines.add(line);
            }
        }
        return lines.size();
    }

    private static int percent(int covered, int span) {
        if (span <= 0) {
            return 0;
        }
        return (int) Math.min(100, Math.round(100.0 * covered / span));
    }

    private static int lineSpan(CodeRegion region) {
        if (region == null || region.beginLine() < 0 || region.endLine() < region.beginLine()) {
            return 0;
        }
        return region.endLine() - region.beginLine() + 1;
    }

    private static CodeRegion fileRegion(NextPipelineResult result, boolean left) {
        for (RegionDecision decision : result.regionDecisions()) {
            CodeRegion region = left ? decision.candidate().left() : decision.candidate().right();
            if (region.kind() == RegionKind.FILE) {
                return region;
            }
        }
        return null;
    }

    private static void appendRegionLine(StringBuilder out, RegionDecision decision, String marker) {
        out.append(String.format(Locale.ROOT, "  %-4s %s -> %s%s%s%s%n",
                decision.type().name(),
                decision.candidate().left().displayName(),
                decision.candidate().right().displayName(),
                tagSuffix(decision.tags()),
                rawInfo(decision),
                marker));
    }

    // Accepted clone regions that were de-duplicated away (in the full region list but not in the
    // selected/shown set). Not dropped -- surfaced on demand so no information is silently lost.
    private static List<RegionDecision> suppressedRegions(NextPipelineResult result) {
        java.util.Set<String> selectedIds = new java.util.HashSet<>();
        for (RegionDecision decision : result.selectedRegionDecisions()) {
            selectedIds.add(decision.candidate().candidateId());
        }
        List<RegionDecision> suppressed = new ArrayList<>();
        for (RegionDecision decision : result.regionDecisions()) {
            if (decision.type() != CloneRegionType.NON_CLONE
                    && !selectedIds.contains(decision.candidate().candidateId())) {
                suppressed.add(decision);
            }
        }
        return suppressed;
    }

    // Raw numbers for the user to judge instead of system-imposed flags: the matched region
    // sizes (statements) and, when available, the method CFG structural similarity.
    private static String rawInfo(RegionDecision decision) {
        String cfg = Double.isNaN(decision.structuralSimilarity())
                ? ""
                : String.format(Locale.ROOT, ", cfg-sim %.2f", decision.structuralSimilarity());
        return String.format(Locale.ROOT, "   (stmts %d/%d%s)",
                decision.candidate().left().statementCount(),
                decision.candidate().right().statementCount(),
                cfg);
    }

    // A match is "method-level" when either side is a whole method or comparable unit; otherwise
    // it is a pure fragment (block-level) match.
    private static boolean isMethodLevel(RegionDecision decision) {
        return isCompleteUnit(decision.candidate().left().kind())
                || isCompleteUnit(decision.candidate().right().kind());
    }

    private static boolean isCompleteUnit(RegionKind kind) {
        return kind == RegionKind.METHOD
                || kind == RegionKind.METHOD_BODY_REGION
                || kind == RegionKind.CALL_EXPANDED_REGION
                || kind == RegionKind.FILE;
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
