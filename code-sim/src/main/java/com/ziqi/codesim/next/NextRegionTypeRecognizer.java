package com.ziqi.codesim.next;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class NextRegionTypeRecognizer {
    // The only inherent threshold: the BigCloneBench Type-3 syntactic-similarity boundary that
    // separates a near-miss clone from unrelated code. Everything else is reported as raw
    // numbers for the user to judge, with no system-imposed cutoffs.
    private static final double BIGCLONEBENCH_T3_MIN_SYNTACTIC_SIMILARITY = 0.50;
    private static final Set<String> SOURCE_ONLY_CHANNELS = Set.of(
            "EXACT_TEXT_SCAN",
            "NORMALIZED_AST_SCAN",
            "NORMALIZED_TOKEN_KNN_SCAN",
            "STATEMENT_DIFF_SCAN"
    );

    private final StructuralSimilarityOracle structuralOracle;

    public NextRegionTypeRecognizer() {
        this(StructuralSimilarityOracle.NONE);
    }

    public NextRegionTypeRecognizer(StructuralSimilarityOracle structuralOracle) {
        this.structuralOracle = structuralOracle;
    }

    public RegionDecision decide(RegionCandidate candidate) {
        CodeRegion left = candidate.left();
        CodeRegion right = candidate.right();
        List<String> path = new ArrayList<>();
        Set<RegionTag> tags = EnumSet.noneOf(RegionTag.class);
        RenameEvidence renameEvidence = NextEvidenceExtractor.renameEvidence(left, right);
        if (renameEvidence.detected()) {
            tags.add(RegionTag.RENAMING_DETECTED);
        }
        if (NextEvidenceExtractor.literalChanged(left, right)) {
            tags.add(RegionTag.LITERAL_CHANGED);
        }
        if (NextEvidenceExtractor.identifierChanged(left, right)) {
            tags.add(RegionTag.RENAMING_DETECTED);
        }

        StatementEditScript editScript = statementEditScript(
                left.normalizedStatementTexts(),
                right.normalizedStatementTexts()
        );
        addStatementTags(tags, editScript);

        // Raw method-CFG structural similarity (approximate MCS), surfaced as information for the
        // user to weigh. NaN when it does not apply (non-method regions or the source-only
        // pipeline). It is NEVER used to change the type -- no structural threshold/verdict.
        double structuralSimilarity = structuralOracle.similarity(left, right).orElse(Double.NaN);
        if (!Double.isNaN(structuralSimilarity)) {
            path.add(String.format(
                    "Method CFG structural similarity: %.4f (informational; not used to change the type).",
                    structuralSimilarity));
        }

        if (!left.t1ComparableTokens().isEmpty()
                && left.t1ComparableTokens().equals(right.t1ComparableTokens())) {
            tags.add(RegionTag.EXACT_COPY);
            path.add("T1 passed: T1 comparable token sequences are 100% identical.");
            return decision(candidate, CloneRegionType.T1, CloneStrength.NONE, 1.0,
                    structuralSimilarity, renameEvidence, editScript, tags, path);
        }
        path.add("T1 failed: T1 comparable token sequences are not 100% identical.");

        if (!left.t2NormalizedTokens().isEmpty()
                && left.t2NormalizedTokens().equals(right.t2NormalizedTokens())
                && !editScript.hasAnyChange()) {
            path.add("T2 passed: T2 normalized token sequences are 100% identical and statement edit script is empty.");
            return decision(candidate, CloneRegionType.T2, CloneStrength.NONE, 1.0,
                    structuralSimilarity, renameEvidence, editScript, tags, path);
        }
        path.add("T2 failed: T2 normalized token sequences are not 100% identical or statement edits exist.");

        double syntacticSimilarity = syntacticSimilarity(left, right);
        boolean t3ComparableScope = hasCompleteComparableUnit(candidate) || hasExternalCandidateEvidence(candidate);
        if (editScript.hasAnyChange()
                && syntacticSimilarity >= BIGCLONEBENCH_T3_MIN_SYNTACTIC_SIMILARITY
                && t3ComparableScope) {
            CloneStrength strength = strength(syntacticSimilarity);
            path.add(String.format(
                    "T3 passed: statement edit script has insert/delete/modify evidence and syntactic similarity %.4f is in the BigCloneBench Type-3 range.",
                    syntacticSimilarity
            ));
            return decision(candidate, CloneRegionType.T3, strength, syntacticSimilarity,
                    structuralSimilarity, renameEvidence, editScript, tags, path);
        }
        if (editScript.hasAnyChange()
                && syntacticSimilarity >= BIGCLONEBENCH_T3_MIN_SYNTACTIC_SIMILARITY
                && !t3ComparableScope) {
            path.add(String.format(
                    "T3 held: local source-only window has statement edits and syntactic similarity %.4f, but no complete comparable unit or external CFG/semantic signal approved it.",
                    syntacticSimilarity
            ));
        }
        path.add(String.format(
                "T3 failed: statement edit evidence is absent or syntactic similarity %.4f is below 0.5000.",
                syntacticSimilarity
        ));

        path.add("T4 not approved in this source-only slice: no independent CFG/dynamic semantic approval is attached to this candidate.");
        return decision(candidate, CloneRegionType.NON_CLONE, CloneStrength.NONE, syntacticSimilarity,
                structuralSimilarity, renameEvidence, editScript, tags, path);
    }

    private static RegionDecision decision(RegionCandidate candidate,
                                           CloneRegionType type,
                                           CloneStrength strength,
                                           double syntacticSimilarity,
                                           double structuralSimilarity,
                                           RenameEvidence renameEvidence,
                                           StatementEditScript editScript,
                                           Set<RegionTag> tags,
                                           List<String> path) {
        return new RegionDecision(
                candidate,
                type,
                strength,
                syntacticSimilarity,
                structuralSimilarity,
                renameEvidence,
                editScript,
                Set.copyOf(tags),
                List.copyOf(path)
        );
    }

    private static double syntacticSimilarity(CodeRegion left, CodeRegion right) {
        double tokenSimilarity = NextEvidenceExtractor.lcsSimilarity(
                left.t2NormalizedTokens(),
                right.t2NormalizedTokens()
        );
        double lineSimilarity = NextEvidenceExtractor.lcsSimilarity(
                left.normalizedStatementTexts(),
                right.normalizedStatementTexts()
        );
        return Math.min(tokenSimilarity, lineSimilarity);
    }

    private static CloneStrength strength(double syntacticSimilarity) {
        if (syntacticSimilarity >= 0.90) {
            return CloneStrength.VST3;
        }
        if (syntacticSimilarity >= 0.70) {
            return CloneStrength.ST3;
        }
        if (syntacticSimilarity >= 0.50) {
            return CloneStrength.MT3;
        }
        return CloneStrength.WT3_T4_BOUNDARY;
    }

    private static boolean hasCompleteComparableUnit(RegionCandidate candidate) {
        return isCompleteComparableUnit(candidate.left().kind())
                || isCompleteComparableUnit(candidate.right().kind());
    }

    private static boolean isCompleteComparableUnit(RegionKind kind) {
        return kind == RegionKind.FILE
                || kind == RegionKind.METHOD
                || kind == RegionKind.METHOD_BODY_REGION
                || kind == RegionKind.CALL_EXPANDED_REGION;
    }

    private static boolean hasExternalCandidateEvidence(RegionCandidate candidate) {
        return candidate.sources().stream()
                .map(CandidateSource::channel)
                .anyMatch(channel -> !SOURCE_ONLY_CHANNELS.contains(channel));
    }

    private static void addStatementTags(Set<RegionTag> tags, StatementEditScript editScript) {
        if (editScript.hasInserted()) {
            tags.add(RegionTag.STATEMENT_INSERTED);
        }
        if (editScript.hasDeleted()) {
            tags.add(RegionTag.STATEMENT_DELETED);
        }
        if (editScript.hasModified()) {
            tags.add(RegionTag.STATEMENT_MODIFIED);
        }
    }

    static StatementEditScript statementEditScript(List<String> left, List<String> right) {
        List<StatementChange> changes = new ArrayList<>();
        collectChanges(left, right, 0, left.size(), 0, right.size(), changes);
        return new StatementEditScript(List.copyOf(changes));
    }

    private static void collectChanges(List<String> left, List<String> right,
                                       int leftStart, int leftEnd,
                                       int rightStart, int rightEnd,
                                       List<StatementChange> changes) {
        int[][] dp = lcsTable(left, right, leftStart, leftEnd, rightStart, rightEnd);
        int i = leftStart;
        int j = rightStart;
        int unmatchedLeftStart = -1;
        int unmatchedRightStart = -1;
        while (i < leftEnd && j < rightEnd) {
            if (left.get(i).equals(right.get(j))) {
                flushUnmatched(left, right, unmatchedLeftStart, i,
                        unmatchedRightStart, j, changes);
                unmatchedLeftStart = -1;
                unmatchedRightStart = -1;
                i++;
                j++;
            } else if (dp[i + 1 - leftStart][j - rightStart]
                    >= dp[i - leftStart][j + 1 - rightStart]) {
                if (unmatchedLeftStart < 0) {
                    unmatchedLeftStart = i;
                }
                i++;
            } else {
                if (unmatchedRightStart < 0) {
                    unmatchedRightStart = j;
                }
                j++;
            }
        }
        if (i < leftEnd && unmatchedLeftStart < 0) {
            unmatchedLeftStart = i;
        }
        if (j < rightEnd && unmatchedRightStart < 0) {
            unmatchedRightStart = j;
        }
        flushUnmatched(left, right, unmatchedLeftStart, leftEnd,
                unmatchedRightStart, rightEnd, changes);
    }

    private static void flushUnmatched(List<String> left, List<String> right,
                                       int leftStart, int leftEnd,
                                       int rightStart, int rightEnd,
                                       List<StatementChange> changes) {
        boolean hasLeft = leftStart >= 0 && leftStart < leftEnd;
        boolean hasRight = rightStart >= 0 && rightStart < rightEnd;
        if (hasLeft && hasRight) {
            int pairs = Math.min(leftEnd - leftStart, rightEnd - rightStart);
            for (int offset = 0; offset < pairs; offset++) {
                changes.add(new StatementChange(
                        StatementChangeKind.MODIFIED,
                        left.get(leftStart + offset),
                        right.get(rightStart + offset)
                ));
            }
            for (int i = leftStart + pairs; i < leftEnd; i++) {
                changes.add(new StatementChange(StatementChangeKind.DELETED, left.get(i), ""));
            }
            for (int j = rightStart + pairs; j < rightEnd; j++) {
                changes.add(new StatementChange(StatementChangeKind.INSERTED, "", right.get(j)));
            }
        } else if (hasLeft) {
            for (int i = leftStart; i < leftEnd; i++) {
                changes.add(new StatementChange(StatementChangeKind.DELETED, left.get(i), ""));
            }
        } else if (hasRight) {
            for (int j = rightStart; j < rightEnd; j++) {
                changes.add(new StatementChange(StatementChangeKind.INSERTED, "", right.get(j)));
            }
        }
    }

    private static int[][] lcsTable(List<String> left, List<String> right,
                                    int leftStart, int leftEnd,
                                    int rightStart, int rightEnd) {
        int[][] dp = new int[leftEnd - leftStart + 1][rightEnd - rightStart + 1];
        for (int i = leftEnd - 1; i >= leftStart; i--) {
            for (int j = rightEnd - 1; j >= rightStart; j--) {
                dp[i - leftStart][j - rightStart] = left.get(i).equals(right.get(j))
                        ? 1 + dp[i + 1 - leftStart][j + 1 - rightStart]
                        : Math.max(dp[i + 1 - leftStart][j - rightStart],
                        dp[i - leftStart][j + 1 - rightStart]);
            }
        }
        return dp;
    }
}
