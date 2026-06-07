package com.ziqi.codesim.next;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AcceptedRegionSelector {
    private static final double MAX_OVERLAP_RATIO = 0.80;

    public SelectedRegionDecisions select(List<RegionDecision> rankedDecisions) {
        List<RegionDecision> accepted = rankedDecisions.stream()
                .filter(decision -> decision.type() != CloneRegionType.NON_CLONE)
                .toList();
        List<RegionDecision> selected = new ArrayList<>();
        for (RegionDecision candidate : accepted) {
            if (isRedundant(candidate, selected)) {
                continue;
            }
            selected.add(candidate);
        }
        return new SelectedRegionDecisions(
                List.copyOf(selected),
                new RegionSelectionSummary(
                        accepted.size(),
                        selected.size(),
                        accepted.size() - selected.size()
                )
        );
    }

    private static boolean isRedundant(RegionDecision candidate,
                                       List<RegionDecision> selected) {
        for (RegionDecision existing : selected) {
            if (sameSideOverlap(candidate.candidate().left(), existing.candidate().left())
                    && sameSideOverlap(candidate.candidate().right(), existing.candidate().right())) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameSideOverlap(CodeRegion candidate, CodeRegion existing) {
        if (candidate.side() != existing.side()) {
            return false;
        }
        double overlap = overlapRatio(candidate, existing);
        return overlap >= MAX_OVERLAP_RATIO
                || lowerPriorityContained(candidate, existing, overlap);
    }

    private static boolean lowerPriorityContained(CodeRegion candidate,
                                                  CodeRegion existing,
                                                  double overlap) {
        return overlap > 0.0
                && kindPriority(candidate.kind()) < kindPriority(existing.kind())
                && candidate.beginLine() >= existing.beginLine()
                && candidate.endLine() <= existing.endLine();
    }

    private static double overlapRatio(CodeRegion candidate, CodeRegion existing) {
        if (candidate.beginLine() < 0 || existing.beginLine() < 0
                || candidate.endLine() < candidate.beginLine()
                || existing.endLine() < existing.beginLine()) {
            return tokenOverlapRatio(candidate, existing);
        }
        int start = Math.max(candidate.beginLine(), existing.beginLine());
        int end = Math.min(candidate.endLine(), existing.endLine());
        if (end < start) {
            return 0.0;
        }
        double overlap = end - start + 1.0;
        double candidateSpan = candidate.endLine() - candidate.beginLine() + 1.0;
        return candidateSpan <= 0.0 ? 0.0 : overlap / candidateSpan;
    }

    private static double tokenOverlapRatio(CodeRegion candidate, CodeRegion existing) {
        if (candidate.t2NormalizedTokens().isEmpty()) {
            return 0.0;
        }
        Set<String> candidateTokens = new HashSet<>(candidate.t2NormalizedTokens());
        Set<String> existingTokens = new HashSet<>(existing.t2NormalizedTokens());
        candidateTokens.retainAll(existingTokens);
        return (double) candidateTokens.size() / candidate.t2NormalizedTokens().size();
    }

    private static int kindPriority(RegionKind kind) {
        return switch (kind) {
            case METHOD -> 6;
            case METHOD_BODY_REGION -> 5;
            case CALL_EXPANDED_REGION -> 5;
            case BLOCK_SEQUENCE_REGION -> 4;
            case CONTROL_REGION -> 3;
            case STATEMENT_WINDOW_REGION -> 2;
            case FILE -> 1;
        };
    }
}
