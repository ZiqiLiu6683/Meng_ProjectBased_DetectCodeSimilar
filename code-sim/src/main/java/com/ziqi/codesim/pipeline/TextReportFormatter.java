package com.ziqi.codesim.pipeline;

import java.util.List;
import java.util.stream.Collectors;

public class TextReportFormatter {
    public String format(FullPipelineResult result, String fileA, String fileB) {
        StringBuilder out = new StringBuilder();
        appendHeader(out, result, fileA, fileB);
        appendDecision(out, result.stage4());
        appendEvidence(out, result.stage4().evidenceChain());
        appendMethodPairs(out, result.stage3().mergedPairs());
        appendDiagnostics(out, result.stage3());
        appendPipelineDetails(out, result);
        return out.toString();
    }

    private static void appendHeader(StringBuilder out, FullPipelineResult result,
                                     String fileA, String fileB) {
        out.append("=== Code Similarity Report ===\n");
        out.append("File A: ").append(fileA).append('\n');
        out.append("File B: ").append(fileB).append('\n');
        out.append("Mode  : ").append(result.stage0().primaryMode()).append('\n');
        out.append("Flags : ").append(formatList(result.stage0().flags().stream()
                .map(Enum::name)
                .sorted()
                .toList())).append('\n');
        out.append("Signals: ").append(result.stage0().enabledSignals()).append("\n\n");
    }

    private static void appendDecision(StringBuilder out, Stage4Result stage4) {
        out.append("=== Final Decision ===\n");
        out.append("Clone Type          : ").append(stage4.cloneType()).append('\n');
        out.append("Scope               : ").append(stage4.scopeType()).append('\n');
        out.append("Containment         : ").append(stage4.containmentDirection()).append('\n');
        out.append("Confidence          : ").append(percent(stage4.confidence()))
                .append(" (").append(stage4.confidenceLevel()).append(")\n");
        out.append("Scope Confidence    : ").append(percent(stage4.scopeConfidence()))
                .append(" (").append(stage4.scopeConfidenceLevel()).append(")\n");
        out.append("Evidence strength   : ").append(percent(stage4.evidenceStrength())).append('\n');
        out.append("Evidence consistency: ").append(percent(stage4.evidenceConsistency())).append('\n');
        out.append("Pipeline reliability: ").append(percent(stage4.pipelineReliability())).append("\n\n");
    }

    private static void appendEvidence(StringBuilder out, EvidenceChain evidence) {
        out.append("=== Evidence Chain ===\n");
        appendEvidenceSection(out, "Supporting Evidence", evidence.supportingEvidence());
        appendEvidenceSection(out, "Opposing Evidence", evidence.opposingEvidence());
        appendEvidenceSection(out, "Scope Evidence", evidence.scopeEvidence());
        appendEvidenceSection(out, "Reliability Warnings", evidence.reliabilityWarnings());
        out.append('\n');
    }

    private static void appendEvidenceSection(StringBuilder out, String title,
                                              List<EvidenceItem> items) {
        out.append("-- ").append(title).append(" --\n");
        if (items.isEmpty()) {
            out.append("  (none)\n");
            return;
        }
        for (EvidenceItem item : items) {
            out.append("  - ").append(item.signal())
                    .append(" = ").append(item.value())
                    .append(" [").append(item.strength()).append("] ")
                    .append(item.interpretation())
                    .append(" Supports: ").append(item.supports())
                    .append('\n');
        }
    }

    private static void appendMethodPairs(StringBuilder out, List<MergedPairFeature> pairs) {
        out.append("=== Method Correspondence ===\n");
        if (pairs.isEmpty()) {
            out.append("(no method-level correspondences)\n\n");
            return;
        }
        out.append(String.format(
                "%-28s %-28s %-16s %8s %14s %8s %8s %8s %8s %10s %10s %8s%n",
                "A Method", "B Method", "Direction", "Match", "Reason",
                "S2", "S3", "S4", "Mag", "A_in_B", "B_in_A", "Size"));
        for (MergedPairFeature pair : pairs) {
            MethodPairFeature f = pair.feature();
            out.append(String.format(
                    "%-28s %-28s %-16s %8.2f %14s %8.2f %8.2f %8.2f %8.2f %10.2f %10.2f %8.2f%n",
                    pair.methodAId(),
                    pair.methodBId(),
                    pair.direction(),
                    pair.matchScore() * 100.0,
                    pair.matchReason(),
                    f.s2() * 100.0,
                    f.s3() * 100.0,
                    f.s4() * 100.0,
                    f.magnitude() * 100.0,
                    f.containmentAInB() * 100.0,
                    f.containmentBInA() * 100.0,
                    f.sizeRatio() * 100.0
            ));
        }
        out.append('\n');
    }

    private static void appendDiagnostics(StringBuilder out, Stage3Result stage3) {
        out.append("=== Diagnostic Feature Summary ===\n");
        out.append("-- Overall --\n");
        out.append("  magnitude_avg      : ").append(percent(stage3.magnitudeAvg())).append('\n');
        out.append("  match_score_avg    : ").append(percent(stage3.matchScoreAvg())).append('\n');
        out.append("  centroid_S3        : ").append(percent(stage3.centroidS3())).append('\n');
        out.append("  centroid_S4        : ").append(percent(stage3.centroidS4())).append('\n');
        out.append("-- Type Diagnostics --\n");
        out.append("  structural_exactness_avg : ").append(percent(stage3.structuralExactnessAvg())).append('\n');
        out.append("  token_exact_gap_avg      : ").append(percent(stage3.tokenExactGapAvg())).append('\n');
        out.append("  spread_avg               : ").append(percent(stage3.spreadAvg())).append('\n');
        out.append("-- Coverage --\n");
        out.append("  coverage_A          : ").append(percent(stage3.coverageA())).append('\n');
        out.append("  coverage_B          : ").append(percent(stage3.coverageB())).append('\n');
        out.append("  confirmed_coverage_A: ").append(percent(stage3.confirmedCoverageA())).append('\n');
        out.append("  confirmed_coverage_B: ").append(percent(stage3.confirmedCoverageB())).append('\n');
        out.append("  confirmed_ratio     : ").append(percent(stage3.confirmedRatio())).append('\n');
        out.append("-- Partial --\n");
        out.append("  partial_clone_signal: ").append(percent(stage3.partialCloneSignal())).append('\n');
        out.append("  partial_A_in_B      : ").append(percent(stage3.partialAInB())).append('\n');
        out.append("  partial_B_in_A      : ").append(percent(stage3.partialBInA())).append('\n');
        out.append("-- File Level --\n");
        out.append("  S1                  : ").append(percent(stage3.s1())).append('\n');
        out.append("  S5                  : ");
        if (stage3.s5Status() == SignalStatus.APPLICABLE) {
            out.append(percent(stage3.s5())).append('\n');
        } else {
            out.append(stage3.s5Status()).append('\n');
        }
        out.append('\n');
    }

    private static void appendPipelineDetails(StringBuilder out, FullPipelineResult result) {
        out.append("=== Pipeline Details ===\n");
        out.append("Stage0 methods : A=").append(result.stage0().methodCountA())
                .append(", B=").append(result.stage0().methodCountB()).append('\n');
        out.append("Stage0 AST nodes: A=").append(result.stage0().totalAstNodesA())
                .append(", B=").append(result.stage0().totalAstNodesB()).append('\n');
        out.append("Stage1 pair count: ").append(result.stage1().pairMatrix().size()).append('\n');
        out.append("Stage1 exact-normalized match: ")
                .append(result.stage1().fileExactNormalizedMatch()).append('\n');
        out.append("Stage2 status: ").append(result.stage2().status())
                .append(", feature count: ").append(result.stage2().pairFeatures().size()).append('\n');
        out.append("Stage3 status: ").append(result.stage3().status())
                .append(", merged pair count: ").append(result.stage3().mergedPairs().size()).append('\n');
        out.append("Stage3 flags: ").append(formatList(result.stage3().flags().stream()
                .map(Enum::name)
                .sorted()
                .toList())).append('\n');
    }

    private static String percent(double value) {
        return String.format("%.2f%%", value * 100.0);
    }

    private static String formatList(List<String> values) {
        if (values.isEmpty()) return "(none)";
        return values.stream().collect(Collectors.joining(", "));
    }
}
