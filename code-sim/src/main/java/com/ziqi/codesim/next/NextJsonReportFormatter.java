package com.ziqi.codesim.next;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class NextJsonReportFormatter {
    // No silent truncation by default: emit every user-facing region. A caller may still pass a
    // positive cap (e.g. for a constrained UI), in which case emittedRegionCount vs
    // selectedRegionCount makes any truncation visible rather than hidden.
    private static final int DEFAULT_MAX_REGIONS = Integer.MAX_VALUE;
    private final int maxRegions;

    public NextJsonReportFormatter() {
        this(DEFAULT_MAX_REGIONS);
    }

    public NextJsonReportFormatter(int maxRegions) {
        this.maxRegions = maxRegions;
    }

    public String format(NextPipelineResult result) {
        StringBuilder out = new StringBuilder();
        List<RegionDecision> emittedDecisions = userFacingRegionDecisions(result.selectedRegionDecisions()).stream()
                .limit(maxRegions)
                .toList();
        out.append("{\n");
        appendFileSummary(out, result.fileSummary(), 1);
        out.append(",\n");
        field(out, 1, "acceptedRegionCount", result.regionSelectionSummary().acceptedRegionCount(), true);
        field(out, 1, "selectedRegionCount", result.regionSelectionSummary().selectedRegionCount(), true);
        field(out, 1, "suppressedRegionCount", result.regionSelectionSummary().suppressedRegionCount(), true);
        field(out, 1, "emittedRegionCount", emittedDecisions.size(), true);
        indent(out, 1).append("\"regions\": [\n");
        Map<CloneRegionType, Integer> displayIndexes = new EnumMap<>(CloneRegionType.class);
        for (int i = 0; i < emittedDecisions.size(); i++) {
            RegionDecision decision = emittedDecisions.get(i);
            int displayIndex = displayIndexes.merge(decision.type(), 1, Integer::sum);
            appendRegion(out, decision, displayIndex, 2);
            if (i + 1 < emittedDecisions.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        // A region the recognizer examined and refused is evidence too, but serializing every
        // NON_CLONE decision would dominate a bulk run's output. Off by default; turn on with
        // -Dcodesim.emitRejectedRegions=true to audit WHY a region was refused (each carries its
        // full decision path), which is otherwise unrecoverable from the result alone.
        List<RegionDecision> rejected = Boolean.getBoolean("codesim.emitRejectedRegions")
                ? result.regionDecisions().stream()
                        .filter(decision -> decision.type() == CloneRegionType.NON_CLONE)
                        .limit(maxRegions)
                        .toList()
                : List.of();
        indent(out, 1).append(rejected.isEmpty() ? "]\n" : "],\n");
        if (!rejected.isEmpty()) {
            indent(out, 1).append("\"rejectedRegions\": [\n");
            for (int i = 0; i < rejected.size(); i++) {
                appendRegion(out, rejected.get(i), i + 1, 2);
                if (i + 1 < rejected.size()) {
                    out.append(',');
                }
                out.append('\n');
            }
            indent(out, 1).append("]\n");
        }
        out.append("}\n");
        return out.toString();
    }

    private static List<RegionDecision> userFacingRegionDecisions(List<RegionDecision> decisions) {
        boolean hasGranularEvidence = decisions.stream()
                .anyMatch(decision -> !isFilePair(decision));
        if (!hasGranularEvidence) {
            return decisions;
        }
        return decisions.stream()
                .filter(decision -> !isFilePair(decision))
                .toList();
    }

    private static boolean isFilePair(RegionDecision decision) {
        return decision.candidate().left().kind() == RegionKind.FILE
                && decision.candidate().right().kind() == RegionKind.FILE;
    }

    private static void appendFileSummary(StringBuilder out, FileCloneSummary summary, int level) {
        indent(out, level).append("\"fileSummary\": {\n");
        // inspectionPriority stays top-level: it is review guidance ("look here first"), not a
        // verdict. The three single-label fields (overallRelationship, relationshipShape,
        // dominantRegionType) are demoted into "legacy" because the system intentionally assigns
        // no single file-level clone type; consumers should read the per-region evidence instead.
        field(out, level + 1, "inspectionPriority", summary.inspectionPriority().name(), true);
        appendAffectedContent(out, level + 1, summary, true);
        appendDisplaySummary(out, level + 1, summary, true);
        appendEvidenceBreakdown(out, level + 1, summary.evidenceBreakdown(), true);
        field(out, level + 1, "matchedCoverageLeft", summary.matchedCoverageLeft(), true);
        field(out, level + 1, "matchedCoverageRight", summary.matchedCoverageRight(), true);
        field(out, level + 1, "unrelatedCodeRatio", summary.unrelatedCodeRatio(), true);
        appendEnumIntMap(out, level + 1, "regionTypeCounts", summary.regionTypeCounts(), true);
        appendEnumDoubleMap(out, level + 1, "regionTypeCoverage", summary.regionTypeCoverage(), true);
        // A file pair gets NO single clone type. The three single-label fields below contradict
        // that contract, so they are off by default and only emitted for tooling that still reads
        // them: -Dcodesim.emitLegacyFileSummary=true. The aggregator still computes them, so
        // nothing is lost; they are simply not part of the reported result.
        boolean emitLegacy = Boolean.getBoolean("codesim.emitLegacyFileSummary");
        appendStringArray(out, level + 1, "fileTags",
                summary.fileTags().stream().map(Enum::name).sorted().toList(), emitLegacy);
        if (emitLegacy) {
            appendLegacySummary(out, level + 1, summary, false);
        }
        indent(out, level).append('}');
    }

    // Demoted single-label fields kept only for backward compatibility / tooling. The product
    // does not reduce a file pair to one label; these are not part of the user-facing verdict.
    private static void appendLegacySummary(StringBuilder out, int level,
                                            FileCloneSummary summary, boolean comma) {
        indent(out, level).append("\"legacy\": {\n");
        field(out, level + 1, "overallRelationship", summary.overallRelationship().name(), true);
        field(out, level + 1, "relationshipShape", summary.relationshipShape().name(), true);
        field(out, level + 1, "dominantRegionType", summary.dominantRegionType().name(), false);
        indent(out, level).append('}');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void appendAffectedContent(StringBuilder out, int level,
                                              FileCloneSummary summary, boolean comma) {
        indent(out, level).append("\"affectedContent\": {\n");
        field(out, level + 1, "leftRatio", summary.matchedCoverageLeft(), true);
        field(out, level + 1, "rightRatio", summary.matchedCoverageRight(), false);
        indent(out, level).append('}');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void appendDisplaySummary(StringBuilder out, int level,
                                             FileCloneSummary summary, boolean comma) {
        indent(out, level).append("\"display\": {\n");
        appendAffectedDisplay(out, level + 1, "left", summary.matchedCoverageLeft(), true);
        appendAffectedDisplay(out, level + 1, "right", summary.matchedCoverageRight(), false);
        indent(out, level).append('}');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void appendAffectedDisplay(StringBuilder out, int level, String name,
                                              double ratio, boolean comma) {
        int percent = (int) Math.round(ratio * 100.0);
        indent(out, level).append('"').append(name).append("\": {\n");
        field(out, level + 1, "percent", percent, true);
        field(out, level + 1, "status", severityStatus(percent), true);
        field(out, level + 1, "severityClass", severityClass(percent), true);
        field(out, level + 1, "description", "Matched or changed code regions.", false);
        indent(out, level).append('}');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void appendEvidenceBreakdown(StringBuilder out, int level,
                                                List<EvidenceBreakdown> evidenceBreakdown,
                                                boolean comma) {
        indent(out, level).append("\"evidenceBreakdown\": [\n");
        for (int i = 0; i < evidenceBreakdown.size(); i++) {
            EvidenceBreakdown breakdown = evidenceBreakdown.get(i);
            indent(out, level + 1).append("{\n");
            field(out, level + 2, "type", breakdown.type().name(), true);
            field(out, level + 2, "typeLabel", typeLabel(breakdown.type()), true);
            field(out, level + 2, "typeButtonLabel", typeButtonLabel(breakdown.type()), true);
            field(out, level + 2, "typeClass", typeClass(breakdown.type()), true);
            field(out, level + 2, "regionCount", breakdown.regionCount(), true);
            field(out, level + 2, "affectedLeftRatio", breakdown.affectedLeftRatio(), true);
            field(out, level + 2, "affectedRightRatio", breakdown.affectedRightRatio(), true);
            field(out, level + 2, "exclusiveAffectedLeftRatio", breakdown.exclusiveAffectedLeftRatio(), true);
            field(out, level + 2, "exclusiveAffectedRightRatio", breakdown.exclusiveAffectedRightRatio(), false);
            indent(out, level + 1).append('}');
            if (i + 1 < evidenceBreakdown.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        indent(out, level).append(']');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void appendRegion(StringBuilder out, RegionDecision decision,
                                     int displayIndex, int level) {
        indent(out, level).append("{\n");
        field(out, level + 1, "candidateId", decision.candidate().candidateId(), true);
        field(out, level + 1, "type", decision.type().name(), true);
        field(out, level + 1, "typeLabel", typeLabel(decision.type()), true);
        field(out, level + 1, "typeButtonLabel", typeButtonLabel(decision.type()), true);
        field(out, level + 1, "typeClass", typeClass(decision.type()), true);
        field(out, level + 1, "strength", decision.strength().name(), true);
        field(out, level + 1, "syntacticSimilarity", decision.syntacticSimilarity(), true);
        if (Double.isNaN(decision.structuralSimilarity())) {
            indent(out, level + 1).append("\"structuralSimilarity\": null,\n");
        } else {
            field(out, level + 1, "structuralSimilarity", decision.structuralSimilarity(), true);
        }
        appendRegionEndpoint(out, level + 1, "left", decision.candidate().left(), true);
        appendRegionEndpoint(out, level + 1, "right", decision.candidate().right(), true);
        appendRegionDisplay(out, level + 1, decision, displayIndex, true);
        appendSources(out, level + 1, decision.candidate().sources(), true);
        appendStringArray(out, level + 1, "tags",
                decision.tags().stream().map(Enum::name).sorted().toList(), true);
        appendRenameEvidence(out, level + 1, decision.renameEvidence(), true);
        appendStatementChanges(out, level + 1, decision.statementEditScript(), true);
        appendStringArray(out, level + 1, "decisionPath", decision.decisionPath(), false);
        indent(out, level).append('}');
    }

    private static void appendRegionDisplay(StringBuilder out, int level,
                                            RegionDecision decision, int displayIndex,
                                            boolean comma) {
        CodeRegion left = decision.candidate().left();
        CodeRegion right = decision.candidate().right();
        int leftLines = lineCount(left);
        int rightLines = lineCount(right);
        indent(out, level).append("\"display\": {\n");
        field(out, level + 1, "regionLabel", decision.type().name() + "-" + displayIndex, true);
        field(out, level + 1, "title", left.displayName() + " -> " + right.displayName(), true);
        field(out, level + 1, "summary", regionSummary(decision), true);
        field(out, level + 1, "affectedLabel", "Left " + leftLines + " lines / Right " + rightLines + " lines", true);
        field(out, level + 1, "leftRangeLabel", "lines " + left.beginLine() + "-" + left.endLine(), true);
        field(out, level + 1, "rightRangeLabel", "lines " + right.beginLine() + "-" + right.endLine(), true);
        appendStringArray(out, level + 1, "changeSummaries", changeSummaries(decision), false);
        indent(out, level).append('}');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static String severityStatus(int percent) {
        if (percent >= 90) {
            return "CHECK FIRST";
        }
        if (percent >= 60) {
            return "REVIEW NEEDED";
        }
        return "LIGHT REVIEW";
    }

    private static String severityClass(int percent) {
        if (percent >= 90) {
            return "severity-high";
        }
        if (percent >= 60) {
            return "severity-mid";
        }
        return "severity-low";
    }

    private static String typeLabel(CloneRegionType type) {
        return switch (type) {
            case T1 -> "No code changes (T1)";
            case T2 -> "Names changed (T2)";
            case T3 -> "Code added/changed (T3)";
            case T4_CONFIRMED -> "Same behavior, different code (T4)";
            case T4_DYNAMIC_EVIDENCE -> "Same behavior by testing (T4)";
            case POSSIBLE_T4_CANDIDATE -> "Possible same behavior (T4)";
            case NON_CLONE -> "No clear match";
        };
    }

    private static String typeButtonLabel(CloneRegionType type) {
        return typeLabel(type);
    }

    private static String typeClass(CloneRegionType type) {
        return switch (type) {
            case T1 -> "t1";
            case T2 -> "t2";
            case T3 -> "t3";
            case T4_CONFIRMED, T4_DYNAMIC_EVIDENCE, POSSIBLE_T4_CANDIDATE -> "t4";
            case NON_CLONE -> "non-clone";
        };
    }

    private static int lineCount(CodeRegion region) {
        return Math.max(1, region.endLine() - region.beginLine() + 1);
    }

    private static String regionSummary(RegionDecision decision) {
        if (!decision.tags().isEmpty()) {
            return decision.tags().stream()
                    .map(tag -> tag.name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT))
                    .sorted()
                    .reduce((left, right) -> left + ", " + right)
                    .orElse(decision.type().name());
        }
        if (!decision.decisionPath().isEmpty()) {
            return decision.decisionPath().get(decision.decisionPath().size() - 1);
        }
        return typeLabel(decision.type());
    }

    private static List<String> changeSummaries(RegionDecision decision) {
        List<StatementChange> changes = decision.statementEditScript().changes();
        if (!changes.isEmpty()) {
            // Emit the full edit script (no silent truncation) so the user sees every change.
            return changes.stream()
                    .map(NextJsonReportFormatter::statementChangeSummary)
                    .toList();
        }
        if (!decision.decisionPath().isEmpty()) {
            return decision.decisionPath();
        }
        return List.of(regionSummary(decision));
    }

    private static String statementChangeSummary(StatementChange change) {
        String left = change.leftText() == null || change.leftText().isBlank()
                ? ""
                : "left: " + change.leftText();
        String right = change.rightText() == null || change.rightText().isBlank()
                ? ""
                : "right: " + change.rightText();
        String details = List.of(left, right).stream()
                .filter(value -> !value.isBlank())
                .reduce((a, b) -> a + " | " + b)
                .orElse("");
        return details.isBlank() ? change.kind().name() : change.kind().name() + " " + details;
    }

    private static void appendRegionEndpoint(StringBuilder out, int level, String name,
                                             CodeRegion region, boolean comma) {
        indent(out, level).append('"').append(name).append("\": {\n");
        field(out, level + 1, "regionId", region.regionId(), true);
        field(out, level + 1, "kind", region.kind().name(), true);
        field(out, level + 1, "displayName", region.displayName(), true);
        field(out, level + 1, "beginLine", region.beginLine(), true);
        field(out, level + 1, "endLine", region.endLine(), true);
        // beginLine/endLine are only the bounding box. "segments" is what the verdict covers, and
        // for a region grown across two methods the two differ substantially.
        appendSegments(out, level + 1, region.segments());
        indent(out, level).append('}');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    /** {@code "segments":[{"begin":12,"end":20},{"begin":95,"end":100}]} — no trailing comma. */
    private static void appendSegments(StringBuilder out, int level, List<LineSegment> segments) {
        indent(out, level).append("\"segments\": [");
        for (int i = 0; i < segments.size(); i++) {
            LineSegment segment = segments.get(i);
            out.append("{\"begin\": ").append(segment.begin())
                    .append(", \"end\": ").append(segment.end()).append('}');
            if (i < segments.size() - 1) {
                out.append(", ");
            }
        }
        out.append("]\n");
    }

    private static void appendSources(StringBuilder out, int level,
                                      List<CandidateSource> sources, boolean comma) {
        indent(out, level).append("\"sources\": [\n");
        for (int i = 0; i < sources.size(); i++) {
            CandidateSource source = sources.get(i);
            indent(out, level + 1).append("{");
            stringFieldInline(out, "channel", source.channel(), true);
            numberFieldInline(out, "score", source.score(), false);
            out.append('}');
            if (i + 1 < sources.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        indent(out, level).append(']');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void appendRenameEvidence(StringBuilder out, int level,
                                             RenameEvidence renameEvidence, boolean comma) {
        indent(out, level).append("\"renameEvidence\": {\n");
        field(out, level + 1, "detected", renameEvidence.detected(), true);
        field(out, level + 1, "hasConflict", renameEvidence.hasConflict(), true);
        indent(out, level + 1).append("\"identifierMap\": {\n");
        List<Map.Entry<String, String>> entries = renameEvidence.identifierMap().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, String> entry = entries.get(i);
            field(out, level + 2, entry.getKey(), entry.getValue(), i + 1 < entries.size());
        }
        indent(out, level + 1).append("}\n");
        indent(out, level).append('}');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void appendStatementChanges(StringBuilder out, int level,
                                               StatementEditScript editScript, boolean comma) {
        indent(out, level).append("\"statementChanges\": [\n");
        List<StatementChange> changes = editScript.changes();
        for (int i = 0; i < changes.size(); i++) {
            StatementChange change = changes.get(i);
            indent(out, level + 1).append("{\n");
            field(out, level + 2, "kind", change.kind().name(), true);
            field(out, level + 2, "leftText", change.leftText(), true);
            field(out, level + 2, "rightText", change.rightText(), false);
            indent(out, level + 1).append('}');
            if (i + 1 < changes.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        indent(out, level).append(']');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void appendEnumIntMap(StringBuilder out, int level, String name,
                                         Map<CloneRegionType, Integer> values,
                                         boolean comma) {
        indent(out, level).append('"').append(name).append("\": {\n");
        List<CloneRegionType> keys = values.keySet().stream()
                .sorted(java.util.Comparator.comparing(Enum::name))
                .toList();
        for (int i = 0; i < keys.size(); i++) {
            CloneRegionType key = keys.get(i);
            field(out, level + 1, key.name(), values.get(key), i + 1 < keys.size());
        }
        indent(out, level).append('}');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void appendEnumDoubleMap(StringBuilder out, int level, String name,
                                            Map<CloneRegionType, Double> values,
                                            boolean comma) {
        indent(out, level).append('"').append(name).append("\": {\n");
        List<CloneRegionType> keys = values.keySet().stream()
                .sorted(java.util.Comparator.comparing(Enum::name))
                .toList();
        for (int i = 0; i < keys.size(); i++) {
            CloneRegionType key = keys.get(i);
            field(out, level + 1, key.name(), values.get(key), i + 1 < keys.size());
        }
        indent(out, level).append('}');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void appendStringArray(StringBuilder out, int level, String name,
                                          List<String> values, boolean comma) {
        indent(out, level).append('"').append(name).append("\": [");
        for (int i = 0; i < values.size(); i++) {
            out.append('"').append(escape(values.get(i))).append('"');
            if (i + 1 < values.size()) {
                out.append(", ");
            }
        }
        out.append(']');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void field(StringBuilder out, int level, String name, String value, boolean comma) {
        indent(out, level).append('"').append(escape(name)).append("\": \"")
                .append(escape(value)).append('"');
        if (comma) out.append(',');
        out.append('\n');
    }

    private static void field(StringBuilder out, int level, String name, double value, boolean comma) {
        indent(out, level).append('"').append(escape(name)).append("\": ")
                .append(String.format(java.util.Locale.ROOT, "%.6f", value));
        if (comma) out.append(',');
        out.append('\n');
    }

    private static void field(StringBuilder out, int level, String name, int value, boolean comma) {
        indent(out, level).append('"').append(escape(name)).append("\": ").append(value);
        if (comma) out.append(',');
        out.append('\n');
    }

    private static void field(StringBuilder out, int level, String name, boolean value, boolean comma) {
        indent(out, level).append('"').append(escape(name)).append("\": ").append(value);
        if (comma) out.append(',');
        out.append('\n');
    }

    private static void stringFieldInline(StringBuilder out, String name, String value, boolean comma) {
        out.append('"').append(escape(name)).append("\": \"").append(escape(value)).append('"');
        if (comma) out.append(", ");
    }

    private static void numberFieldInline(StringBuilder out, String name, double value, boolean comma) {
        out.append('"').append(escape(name)).append("\": ")
                .append(String.format(java.util.Locale.ROOT, "%.6f", value));
        if (comma) out.append(", ");
    }

    private static StringBuilder indent(StringBuilder out, int level) {
        return out.append("  ".repeat(level));
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
