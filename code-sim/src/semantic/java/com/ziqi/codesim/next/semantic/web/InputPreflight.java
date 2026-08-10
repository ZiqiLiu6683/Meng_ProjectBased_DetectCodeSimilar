package com.ziqi.codesim.next.semantic.web;

import com.ziqi.codesim.next.CodeRegion;
import com.ziqi.codesim.next.EvidencePackage;
import com.ziqi.codesim.next.NextEvidenceExtractor;
import com.ziqi.codesim.next.RegionKind;
import com.ziqi.codesim.next.semantic.AnalysisOptions;

import java.util.List;

/**
 * Read-only source/AST sizing used to recommend a web analysis mode before a costly request starts.
 * This does not classify clones or alter either detector pipeline.
 */
public final class InputPreflight {

    /**
     * Temporary, deliberately conservative web guardrail. A measured 310,250-candidate pair took
     * 6.3 seconds while a 5,082,391-candidate pair exhausted the server heap. Keep this visible in
     * the API so the UI never presents it as a detector or research threshold.
     */
    public static final long QUICK_COMPARISON_BUDGET = 300_000L;
    private static final long LOW_WORKLOAD_LIMIT = 50_000L;

    private InputPreflight() {
    }

    public static Result inspect(String leftSource, String rightSource) {
        EvidencePackage evidence = new NextEvidenceExtractor().extract(leftSource, rightSource);
        SideMetrics left = metrics(leftSource, evidence.leftRegions());
        SideMetrics right = metrics(rightSource, evidence.rightRegions());
        Decision decision = decisionForRegionCounts(left.regions(), right.regions());
        return new Result(left, right, decision.comparisonUpperBound(), QUICK_COMPARISON_BUDGET,
                decision.workload(), decision.recommendedMode(), decision.quickAllowed());
    }

    static Decision decisionForRegionCounts(int leftRegions, int rightRegions) {
        long comparisons = comparisonUpperBound(leftRegions, rightRegions);
        boolean quickAllowed = comparisons <= QUICK_COMPARISON_BUDGET;
        Workload workload = comparisons <= LOW_WORKLOAD_LIMIT
                ? Workload.LOW
                : quickAllowed ? Workload.MODERATE : Workload.HIGH;
        AnalysisOptions.AnalysisDepth recommended = quickAllowed
                ? AnalysisOptions.AnalysisDepth.SOURCE_AST
                : AnalysisOptions.AnalysisDepth.WALA_REGIONS;
        return new Decision(comparisons, workload, recommended, quickAllowed);
    }

    /** Mirrors NextCandidateDiscovery.comparable without materializing any candidate objects. */
    static long comparisonUpperBound(int leftRegions, int rightRegions) {
        long leftNonFile = Math.max(0L, leftRegions - 1L);
        long rightNonFile = Math.max(0L, rightRegions - 1L);
        try {
            return Math.addExact(Math.multiplyExact(leftNonFile, rightNonFile),
                    leftRegions > 0 && rightRegions > 0 ? 1L : 0L);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static SideMetrics metrics(String source, List<CodeRegion> regions) {
        int methods = (int) regions.stream().filter(region -> region.kind() == RegionKind.METHOD).count();
        return new SideMetrics(lineCount(source), source.length(), methods, regions.size());
    }

    private static int lineCount(String source) {
        if (source.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    public enum Workload {
        LOW,
        MODERATE,
        HIGH
    }

    public record SideMetrics(int lines, int characters, int methods, int regions) {
    }

    record Decision(long comparisonUpperBound, Workload workload,
                    AnalysisOptions.AnalysisDepth recommendedMode, boolean quickAllowed) {
    }

    public record Result(SideMetrics left, SideMetrics right, long quickComparisonUpperBound,
                         long quickComparisonBudget, Workload workload,
                         AnalysisOptions.AnalysisDepth recommendedMode, boolean quickAllowed) {
    }
}
