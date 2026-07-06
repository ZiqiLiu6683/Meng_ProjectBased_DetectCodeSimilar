package com.ziqi.codesim.next.semantic.eval;

import com.ziqi.codesim.next.CloneRegionType;
import com.ziqi.codesim.next.NextPipelineResult;
import com.ziqi.codesim.next.RegionDecision;
import com.ziqi.codesim.next.RegionKind;
import com.ziqi.codesim.next.semantic.WalaNextPipelineRunner;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Region-level classification eval. There is NO file-level clone type: the input is a pair of files
 * (you always compare two files), but the verdict is judged per REGION. For each case we collect the
 * clone types the pipeline assigned to Phase A regions (T1/T2/T3), plus method-level behavioural T4,
 * and check the expected type appears among them. Method-level T1/T2/T3 is deliberately ignored --
 * the WALA pipeline should not produce it (T1/T2/T3 is region-only).
 *
 * <p>Scoring: a positive case is correct when its expected family appears among the region/method-T4
 * verdicts; a negative (NON_CLONE) case is correct when NO region is judged a clone.
 */
public final class PipelineClassificationHarness {

    private final WalaNextPipelineRunner runner;

    public PipelineClassificationHarness() {
        this(new WalaNextPipelineRunner());
    }

    public PipelineClassificationHarness(WalaNextPipelineRunner runner) {
        this.runner = runner;
    }

    /** One case's outcome: the clone families found across regions (+ method T4) vs what was expected. */
    public record Outcome(ClassificationCase evalCase, Set<String> foundFamilies, boolean correct,
                          String error, List<String> decisionPaths) {
    }

    public Outcome evaluate(ClassificationCase evalCase) {
        try {
            NextPipelineResult result = runner.run(evalCase.leftSource(), evalCase.rightSource());
            List<RegionDecision> verdicts = result.regionDecisions().stream()
                    .filter(PipelineClassificationHarness::isCountedVerdict)
                    .toList();
            Set<String> found = verdicts.stream()
                    .map(d -> family(d.type()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            boolean correct = evalCase.expectedFamily().equals("NON_CLONE")
                    ? found.isEmpty()
                    : found.contains(evalCase.expectedFamily());

            List<String> paths = verdicts.stream()
                    .map(d -> family(d.type()) + " <- " + d.candidate().left().kind() + " "
                            + d.decisionPath())
                    .toList();
            return new Outcome(evalCase, found, correct, null, paths);
        } catch (Exception ex) {
            return new Outcome(evalCase, Set.of(), false,
                    ex.getClass().getSimpleName() + ": " + ex.getMessage(), List.of());
        }
    }

    public List<Outcome> evaluateAll(List<ClassificationCase> cases) {
        return cases.stream().map(this::evaluate).toList();
    }

    /**
     * A decision that counts as a real clone verdict under the region-only model: a Phase A region
     * (CALL_EXPANDED_REGION) of any clone type, OR a method-level behavioural T4. Method-level
     * T1/T2/T3 and all NON_CLONE decisions are ignored.
     */
    private static boolean isCountedVerdict(RegionDecision decision) {
        if (decision.type() == CloneRegionType.NON_CLONE) {
            return false;
        }
        boolean regionLevel = decision.candidate().left().kind() == RegionKind.CALL_EXPANDED_REGION;
        return regionLevel || family(decision.type()).equals("T4");
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

    public String report(List<Outcome> outcomes) {
        StringBuilder out = new StringBuilder();
        out.append("== Region-Level Classification Eval (no file-level clone type) ==")
                .append(System.lineSeparator());

        out.append(String.format(Locale.ROOT, "%-20s %-11s %-20s %s%n",
                "case", "expected", "foundRegionTypes", "result"));
        for (Outcome o : outcomes) {
            String mark = o.error() != null ? "ERROR" : (o.correct() ? "ok" : "MISS");
            String found = o.foundFamilies().isEmpty() ? "(none)" : String.join(",", o.foundFamilies());
            out.append(String.format(Locale.ROOT, "%-20s %-11s %-20s %s%n",
                    o.evalCase().id(), o.evalCase().expectedFamily(), found, mark));
        }

        long correct = outcomes.stream().filter(Outcome::correct).count();
        long errors = outcomes.stream().filter(o -> o.error() != null).count();
        out.append(System.lineSeparator())
                .append(String.format(Locale.ROOT, "Accuracy: %d/%d correct; %d crashes%n",
                        correct, outcomes.size(), errors));

        out.append(System.lineSeparator()).append("Misses (why):").append(System.lineSeparator());
        boolean anyMiss = false;
        for (Outcome o : outcomes) {
            if (o.correct()) {
                continue;
            }
            anyMiss = true;
            String found = o.foundFamilies().isEmpty() ? "(none)" : String.join(",", o.foundFamilies());
            out.append(String.format(Locale.ROOT, "  [%s] expected %s, found %s -- %s%n",
                    o.evalCase().id(), o.evalCase().expectedFamily(), found, o.evalCase().note()));
            if (o.error() != null) {
                out.append("      error: ").append(o.error()).append(System.lineSeparator());
            }
            for (String path : o.decisionPaths()) {
                out.append("      | ").append(path).append(System.lineSeparator());
            }
        }
        if (!anyMiss) {
            out.append("  (none)").append(System.lineSeparator());
        }
        return out.toString();
    }

    /** Convenience for callers that just want the printed report. */
    public String evaluateAndReport(List<ClassificationCase> cases) {
        List<Outcome> outcomes = new ArrayList<>(evaluateAll(cases));
        return report(outcomes);
    }
}
