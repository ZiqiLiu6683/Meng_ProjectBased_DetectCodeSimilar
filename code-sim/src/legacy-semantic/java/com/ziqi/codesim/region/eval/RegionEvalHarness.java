package com.ziqi.codesim.region.eval;

import com.ziqi.codesim.region.CloneAnalyzer;
import com.ziqi.codesim.region.grow.RegionGroup;
import com.ziqi.codesim.region.semantic.SemanticMethodMatch;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Runs the region selector over a corpus of injection cases and scores recovery. A case is
 * "detected" when some region in the top-K spans the expected donor and clone methods with at least
 * {@link #minCoverage} substantive coverage — so a trivial param/entry-only region linking unrelated
 * methods does NOT count as a detection. This is what lets the harness measure recall on positives
 * and false positives on negatives, the signal needed to drive later tuning.
 */
public final class RegionEvalHarness {

    private final CloneAnalyzer analyzer;
    private final int topK;
    private final double minCoverage;

    public RegionEvalHarness() {
        this(new CloneAnalyzer(), 3, 1.0);
    }

    public RegionEvalHarness(CloneAnalyzer analyzer, int topK, double minCoverage) {
        this.analyzer = analyzer;
        this.topK = topK;
        this.minCoverage = minCoverage;
    }

    public EvalResult evaluate(EvalCase evalCase) {
        try {
            CloneAnalyzer.Result analysis = analyzer.analyze(evalCase.leftSource(), evalCase.rightSource());

            // Structural detection: a top-K region linking the expected methods with real coverage.
            int rank = -1;
            RegionGroup matched = null;
            List<RegionGroup> regions = analysis.regions();
            for (int i = 0; i < regions.size(); i++) {
                RegionGroup region = regions.get(i);
                if (coversAll(region.leftMethods(), evalCase.leftMethodHints())
                        && coversAll(region.rightMethods(), evalCase.rightMethodHints())
                        && region.coverage() >= minCoverage) {
                    rank = i;
                    matched = region;
                    break;
                }
            }
            if (rank >= 0 && rank < topK) {
                return new EvalResult(evalCase, true, "structural", rank, matched.coverage(),
                        matched.leftMethods(), matched.rightMethods(), null);
            }

            // Semantic fallback: a proven-equivalent method pair linking the expected methods.
            Optional<SemanticMethodMatch> semantic = analysis.semanticMatches().stream()
                    .filter(m -> coversAll(Set.of(m.leftMethod()), evalCase.leftMethodHints())
                            && coversAll(Set.of(m.rightMethod()), evalCase.rightMethodHints()))
                    .findFirst();
            if (semantic.isPresent()) {
                return new EvalResult(evalCase, true, "semantic", -1, 0.0,
                        Set.of(semantic.get().leftMethod()), Set.of(semantic.get().rightMethod()), null);
            }

            return new EvalResult(evalCase, false, "none", -1, 0.0, Set.of(), Set.of(), null);
        } catch (Exception ex) {
            return new EvalResult(evalCase, false, "none", -1, 0.0, Set.of(), Set.of(),
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    public List<EvalResult> evaluateAll(List<EvalCase> cases) {
        return cases.stream().map(this::evaluate).toList();
    }

    private static boolean coversAll(Set<String> methods, List<String> hints) {
        return hints.stream().allMatch(hint -> methods.stream().anyMatch(m -> m.contains(hint)));
    }

    /** Human-readable report: per-case outcome plus aggregate recall and false-positive count. */
    public String report(List<EvalResult> results) {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "== Region Selector Evaluation (topK=%d, minCoverage=%.1f) ==%n",
                topK, minCoverage));
        out.append(String.format(Locale.ROOT, "%-16s %-16s %-7s %-9s %-11s %-5s %-9s %s%n",
                "case", "transform", "expect", "detected", "via", "rank", "coverage", "recoveredRight"));
        for (EvalResult result : results) {
            EvalCase c = result.evalCase();
            String detected = result.error() != null ? "ERROR" : (result.detected() ? "YES" : "no");
            out.append(String.format(Locale.ROOT, "%-16s %-16s %-7s %-9s %-11s %-5s %-9.2f %s%n",
                    c.id(), c.transformation(), c.expectClone() ? "clone" : "none",
                    detected, result.detectedVia(), result.rank() < 0 ? "-" : String.valueOf(result.rank()),
                    result.coverage(), result.recoveredRight()));
            if (result.error() != null) {
                out.append("    error: ").append(result.error()).append(System.lineSeparator());
            }
        }
        long positives = results.stream().filter(r -> r.evalCase().expectClone()).count();
        long truePositives = results.stream().filter(EvalResult::isTruePositive).count();
        long negatives = results.size() - positives;
        long falsePositives = results.stream().filter(EvalResult::isFalsePositive).count();
        out.append(String.format(Locale.ROOT, "Recall (positives detected): %d/%d%n", truePositives, positives));
        out.append(String.format(Locale.ROOT, "False positives (negatives detected): %d/%d%n",
                falsePositives, negatives));
        return out.toString();
    }
}
