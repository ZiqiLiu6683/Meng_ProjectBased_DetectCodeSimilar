package com.ziqi.mutgen.probes;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtBreak;
import spoon.reflect.code.CtContinue;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtThrow;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Type-safe Type-3 operators, measured against the TXL originals they stand in for.
 *
 * MIF's T3 operators edit statements, but their TXL implementations express the edit with
 * undeclared placeholders — {@code mSIL} turns {@code sc.nextInt()} into {@code sc.nextInt(X1)},
 * {@code mML} turns {@code f(x);} into {@code if (X==Y) f(x);}. On a compilation-based detector
 * those never survive (measured: 0/200 and 2/200). The edit KIND is not the problem; the
 * placeholder is. Each operator below keeps the edit kind of its MIF counterpart and makes only
 * the placeholder type-correct:
 *
 *   ins  (<- mIL)  insert a statement          : a fresh local declaration with a literal value
 *   del  (<- mDL)  delete a statement          : only where Spoon can show nothing later uses it
 *   wrap (<- mML)  modify a statement in place : wrap it in `if (true) { ... }`
 *
 * Every deviation is a redefinition and has to be reported as one; the point of this run is to
 * find out what each redefinition actually buys.
 *
 * Usage: T3OperatorBatch <seed-dir> <max-seeds> [rng-seed]
 */
public final class T3FailureDiag {

    private static final String[] OPERATORS = {"ins", "del", "wrap"};

    public static void main(String[] args) throws Exception {
        Path seedDir = Path.of(args[0]);
        int maxSeeds = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        long rngSeed = args.length > 2 ? Long.parseLong(args[2]) : 42L;

        List<Path> seeds;
        try (var stream = Files.walk(seedDir)) {
            seeds = new ArrayList<>(stream.filter(p -> p.toString().endsWith(".java")).sorted().toList());
        }
        Collections.shuffle(seeds, new Random(rngSeed));

        Map<String, int[]> stats = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> drops = new LinkedHashMap<>();
        for (String op : OPERATORS) {
            stats.put(op, new int[]{0, 0});
            drops.put(op, new LinkedHashMap<>());
        }

        int seedCompileFailures = 0;
        int used = 0;
        for (Path seed : seeds) {
            if (used >= maxSeeds) {
                break;
            }
            String baseline;
            try {
                baseline = printModel(build(seed).getModel());
            } catch (Throwable t) {
                continue;
            }
            if (firstError(baseline) != null) {
                seedCompileFailures++;
                continue;
            }
            used++;
            for (String op : OPERATORS) {
                int[] counters = stats.get(op);
                counters[0]++;
                String reason;
                try {
                    String mutated = apply(seed, op, rngSeed + used);
                    if (mutated == null) {
                        reason = "no applicable statement";
                    } else if (mutated.equals(baseline)) {
                        reason = "no textual change";
                    } else {
                        String err = firstError(mutated);
                        if (err == null) { counters[1]++; continue; }
                        reason = err.replaceAll("[0-9]+", "N");
                        if (reason.length() > 72) { reason = reason.substring(0, 72); }
                    }
                } catch (Throwable t) {
                    reason = "internal error: " + t.getClass().getSimpleName();
                }
                drops.get(op).merge(reason, 1, Integer::sum);
            }
        }

        System.out.printf("%nseeds used=%d (skipped %d that do not compile on their own)%n%n",
                used, seedCompileFailures);
        System.out.printf("%-6s %10s %10s %10s   %s%n", "op", "attempted", "kept", "survival", "MIF counterpart");
        String[] counterparts = {"mIL", "mDL", "mML"};
        for (int i = 0; i < OPERATORS.length; i++) {
            String op = OPERATORS[i];
            int[] c = stats.get(op);
            System.out.printf("%-6s %10d %10d %9.1f%%   %s%n",
                    op, c[0], c[1], c[0] == 0 ? 0.0 : c[1] * 100.0 / c[0], counterparts[i]);
        }
        System.out.println("\ndropped:");
        for (String op : OPERATORS) {
            for (Map.Entry<String, Integer> e : drops.get(op).entrySet()) {
                System.out.printf("  %-6s %5d  %s%n", op, e.getValue(), e.getKey());
            }
        }
    }

    private static String apply(Path seed, String operator, long rng) {
        Launcher launcher = build(seed);
        CtModel model = launcher.getModel();
        Factory factory = launcher.getFactory();
        List<CtStatement> candidates = topLevelStatements(model);
        if (candidates.isEmpty()) {
            return null;
        }
        Collections.shuffle(candidates, new Random(rng));

        switch (operator) {
            case "ins" -> {
                CtStatement anchor = candidates.get(0);
                CtLocalVariable<Integer> fresh = factory.Code().createLocalVariable(
                        factory.Type().integerPrimitiveType(),
                        "zzIns" + Math.abs((int) rng % 9973),
                        factory.Code().createLiteral((int) (Math.abs(rng) % 97)));
                anchor.insertAfter(fresh);
                return printModel(model);
            }
            case "del" -> {
                for (CtStatement candidate : candidates) {
                    if (deletable(candidate)) {
                        candidate.delete();
                        return printModel(model);
                    }
                }
                return null;
            }
            case "wrap" -> {
                for (CtStatement candidate : candidates) {
                    if (candidate instanceof CtLocalVariable) {
                        // Wrapping a declaration would scope the variable out of its later uses.
                        continue;
                    }
                    CtIf conditional = factory.Core().createIf();
                    CtLiteral<Boolean> alwaysTrue = factory.Code().createLiteral(true);
                    conditional.setCondition(alwaysTrue);
                    CtBlock<?> then = factory.Core().createBlock();
                    CtStatement moved = candidate.clone();
                    then.addStatement(moved);
                    conditional.setThenStatement(then);
                    candidate.replace(conditional);
                    return printModel(model);
                }
                return null;
            }
            default -> throw new IllegalArgumentException(operator);
        }
    }

    /**
     * Safe to delete only when nothing downstream can miss it: control flow is excluded outright,
     * and a declaration is excluded when any reference to it survives elsewhere in the model.
     */
    private static boolean deletable(CtStatement statement) {
        if (statement instanceof CtReturn || statement instanceof CtBreak
                || statement instanceof CtContinue || statement instanceof CtThrow) {
            return false;
        }
        if (statement instanceof CtLocalVariable<?> declaration) {
            String name = declaration.getSimpleName();
            CtElement scope = declaration.getParent(CtExecutable.class);
            if (scope == null) {
                return false;
            }
            for (CtVariableReference<?> ref : scope.getElements(
                    (TypeFilter<CtVariableReference<?>>) new TypeFilter(CtVariableReference.class))) {
                if (name.equals(ref.getSimpleName())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Statements directly in a method body — the granularity MIF's line operators work at. */
    private static List<CtStatement> topLevelStatements(CtModel model) {
        List<CtStatement> statements = new ArrayList<>();
        for (CtExecutable<?> executable : model.getElements(new TypeFilter<>(CtExecutable.class))) {
            CtBlock<?> body = executable.getBody();
            if (body == null) {
                continue;
            }
            for (CtStatement statement : body.getStatements()) {
                if (statement.getPosition() != null && statement.getPosition().isValidPosition()) {
                    statements.add(statement);
                }
            }
        }
        return statements;
    }

    private static Launcher build(Path seed) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(seed.toString());
        Environment env = launcher.getEnvironment();
        env.setAutoImports(false);
        env.setCommentEnabled(true);
        env.setNoClasspath(true);
        env.setComplianceLevel(17);
        env.setPrettyPrinterCreator(() -> new SniperJavaPrettyPrinter(env));
        launcher.buildModel();
        return launcher;
    }

    private static String printModel(CtModel model) {
        StringBuilder out = new StringBuilder();
        for (CtType<?> type : model.getAllTypes()) {
            out.append(type.getPosition().getCompilationUnit().prettyprint());
        }
        return out.toString();
    }

    /** null when it compiles; otherwise the first javac error line. */
    private static String firstError(String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try {
            Path root = Files.createTempDirectory("t3-");
            var matcher = java.util.regex.Pattern
                    .compile("public\\s+(?:final\\s+|abstract\\s+)*(?:class|interface|enum|record)\\s+(\\w+)")
                    .matcher(source);
            String name = matcher.find() ? matcher.group(1) : "Main";
            Path file = root.resolve(name + ".java");
            Path classes = root.resolve("classes");
            Files.createDirectories(classes);
            Files.writeString(file, source, StandardCharsets.UTF_8);
            ByteArrayOutputStream sink = new ByteArrayOutputStream();
            int rc = compiler.run(null, sink, sink, "-proc:none", "-nowarn", "--release", "17",
                    "-d", classes.toString(), file.toString());
            String err = sink.toString(StandardCharsets.UTF_8);
            deleteTree(root);
            if (rc == 0) { return null; }
            for (String line : err.split("\n")) {
                int at = line.indexOf(": error: ");
                if (at >= 0) { return line.substring(at + 9).trim(); }
            }
            return err.lines().findFirst().orElse("unknown");
        } catch (Exception e) {
            return "probe failure: " + e;
        }
    }

    private static void deleteTree(Path root) {
        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best effort
                }
            });
        } catch (Exception ignored) {
            // best effort
        }
    }
}
