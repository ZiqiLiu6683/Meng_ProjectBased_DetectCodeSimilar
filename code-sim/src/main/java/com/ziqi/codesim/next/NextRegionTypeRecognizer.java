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
    // Minimum substance-weighted coverage of a Phase A structural region group before it flags a
    // possible Type-4 candidate. A floor (>= ~two real computations aligned), like the grower's
    // minPairs -- not a similarity ratio -- so a trivial one-node structural coincidence does not
    // raise a candidate.
    private static final double MIN_STRUCTURAL_REGION_COVERAGE = 2.0;
    private static final Set<String> SOURCE_ONLY_CHANNELS = Set.of(
            "EXACT_TEXT_SCAN",
            "NORMALIZED_AST_SCAN",
            "NORMALIZED_TOKEN_OVERLAP_SCAN",
            "STATEMENT_DIFF_SCAN"
    );

    private final StructuralSimilarityOracle structuralOracle;
    private final SemanticEquivalenceOracle semanticOracle;
    private final StructuralRegionOracle structuralRegionOracle;
    private final DynamicEquivalenceOracle dynamicOracle;

    public NextRegionTypeRecognizer() {
        this(StructuralSimilarityOracle.NONE, SemanticEquivalenceOracle.NONE, StructuralRegionOracle.NONE,
                DynamicEquivalenceOracle.NONE);
    }

    public NextRegionTypeRecognizer(StructuralSimilarityOracle structuralOracle) {
        this(structuralOracle, SemanticEquivalenceOracle.NONE, StructuralRegionOracle.NONE,
                DynamicEquivalenceOracle.NONE);
    }

    public NextRegionTypeRecognizer(StructuralSimilarityOracle structuralOracle,
                                    SemanticEquivalenceOracle semanticOracle) {
        this(structuralOracle, semanticOracle, StructuralRegionOracle.NONE, DynamicEquivalenceOracle.NONE);
    }

    public NextRegionTypeRecognizer(StructuralSimilarityOracle structuralOracle,
                                    SemanticEquivalenceOracle semanticOracle,
                                    StructuralRegionOracle structuralRegionOracle) {
        this(structuralOracle, semanticOracle, structuralRegionOracle, DynamicEquivalenceOracle.NONE);
    }

    public NextRegionTypeRecognizer(StructuralSimilarityOracle structuralOracle,
                                    SemanticEquivalenceOracle semanticOracle,
                                    StructuralRegionOracle structuralRegionOracle,
                                    DynamicEquivalenceOracle dynamicOracle) {
        this.structuralOracle = structuralOracle;
        this.semanticOracle = semanticOracle;
        this.structuralRegionOracle = structuralRegionOracle;
        this.dynamicOracle = dynamicOracle;
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

        double syntacticSimilarity = syntacticSimilarity(left, right);

        // A candidate proposed ONLY by Phase B exists to carry behavioural evidence about a method
        // pair; it is not a claim that the two methods are syntactically alike. Letting it run the
        // syntactic layers would report a method-scoped T1/T2/T3, and a file pair gets no
        // method-scoped syntactic type -- that verdict belongs to the Phase A region covering the
        // same code, which is region-scoped by construction. So such a candidate skips straight to
        // the behavioural layers, where its only outcomes are the T4 family or NON_CLONE.
        // The cascade order itself is unchanged: T1/T2/T3 are still tried first for every candidate
        // that carries any syntactic evidence at all.
        if (isBehaviouralOnly(candidate)) {
            path.add("T1-T3 skipped: this candidate carries only behavioural method-pair evidence "
                    + "(" + behaviouralChannels(candidate) + "); a syntactic clone type at method "
                    + "scope is not part of the result contract, so only T4 evidence can apply.");
        } else {
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

            boolean t3ComparableScope =
                    hasCompleteComparableUnit(candidate) || hasExternalCandidateEvidence(candidate);
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
        }

        // T4: T1/T2/T3 did not approve a syntactic clone. Confirm a semantic (Type-4) clone only on
        // independent positive evidence -- an SMT proof that the two regions compute the same value
        // for all inputs. This is exactly the "independent semantic approval" the source-only slice
        // lacked; Phase B now supplies it.
        if (semanticOracle.provenEquivalent(left, right)) {
            tags.add(RegionTag.POSSIBLE_SEMANTIC_RELATION);
            path.add("T4 confirmed: an independent SMT proof shows the two regions compute the same "
                    + "value for all inputs, despite differing structure.");
            return decision(candidate, CloneRegionType.T4_CONFIRMED, CloneStrength.NONE, syntacticSimilarity,
                    structuralSimilarity, renameEvidence, editScript, tags, path);
        }

        // T4 by dynamic evidence: SMT could not prove equivalence (loops/nonlinear code), but the
        // dynamic layer ran both regions on the same inputs and they agreed on every one. This is
        // strong EVIDENCE, not a proof -- reported as its own tier, strictly below an SMT-confirmed T4.
        if (dynamicOracle.likelyEquivalent(left, right)) {
            tags.add(RegionTag.POSSIBLE_SEMANTIC_RELATION);
            path.add("T4 evidenced: I/O sampling ran the two regions on the same inputs and they agreed "
                    + "on every tested input (evidence, not proof; SMT could not prove it).");
            return decision(candidate, CloneRegionType.T4_DYNAMIC_EVIDENCE, CloneStrength.NONE, syntacticSimilarity,
                    structuralSimilarity, renameEvidence, editScript, tags, path);
        }

        // Possible T4: no equivalence proof, but Phase A found a strong boundary-free structural
        // region group aligning these methods (e.g. helper extraction -- one side inlines what the
        // other splits across a callee). Structural support without a behavioural proof is exactly
        // POSSIBLE_T4_CANDIDATE, not a confirmed clone.
        // Structural coverage comes either from a projected Phase A region (carried on the candidate
        // as a STRUCTURAL_REGION_SCAN source) or from the method-keyed oracle.
        double regionCoverage = Math.max(
                structuralRegionCoverageFrom(candidate),
                structuralRegionOracle.regionCoverage(left, right));
        if (regionCoverage >= MIN_STRUCTURAL_REGION_COVERAGE) {
            tags.add(RegionTag.POSSIBLE_SEMANTIC_RELATION);
            path.add(String.format(
                    "Possible T4: a Phase A cross-method structural region group aligns these methods "
                            + "with coverage %.2f, but no equivalence proof confirms it.", regionCoverage));
            return decision(candidate, CloneRegionType.POSSIBLE_T4_CANDIDATE, CloneStrength.NONE,
                    syntacticSimilarity, structuralSimilarity, renameEvidence, editScript, tags, path);
        }

        // A cross-method aligned region (helper extraction / reorganization) is a STRUCTURAL FACT
        // Phase A established. If T1-T3 and the behavioural checks did not settle a type, it must not
        // be silently dropped by a similarity number -- surface it as a tagged possible clone.
        if (hasCrossMethodRegionMarker(candidate)) {
            tags.add(RegionTag.POSSIBLE_SEMANTIC_RELATION);
            path.add("Possible T4: Phase A found a cross-method aligned region (helper extraction / "
                    + "reorganization), but no T1-T3 match or equivalence proof settled a type; "
                    + "surfaced as a possible clone rather than dropped.");
            return decision(candidate, CloneRegionType.POSSIBLE_T4_CANDIDATE, CloneStrength.NONE,
                    syntacticSimilarity, structuralSimilarity, renameEvidence, editScript, tags, path);
        }

        path.add("T4 not approved: no independent semantic-equivalence proof or structural region "
                + "evidence is attached to this candidate.");
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
                List.copyOf(path),
                subRegions(candidate.left(), candidate.right())
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

    private static boolean hasCrossMethodRegionMarker(RegionCandidate candidate) {
        return candidate.sources().stream()
                .anyMatch(source -> source.channel().equals("CROSS_METHOD_REGION"));
    }

    private static double structuralRegionCoverageFrom(RegionCandidate candidate) {
        return candidate.sources().stream()
                .filter(source -> source.channel().equals("STRUCTURAL_REGION_SCAN"))
                .mapToDouble(CandidateSource::score)
                .max()
                .orElse(0.0);
    }

    /**
     * Channels that only ever say "these two METHODS behave the same" -- Phase B's SMT proof and
     * its sampled-execution fallback. They carry no claim about syntax.
     */
    private static final Set<String> BEHAVIOURAL_METHOD_CHANNELS = Set.of(
            "SEMANTIC_EQUIV_SCAN",
            "DYNAMIC_EQUIV_SCAN"
    );

    /** True when every piece of evidence on this candidate is behavioural method-pair evidence. */
    private static boolean isBehaviouralOnly(RegionCandidate candidate) {
        return !candidate.sources().isEmpty() && candidate.sources().stream()
                .allMatch(source -> BEHAVIOURAL_METHOD_CHANNELS.contains(source.channel()));
    }

    private static String behaviouralChannels(RegionCandidate candidate) {
        return candidate.sources().stream()
                .map(CandidateSource::channel)
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static boolean hasExternalCandidateEvidence(RegionCandidate candidate) {
        return candidate.sources().stream()
                .map(CandidateSource::channel)
                .anyMatch(channel -> !SOURCE_ONLY_CHANNELS.contains(channel));
    }

    /**
     * The same T1 -> T2 -> T3 cascade, applied per aligned statement pair instead of per region.
     *
     * A grown region stops where the two sides stop corresponding, not where the KIND of difference
     * changes, so one region routinely mixes identical, renamed and edited statements and the
     * region-level verdict can only report the last of those. This recovers the breakdown WITHOUT
     * introducing a second classification rule: the alignment is the same LCS over
     * {@code normalizedStatementTexts} that produces the edit script, and the per-pair test is the
     * same pair of comparisons the region-level cascade makes.
     *
     * <p>Alignment on the T2 view is deliberate and must match the region-level decision: a
     * statement that only had identifiers renamed is MATCHED here (it is T2, not an edit), which is
     * exactly why renaming alone leaves the edit script empty and keeps a region at T2.
     *
     * <p>Returns empty when positions are unavailable or the parallel lists disagree -- an
     * off-by-one would attach real types to the wrong lines, which is worse than no breakdown.
     */
    static List<SubRegion> subRegions(CodeRegion left, CodeRegion right) {
        List<String> leftKeys = left.normalizedStatementTexts();
        List<String> rightKeys = right.normalizedStatementTexts();
        if (leftKeys.size() != left.statementTexts().size()
                || rightKeys.size() != right.statementTexts().size()
                || left.statementLines().size() != leftKeys.size()
                || right.statementLines().size() != rightKeys.size()
                || leftKeys.isEmpty() || rightKeys.isEmpty()) {
            return List.of();
        }

        List<SubRegion> items = new ArrayList<>();
        int[][] dp = lcsTable(leftKeys, rightKeys, 0, leftKeys.size(), 0, rightKeys.size());
        int i = 0;
        int j = 0;
        while (i < leftKeys.size() && j < rightKeys.size()) {
            if (leftKeys.get(i).equals(rightKeys.get(j))) {
                boolean identical = left.statementTexts().get(i).equals(right.statementTexts().get(j));
                items.add(new SubRegion(
                        identical ? CloneRegionType.T1 : CloneRegionType.T2,
                        List.of(left.statementLines().get(i)),
                        List.of(right.statementLines().get(j)),
                        identical
                                ? "T1: statement tokens are identical."
                                : "T2: statement matches after identifier/literal normalization."));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                items.add(new SubRegion(CloneRegionType.T3,
                        List.of(left.statementLines().get(i)), List.of(),
                        "T3: statement has no counterpart on the right (deleted)."));
                i++;
            } else {
                items.add(new SubRegion(CloneRegionType.T3,
                        List.of(), List.of(right.statementLines().get(j)),
                        "T3: statement has no counterpart on the left (inserted)."));
                j++;
            }
        }
        while (i < leftKeys.size()) {
            items.add(new SubRegion(CloneRegionType.T3,
                    List.of(left.statementLines().get(i)), List.of(),
                    "T3: statement has no counterpart on the right (deleted)."));
            i++;
        }
        while (j < rightKeys.size()) {
            items.add(new SubRegion(CloneRegionType.T3,
                    List.of(), List.of(right.statementLines().get(j)),
                    "T3: statement has no counterpart on the left (inserted)."));
            j++;
        }
        return coalesce(items);
    }

    /** Merge neighbouring runs of the same type; separate entries would imply a boundary that is not there. */
    private static List<SubRegion> coalesce(List<SubRegion> items) {
        List<SubRegion> merged = new ArrayList<>();
        for (SubRegion item : items) {
            if (merged.isEmpty()) {
                merged.add(item);
                continue;
            }
            SubRegion last = merged.get(merged.size() - 1);
            if (last.type() != item.type()) {
                merged.add(item);
                continue;
            }
            List<LineSegment> l = new ArrayList<>(last.left());
            l.addAll(item.left());
            List<LineSegment> r = new ArrayList<>(last.right());
            r.addAll(item.right());
            merged.set(merged.size() - 1, new SubRegion(item.type(), l, r, last.reason()));
        }
        return List.copyOf(merged);
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
