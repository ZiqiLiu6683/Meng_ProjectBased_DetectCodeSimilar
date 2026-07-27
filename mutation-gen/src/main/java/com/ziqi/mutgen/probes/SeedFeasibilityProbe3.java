package com.ziqi.mutgen.probes;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Feasibility gate, corrected: mutated ranges spread across a file's several methods.
 *
 * The region-level contract forbids a syntactic clone type from BEING a method — it does not
 * require every range to live in the same method. So a normal multi-method file can host one
 * statement range per method, each still a sub-method range. The earlier probe measured the
 * largest single method and therefore answered a question the design never asked.
 *
 * A method can host a range when it is long enough to contain the range AND keep untouched
 * statements on both sides: without that padding the range would coincide with the method body
 * and the label would be method-scoped after all.
 *
 * Usage: SeedFeasibilityProbe3 <corpus-dir> <sample> <ranges-needed> <range-lines> <pad-lines>
 */
public final class SeedFeasibilityProbe3 {

    public static void main(String[] args) throws Exception {
        Path corpus = Path.of(args[0]);
        int sample = args.length > 1 ? Integer.parseInt(args[1]) : 400;
        int rangesNeeded = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        int rangeLines = args.length > 3 ? Integer.parseInt(args[3]) : 6;
        int padLines = args.length > 4 ? Integer.parseInt(args[4]) : 3;

        int hostMinSpan = rangeLines + 2 * padLines;

        List<Path> files;
        try (var stream = Files.walk(corpus)) {
            files = new ArrayList<>(stream.filter(p -> p.toString().endsWith(".java")).toList());
        }
        Collections.shuffle(files, new Random(11));
        files = files.subList(0, Math.min(sample, files.size()));

        int parsed = 0, failed = 0, enoughHosts = 0, enoughHostsWithVars = 0;
        List<Integer> hostCounts = new ArrayList<>();
        List<Integer> methodCounts = new ArrayList<>();

        for (Path file : files) {
            try {
                CtModel model = build(file);
                int methods = 0;
                int hosts = 0;
                int hostsWithConfinedVar = 0;
                for (CtExecutable<?> executable : model.getElements(new TypeFilter<>(CtExecutable.class))) {
                    CtBlock<?> body = executable.getBody();
                    if (body == null || body.getPosition() == null || !body.getPosition().isValidPosition()) {
                        continue;
                    }
                    methods++;
                    int span = body.getPosition().getEndLine() - body.getPosition().getLine() + 1;
                    // Capacity: a long method holds several padded ranges, not just one.
                    int capacity = (span - padLines) / (rangeLines + padLines);
                    if (capacity >= 1) {
                        hosts += capacity;
                        if (confinedLocalVariables(body, rangeLines) >= 1) {
                            hostsWithConfinedVar++;
                        }
                    }
                }
                parsed++;
                methodCounts.add(methods);
                hostCounts.add(hosts);
                if (hosts >= rangesNeeded) {
                    enoughHosts++;
                }
                // T2 needs one confined variable in its own range; the T3 and donor ranges do not.
                if (hosts >= rangesNeeded && hostsWithConfinedVar >= 1) {
                    enoughHostsWithVars++;
                }
            } catch (Throwable t) {
                failed++;
            }
        }

        System.out.println();
        System.out.printf("corpus sample: %d files (parsed=%d, parse-failed=%d)%n", files.size(), parsed, failed);
        System.out.printf("design: %d ranges x %d lines, %d lines of untouched padding each side%n",
                rangesNeeded, rangeLines, padLines);
        System.out.printf("        -> a hosting method must span >= %d lines; need >= %d such methods per file%n%n",
                hostMinSpan, rangesNeeded);
        printQuantiles("methods per file", methodCounts);
        printQuantiles("methods able to host a range", hostCounts);
        System.out.printf("%nfiles with >= %d hosting methods:        %d / %d = %.1f%%%n",
                rangesNeeded, enoughHosts, parsed, pct(enoughHosts, parsed));
        System.out.printf("  ... and >= 1 with a confined variable: %d / %d = %.1f%%%n",
                enoughHostsWithVars, parsed, pct(enoughHostsWithVars, parsed));
        System.out.printf("%nprojected usable seeds in the full 75,000-file corpus: ~%.0f%n",
                pct(enoughHostsWithVars, parsed) / 100.0 * 75000);
    }

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
            System.out.printf("%-34s (no data)%n", label);
            return;
        }
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        System.out.printf("%-34s p25=%d  median=%d  p75=%d  p90=%d  max=%d%n",
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
