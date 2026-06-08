package com.ziqi.codesim.next;

import java.util.List;
import java.util.Map;

public class NextJsonReportFormatter {
    private static final int DEFAULT_MAX_REGIONS = 25;
    private final int maxRegions;

    public NextJsonReportFormatter() {
        this(DEFAULT_MAX_REGIONS);
    }

    public NextJsonReportFormatter(int maxRegions) {
        this.maxRegions = maxRegions;
    }

    public String format(NextPipelineResult result) {
        StringBuilder out = new StringBuilder();
        List<RegionDecision> emittedDecisions = result.selectedRegionDecisions().stream()
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
        for (int i = 0; i < emittedDecisions.size(); i++) {
            appendRegion(out, emittedDecisions.get(i), 2);
            if (i + 1 < emittedDecisions.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        indent(out, 1).append("]\n");
        out.append("}\n");
        return out.toString();
    }

    private static void appendFileSummary(StringBuilder out, FileCloneSummary summary, int level) {
        indent(out, level).append("\"fileSummary\": {\n");
        field(out, level + 1, "inspectionPriority", summary.inspectionPriority().name(), true);
        field(out, level + 1, "relationshipShape", summary.relationshipShape().name(), true);
        appendAffectedContent(out, level + 1, summary, true);
        appendEvidenceBreakdown(out, level + 1, summary.evidenceBreakdown(), true);
        field(out, level + 1, "overallRelationship", summary.overallRelationship().name(), true);
        field(out, level + 1, "dominantRegionType", summary.dominantRegionType().name(), true);
        field(out, level + 1, "matchedCoverageLeft", summary.matchedCoverageLeft(), true);
        field(out, level + 1, "matchedCoverageRight", summary.matchedCoverageRight(), true);
        field(out, level + 1, "unrelatedCodeRatio", summary.unrelatedCodeRatio(), true);
        appendEnumIntMap(out, level + 1, "regionTypeCounts", summary.regionTypeCounts(), true);
        appendEnumDoubleMap(out, level + 1, "regionTypeCoverage", summary.regionTypeCoverage(), true);
        appendStringArray(out, level + 1, "fileTags",
                summary.fileTags().stream().map(Enum::name).sorted().toList(), false);
        indent(out, level).append('}');
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

    private static void appendEvidenceBreakdown(StringBuilder out, int level,
                                                List<EvidenceBreakdown> evidenceBreakdown,
                                                boolean comma) {
        indent(out, level).append("\"evidenceBreakdown\": [\n");
        for (int i = 0; i < evidenceBreakdown.size(); i++) {
            EvidenceBreakdown breakdown = evidenceBreakdown.get(i);
            indent(out, level + 1).append("{\n");
            field(out, level + 2, "type", breakdown.type().name(), true);
            field(out, level + 2, "regionCount", breakdown.regionCount(), true);
            field(out, level + 2, "affectedLeftRatio", breakdown.affectedLeftRatio(), true);
            field(out, level + 2, "affectedRightRatio", breakdown.affectedRightRatio(), false);
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

    private static void appendRegion(StringBuilder out, RegionDecision decision, int level) {
        indent(out, level).append("{\n");
        field(out, level + 1, "candidateId", decision.candidate().candidateId(), true);
        field(out, level + 1, "type", decision.type().name(), true);
        field(out, level + 1, "strength", decision.strength().name(), true);
        field(out, level + 1, "syntacticSimilarity", decision.syntacticSimilarity(), true);
        appendRegionEndpoint(out, level + 1, "left", decision.candidate().left(), true);
        appendRegionEndpoint(out, level + 1, "right", decision.candidate().right(), true);
        appendSources(out, level + 1, decision.candidate().sources(), true);
        appendStringArray(out, level + 1, "tags",
                decision.tags().stream().map(Enum::name).sorted().toList(), true);
        appendRenameEvidence(out, level + 1, decision.renameEvidence(), true);
        appendStatementChanges(out, level + 1, decision.statementEditScript(), true);
        appendStringArray(out, level + 1, "decisionPath", decision.decisionPath(), false);
        indent(out, level).append('}');
    }

    private static void appendRegionEndpoint(StringBuilder out, int level, String name,
                                             CodeRegion region, boolean comma) {
        indent(out, level).append('"').append(name).append("\": {\n");
        field(out, level + 1, "regionId", region.regionId(), true);
        field(out, level + 1, "kind", region.kind().name(), true);
        field(out, level + 1, "displayName", region.displayName(), true);
        field(out, level + 1, "beginLine", region.beginLine(), true);
        field(out, level + 1, "endLine", region.endLine(), false);
        indent(out, level).append('}');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
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
