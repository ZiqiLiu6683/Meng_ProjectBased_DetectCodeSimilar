package com.ziqi.mutgen.probes;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Feasibility of the region-level negative: a block of foreign code substituted into B.
 *
 * The NON_CLONE region is what makes localisation PRECISION measurable — without a region that
 * must NOT be reported, only recall can be scored. Two things have to hold for its label to be
 * true, and both are measured here because either can silently invalidate it:
 *
 *   1. The donated block must not resemble the seed. If it does, the detector reporting it is
 *      correct and our NON_CLONE label turns a correct detection into a counted false positive.
 *      Similarity is measured as the longest run of CONSECUTIVE shared tokens, which is what a
 *      clone detector responds to — not token-set overlap, which is high for any two Java files.
 *   2. The substituted file must still compile. Foreign statements reference variables that do not
 *      exist at the destination, so a donor is only usable when it is self-contained: every
 *      variable it reads is declared inside the block itself.
 *
 * Usage: DonorProbe <corpus-dir> <trials> [rng-seed]
 */
public final class DonorProbe {

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_]\\w*|\\d+|[^\\s\\w]");
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "public\\s+(?:final\\s+|abstract\\s+)*(?:class|interface|enum|record)\\s+(\\w+)");

    public static void main(String[] args) throws Exception {
        Path corpus = Path.of(args[0]);
        int trials = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        long rngSeed = args.length > 2 ? Long.parseLong(args[2]) : 42L;

        List<Path> files;
        try (var stream = Files.walk(corpus)) {
            files = new ArrayList<>(stream.filter(p -> p.toString().endsWith(".java")).toList());
        }
        Random rng = new Random(rngSeed);
        Collections.shuffle(files, rng);

        List<Integer> overlaps = new ArrayList<>();
        Map<String, Integer> drops = new LinkedHashMap<>();
        int attempted = 0, selfContainedFound = 0, compiled = 0;

        for (int i = 0; i + 1 < files.size() && attempted < trials; i += 2) {
            Path seed = files.get(i);
            Path donor = files.get(i + 1);
            attempted++;
            try {
                Launcher seedLauncher = build(seed);
                CtModel seedModel = seedLauncher.getModel();
                String baseline = printModel(seedModel);
                if (!compiles(baseline)) {
                    drops.merge("seed does not compile", 1, Integer::sum);
                    continue;
                }

                List<CtStatement> donorBlock = selfContainedBlock(build(donor).getModel(), 3);
                if (donorBlock.isEmpty()) {
                    drops.merge("donor has no self-contained block", 1, Integer::sum);
                    continue;
                }
                selfContainedFound++;

                String donorText = String.join("\n", donorBlock.stream().map(Object::toString).toList());
                int overlap = longestCommonRun(tokens(donorText), tokens(baseline));
                overlaps.add(overlap);

                // Substitute: drop one target statement in the seed, put the donor block there.
                List<CtStatement> targets = topLevelStatements(seedModel);
                if (targets.isEmpty()) {
                    drops.merge("seed has no replaceable statement", 1, Integer::sum);
                    continue;
                }
                CtStatement target = targets.get(rng.nextInt(targets.size()));
                CtStatement anchor = target;
                for (CtStatement donated : donorBlock) {
                    CtStatement copy = donated.clone();
                    anchor.insertAfter(copy);
                    anchor = copy;
                }
                target.delete();

                String substituted = printModel(seedModel);
                if (compiles(substituted)) {
                    compiled++;
                } else {
                    drops.merge("substituted file does not compile", 1, Integer::sum);
                }
            } catch (Throwable t) {
                drops.merge("internal error: " + t.getClass().getSimpleName(), 1, Integer::sum);
            }
        }

        System.out.printf("%ntrials=%d%n", attempted);
        System.out.printf("donors with a self-contained block: %d (%.1f%%)%n",
                selfContainedFound, pct(selfContainedFound, attempted));
        System.out.printf("substituted files that compile:     %d (%.1f%% of self-contained)%n%n",
                compiled, pct(compiled, selfContainedFound));

        if (!overlaps.isEmpty()) {
            List<Integer> sorted = new ArrayList<>(overlaps);
            Collections.sort(sorted);
            System.out.printf("longest shared consecutive token run (donor block vs whole seed):%n");
            System.out.printf("  median=%d  p75=%d  p90=%d  p99=%d  max=%d%n",
                    sorted.get(sorted.size() / 2),
                    sorted.get(sorted.size() * 3 / 4),
                    sorted.get(Math.min(sorted.size() - 1, sorted.size() * 9 / 10)),
                    sorted.get(Math.min(sorted.size() - 1, sorted.size() * 99 / 100)),
                    sorted.get(sorted.size() - 1));
            System.out.println("  pass rate by rejection threshold:");
            for (int threshold : new int[]{6, 10, 15, 20, 30, 50}) {
                long pass = overlaps.stream().filter(v -> v < threshold).count();
                System.out.printf("    reject run >= %-3d tokens : %.1f%% of donors usable%n",
                        threshold, pass * 100.0 / overlaps.size());
            }
        }
        if (!drops.isEmpty()) {
            System.out.println("\ndropped:");
            drops.forEach((k, v) -> System.out.printf("  %5d  %s%n", v, k));
        }
    }

    /**
     * A run of consecutive top-level statements in which every variable read is declared inside
     * the run. Anything else cannot be moved into another file and still compile.
     */
    private static List<CtStatement> selfContainedBlock(CtModel model, int wanted) {
        for (CtExecutable<?> executable : model.getElements(new TypeFilter<>(CtExecutable.class))) {
            CtBlock<?> body = executable.getBody();
            if (body == null) {
                continue;
            }
            List<CtStatement> statements = body.getStatements();
            for (int start = 0; start < statements.size(); start++) {
                Set<String> declared = new HashSet<>();
                List<CtStatement> run = new ArrayList<>();
                for (int end = start; end < statements.size(); end++) {
                    CtStatement statement = statements.get(end);
                    if (statement instanceof CtLocalVariable<?> declaration) {
                        declared.add(declaration.getSimpleName());
                    }
                    boolean escapes = false;
                    for (CtVariableReference<?> ref : statement.getElements(
                            (TypeFilter<CtVariableReference<?>>) new TypeFilter(CtVariableReference.class))) {
                        if (!declared.contains(ref.getSimpleName())) {
                            escapes = true;
                            break;
                        }
                    }
                    if (escapes) {
                        break;
                    }
                    run.add(statement);
                    if (run.size() >= wanted) {
                        return run;
                    }
                }
            }
        }
        return List.of();
    }

    private static List<CtStatement> topLevelStatements(CtModel model) {
        List<CtStatement> statements = new ArrayList<>();
        for (CtExecutable<?> executable : model.getElements(new TypeFilter<>(CtExecutable.class))) {
            CtBlock<?> body = executable.getBody();
            if (body == null) {
                continue;
            }
            for (CtStatement statement : body.getStatements()) {
                if (statement.getPosition() != null && statement.getPosition().isValidPosition()
                        && !(statement instanceof CtLocalVariable)) {
                    statements.add(statement);
                }
            }
        }
        return statements;
    }

    private static List<String> tokens(String source) {
        List<String> out = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", ""));
        while (matcher.find()) {
            out.add(matcher.group());
        }
        return out;
    }

    /** Longest run of consecutive tokens present in both sequences. */
    private static int longestCommonRun(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Map<String, List<Integer>> index = new LinkedHashMap<>();
        for (int j = 0; j < b.size(); j++) {
            index.computeIfAbsent(b.get(j), k -> new ArrayList<>()).add(j);
        }
        int best = 0;
        Map<Integer, Integer> previous = new LinkedHashMap<>();
        for (String token : a) {
            Map<Integer, Integer> current = new LinkedHashMap<>();
            for (int j : index.getOrDefault(token, List.of())) {
                int run = previous.getOrDefault(j - 1, 0) + 1;
                current.put(j, run);
                if (run > best) {
                    best = run;
                }
            }
            previous = current;
        }
        return best;
    }

    private static Launcher build(Path file) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(file.toString());
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

    private static boolean compiles(String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try {
            Path root = Files.createTempDirectory("donor-");
            Matcher matcher = PUBLIC_TYPE.matcher(source);
            String name = matcher.find() ? matcher.group(1) : "Main";
            Path file = root.resolve(name + ".java");
            Path classes = root.resolve("classes");
            Files.createDirectories(classes);
            Files.writeString(file, source, StandardCharsets.UTF_8);
            ByteArrayOutputStream sink = new ByteArrayOutputStream();
            int rc = compiler.run(null, sink, sink, "-proc:none", "-nowarn", "--release", "17",
                    "-d", classes.toString(), file.toString());
            deleteTree(root);
            return rc == 0;
        } catch (Exception e) {
            return false;
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

    private static double pct(int a, int b) {
        return b == 0 ? 0.0 : a * 100.0 / b;
    }
}
