package com.ziqi.codesim.next.semantic.eval;

import com.ziqi.codesim.next.CloneRegionType;
import com.ziqi.codesim.next.NextPipelineResult;
import com.ziqi.codesim.next.semantic.WalaNextPipelineRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full-pipeline classification eval: runs each labelled file pair through the real
 * {@link WalaNextPipelineRunner} and compares the pipeline's headline verdict
 * ({@code fileSummary.dominantRegionType()}, collapsed to a T1/T2/T3/T4/NON_CLONE family) against the
 * expected family. Unlike {@code RegionEvalHarness} (which only measures whether Phase A recovered a
 * region), this measures whether the whole T1-T4 ladder assigns the RIGHT type.
 *
 * <p>Report-only by design: the value is the printed confusion matrix and the per-miss diagnostics
 * (exact sub-tier + decision path), which show WHERE the pipeline is wrong -- not a pass/fail gate.
 */
public final class PipelineClassificationHarness {

    private static final List<String> FAMILIES = List.of("T1", "T2", "T3", "T4", "NON_CLONE");

    private final WalaNextPipelineRunner runner;

    public PipelineClassificationHarness() {
        this(new WalaNextPipelineRunner());
    }

    public PipelineClassificationHarness(WalaNextPipelineRunner runner) {
        this.runner = runner;
    }

    /** One case's outcome: what the pipeline predicted vs what was expected. */
    public record Outcome(ClassificationCase evalCase, String predictedFamily,
                          CloneRegionType dominantType, boolean correct, String error,
                          List<String> decisionPath) {
    }

    public Outcome evaluate(ClassificationCase evalCase) {
        try {
            NextPipelineResult result = runner.run(evalCase.leftSource(), evalCase.rightSource());
            CloneRegionType dominant = result.fileSummary().dominantRegionType();
            String predicted = family(dominant);
            boolean correct = predicted.equals(evalCase.expectedFamily());
            return new Outcome(evalCase, predicted, dominant, correct, null,
                    strongestDecisionPath(result));
        } catch (Exception ex) {
            return new Outcome(evalCase, "ERROR", null, false,
                    ex.getClass().getSimpleName() + ": " + ex.getMessage(), List.of());
        }
    }

    public List<Outcome> evaluateAll(List<ClassificationCase> cases) {
        return cases.stream().map(this::evaluate).toList();
    }

    /** Collapse the pipeline's fine-grained type to the coarse clone family used for scoring. */
    public static String family(CloneRegionType type) {
        if (type == null) {
            return "ERROR";
        }
        return switch (type) {
            case T1 -> "T1";
            case T2 -> "T2";
            case T3 -> "T3";
            case T4_CONFIRMED, T4_DYNAMIC_EVIDENCE, POSSIBLE_T4_CANDIDATE -> "T4";
            case NON_CLONE -> "NON_CLONE";
        };
    }

    /** The decision path of the strongest selected region (for diagnosing a miss). */
    private static List<String> strongestDecisionPath(NextPipelineResult result) {
        return result.selectedRegionDecisions().stream()
                .findFirst()
                .map(d -> d.decisionPath())
                .orElseGet(() -> result.regionDecisions().stream()
                        .findFirst()
                        .map(d -> d.decisionPath())
                        .orElse(List.of()));
    }

    public String report(List<Outcome> outcomes) {
        StringBuilder out = new StringBuilder();
        out.append("== Full-Pipeline Classification Eval ==").append(System.lineSeparator());

        // Per-case table.
        out.append(String.format(Locale.ROOT, "%-20s %-11s %-11s %-24s %s%n",
                "case", "expected", "predicted", "dominantType", "result"));
        for (Outcome o : outcomes) {
            String mark = o.error() != null ? "ERROR" : (o.correct() ? "ok" : "MISS");
            out.append(String.format(Locale.ROOT, "%-20s %-11s %-11s %-24s %s%n",
                    o.evalCase().id(), o.evalCase().expectedFamily(), o.predictedFamily(),
                    o.dominantType() == null ? "-" : o.dominantType().name(), mark));
        }

        // Confusion matrix (rows = expected, cols = predicted).
        out.append(System.lineSeparator()).append("Confusion matrix (row=expected, col=predicted):")
                .append(System.lineSeparator());
        Map<String, Map<String, Integer>> matrix = confusion(outcomes);
        out.append(String.format(Locale.ROOT, "%-11s", ""));
        for (String col : FAMILIES) {
            out.append(String.format(Locale.ROOT, "%-11s", col));
        }
        out.append(System.lineSeparator());
        for (String row : FAMILIES) {
            out.append(String.format(Locale.ROOT, "%-11s", row));
            for (String col : FAMILIES) {
                out.append(String.format(Locale.ROOT, "%-11d", matrix.get(row).get(col)));
            }
            out.append(System.lineSeparator());
        }

        // Aggregate accuracy.
        long correct = outcomes.stream().filter(Outcome::correct).count();
        long errors = outcomes.stream().filter(o -> o.error() != null).count();
        out.append(System.lineSeparator())
                .append(String.format(Locale.ROOT, "Accuracy: %d/%d correct; %d crashes%n",
                        correct, outcomes.size(), errors));

        // Misclassification detail (the actual findings).
        out.append(System.lineSeparator()).append("Misses (why):").append(System.lineSeparator());
        boolean anyMiss = false;
        for (Outcome o : outcomes) {
            if (o.correct()) {
                continue;
            }
            anyMiss = true;
            out.append(String.format(Locale.ROOT, "  [%s] expected %s, got %s (%s) -- %s%n",
                    o.evalCase().id(), o.evalCase().expectedFamily(), o.predictedFamily(),
                    o.dominantType() == null ? "ERROR" : o.dominantType().name(), o.evalCase().note()));
            if (o.error() != null) {
                out.append("      error: ").append(o.error()).append(System.lineSeparator());
            }
            for (String step : o.decisionPath()) {
                out.append("      | ").append(step).append(System.lineSeparator());
            }
        }
        if (!anyMiss) {
            out.append("  (none)").append(System.lineSeparator());
        }
        return out.toString();
    }

    private static Map<String, Map<String, Integer>> confusion(List<Outcome> outcomes) {
        Map<String, Map<String, Integer>> matrix = new LinkedHashMap<>();
        for (String row : FAMILIES) {
            Map<String, Integer> cols = new LinkedHashMap<>();
            for (String col : FAMILIES) {
                cols.put(col, 0);
            }
            matrix.put(row, cols);
        }
        for (Outcome o : outcomes) {
            String expected = o.evalCase().expectedFamily();
            String predicted = FAMILIES.contains(o.predictedFamily()) ? o.predictedFamily() : null;
            if (!matrix.containsKey(expected) || predicted == null) {
                continue; // ERROR / off-grid prediction: excluded from the grid, counted as a crash above
            }
            matrix.get(expected).merge(predicted, 1, Integer::sum);
        }
        return matrix;
    }

    /** Convenience for callers that just want the printed report. */
    public String evaluateAndReport(List<ClassificationCase> cases) {
        List<Outcome> outcomes = new ArrayList<>(evaluateAll(cases));
        return report(outcomes);
    }
}
