package com.ziqi.mutgen.probes;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Feasibility gate for the single-method, statement-range corpus design.
 *
 * The design places several DISJOINT statement ranges inside ONE method — T2 here, T3 there, an
 * unrelated donor block elsewhere, untouched code in between — because the region-level contract
 * forbids scoping a syntactic clone type to a declaration boundary. That only works if seeds have
 * a method long enough to hold those ranges with gaps between them.
 *
 * A range is only usable for T2 if it contains a local variable whose declaration AND every
 * reference lie inside the range; otherwise renaming it would alter lines in a neighbouring region
 * and silently corrupt that region's label. This probe therefore measures the two quantities the
 * design actually depends on, per seed:
 *
 *   1. the largest method's top-level statement count and line span;
 *   2. how many of that method's local variables are confined to a window of the target size.
 *
 * Nothing is generated here. The output is the availability number that decides whether CodeNet
 * can host this design at all.
 *
 * Usage: SeedFeasibilityProbe <corpus-dir> <sample-size> <ranges> <lines-per-range>
 */
public final class SeedFeasibilityProbe {

    public static void main(String[] args) throws Exception {
        Path corpus = Path.of(args[0]);
        int sample = args.length > 1 ? Integer.parseInt(args[1]) : 400;
        int ranges = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        int linesPerRange = args.length > 3 ? Integer.parseInt(args[3]) : 6;

        // ranges of `linesPerRange` each, separated by T1 gaps of the same size, plus a gap at
        // each end so no region touches the method boundary.
        int requiredLines = ranges * linesPerRange + (ranges + 1) * linesPerRange;

        List<Path> files;
        try (var stream = Files.walk(corpus)) {
            files = new ArrayList<>(stream.filter(p -> p.toString().endsWith(".java")).toList());
        }
        Collections.shuffle(files, new Random(11));
        files = files.subList(0, Math.min(sample, files.size()));

        int parsed = 0, failed = 0;
        int longEnough = 0, longEnoughWithConfinedVars = 0;
        List<Integer> spans = new ArrayList<>();
        List<Integer> stmtCounts = new ArrayList<>();
        List<Integer> confinedCounts = new ArrayList<>();

        for (Path file : files) {
            try {
                CtModel model = build(file);
                Best best = largestMethod(model);
                if (best == null) {
                    parsed++;
                    continue;
                }
                parsed++;
                spans.add(best.lineSpan);
                stmtCounts.add(best.statements);
                if (best.lineSpan >= requiredLines) {
                    longEnough++;
                    int confined = confinedLocalVariables(best.body, linesPerRange);
                    confinedCounts.add(confined);
                    if (confined >= ranges - 1) {
                        // T2 needs at least one confined variable in its own range; T3 and the
                        // donor range do not, so ranges-1 confined variables is the floor.
                        longEnoughWithConfinedVars++;
                    }
                }
            } catch (Throwable t) {
                failed++;
            }
        }

        System.out.println();
        System.out.printf("corpus sample: %d files (parsed=%d, parse-failed=%d)%n", files.size(), parsed, failed);
        System.out.printf("design: %d mutated ranges x %d lines, with T1 gaps -> needs a method spanning >= %d lines%n",
                ranges, linesPerRange, requiredLines);
        System.out.println();
        printQuantiles("largest method line span", spans);
        printQuantiles("largest method top-level statements", stmtCounts);
        System.out.println();
        System.out.printf("methods long enough:                 %d / %d = %.1f%%%n",
                longEnough, parsed, pct(longEnough, parsed));
        System.out.printf("  ... and enough range-confined vars: %d / %d = %.1f%%%n",
                longEnoughWithConfinedVars, parsed, pct(longEnoughWithConfinedVars, parsed));
        if (!confinedCounts.isEmpty()) {
            printQuantiles("confined local vars (long-enough seeds only)", confinedCounts);
        }
        System.out.printf("%nprojected usable seeds in the full 75,000-file corpus: ~%.0f%n",
                pct(longEnoughWithConfinedVars, parsed) / 100.0 * 75000);
    }

    private record Best(CtBlock<?> body, int statements, int lineSpan) { }

    private static Best largestMethod(CtModel model) {
        Best best = null;
        for (CtExecutable<?> executable : model.getElements(new TypeFilter<>(CtExecutable.class))) {
            CtBlock<?> body = executable.getBody();
            if (body == null || body.getPosition() == null || !body.getPosition().isValidPosition()) {
                continue;
            }
            int span = body.getPosition().getEndLine() - body.getPosition().getLine() + 1;
            int statements = body.getStatements().size();
            if (best == null || span > best.lineSpan) {
                best = new Best(body, statements, span);
            }
        }
        return best;
    }

    /**
     * Local variables whose declaration and every reference fit inside a window of {@code window}
     * lines. Only these can be renamed without changing text in a neighbouring region.
     */
    private static int confinedLocalVariables(CtBlock<?> body, int window) {
        int confined = 0;
        for (CtLocalVariable<?> var : body.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if (var.getPosition() == null || !var.getPosition().isValidPosition()) {
                continue;
            }
            int lo = var.getPosition().getLine();
            int hi = var.getPosition().getEndLine();
            String name = var.getSimpleName();
            boolean escaped = false;
            for (CtVariableReference<?> ref : body.getElements(
                    (TypeFilter<CtVariableReference<?>>) new TypeFilter(CtVariableReference.class))) {
                if (!name.equals(ref.getSimpleName())) {
                    continue;
                }
                CtElement parent = ref.getParent();
                if (parent == null || parent.getPosition() == null || !parent.getPosition().isValidPosition()) {
                    continue;
                }
                lo = Math.min(lo, parent.getPosition().getLine());
                hi = Math.max(hi, parent.getPosition().getEndLine());
                if (hi - lo + 1 > window) {
                    escaped = true;
                    break;
                }
            }
            if (!escaped) {
                confined++;
            }
        }
        return confined;
    }

    private static void printQuantiles(String label, List<Integer> values) {
        if (values.isEmpty()) {
            System.out.printf("%-46s (no data)%n", label);
            return;
        }
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        System.out.printf("%-46s p25=%d  median=%d  p75=%d  p90=%d  max=%d%n",
                label,
                sorted.get(sorted.size() / 4),
                sorted.get(sorted.size() / 2),
                sorted.get(sorted.size() * 3 / 4),
                sorted.get(Math.min(sorted.size() - 1, sorted.size() * 9 / 10)),
                sorted.get(sorted.size() - 1));
    }

    private static double pct(int a, int b) {
        return b == 0 ? 0.0 : a * 100.0 / b;
    }

    private static CtModel build(Path file) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(file.toString());
        Environment env = launcher.getEnvironment();
        env.setAutoImports(false);
        env.setCommentEnabled(true);
        env.setNoClasspath(true);
        env.setComplianceLevel(17);
        launcher.buildModel();
        return launcher.getModel();
    }
}
