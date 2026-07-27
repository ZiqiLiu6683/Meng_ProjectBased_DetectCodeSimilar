package com.ziqi.mutgen;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.refactoring.CtRenameLocalVariableRefactoring;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * First real operator: scope-aware Type-2 renaming, measured end to end.
 *
 * The point of moving to Spoon was the claim "a transformation that type-checks by construction".
 * That claim is worth nothing until it is measured against the same yardstick as the TXL
 * operators it replaces, so this tool reports exactly what {@code gen_mutants.py} reports:
 * how many pairs were attempted, how many survived compilation, and why the rest were dropped.
 *
 * Renaming uses {@link CtRenameLocalVariableRefactoring}, which runs Spoon's own name-conflict
 * detection before touching the model — a rename that would shadow or collide is refused rather
 * than silently producing a file that no longer compiles.
 *
 * Both sides are printed from the same launcher configuration: the left side is the seed printed
 * unchanged, the right side is the seed printed after renaming. Unchanged code is therefore
 * byte-identical between the sides, and the differing lines ARE the clone-region ground truth.
 *
 * Usage: T2RenameBatch <seed-dir> <out-dir> [max-seeds] [renames-per-seed] [rng-seed]
 */
public final class T2RenameBatch {

    public static void main(String[] args) throws Exception {
        Path seedDir = Path.of(args[0]);
        Path outDir = Path.of(args[1]);
        int maxSeeds = args.length > 2 ? Integer.parseInt(args[2]) : 100;
        int renamesPerSeed = args.length > 3 ? Integer.parseInt(args[3]) : 3;
        long rngSeed = args.length > 4 ? Long.parseLong(args[4]) : 42L;

        List<Path> seeds;
        try (var stream = Files.walk(seedDir)) {
            seeds = new ArrayList<>(stream.filter(p -> p.toString().endsWith(".java")).sorted().toList());
        }
        Collections.shuffle(seeds, new Random(rngSeed));

        Files.createDirectories(outDir.resolve("pairs"));
        StringBuilder labels = new StringBuilder(
                "pair_id,clone_type,operator,seed_file,region_index,left_begin,left_end,right_begin,right_end\n");

        int attempted = 0, kept = 0;
        int dropNoTarget = 0, dropRefactorRefused = 0, dropNoChange = 0, dropLeftCompile = 0,
                dropRightCompile = 0, dropError = 0;
        long regionCount = 0, regionLines = 0;
        List<Integer> renameCounts = new ArrayList<>();

        for (Path seed : seeds) {
            if (attempted >= maxSeeds) {
                break;
            }
            attempted++;
            String pairId = String.format("T2_%05d", attempted - 1);
            try {
                String left = print(seed, null, 0);
                List<String> targets = renameTargets(seed, renamesPerSeed, rngSeed + attempted);
                if (targets.isEmpty()) {
                    dropNoTarget++;
                    continue;
                }
                Applied applied = applyRenames(seed, targets);
                if (applied == null) {
                    dropRefactorRefused++;
                    continue;
                }
                String right = applied.source;
                if (left.equals(right)) {
                    dropNoChange++;
                    continue;
                }

                List<int[]> regions = changedRegions(left, right);
                if (regions.isEmpty()) {
                    dropNoChange++;
                    continue;
                }

                Path pairDir = outDir.resolve("pairs").resolve(pairId);
                Files.createDirectories(pairDir.resolve("left"));
                Files.createDirectories(pairDir.resolve("right"));
                String typeName = publicTypeName(left);
                Path leftPath = pairDir.resolve("left").resolve(typeName + ".java");
                Path rightPath = pairDir.resolve("right").resolve(typeName + ".java");
                Files.writeString(leftPath, left, StandardCharsets.UTF_8);
                Files.writeString(rightPath, right, StandardCharsets.UTF_8);

                if (!compiles(leftPath)) {
                    dropLeftCompile++;
                    deleteTree(pairDir);
                    continue;
                }
                if (!compiles(rightPath)) {
                    dropRightCompile++;
                    deleteTree(pairDir);
                    continue;
                }

                int index = 0;
                for (int[] r : regions) {
                    labels.append(pairId).append(",T2,spoon_rename_local,")
                            .append(seed.getFileName()).append(',')
                            .append(index++).append(',')
                            .append(r[0]).append(',').append(r[1]).append(',')
                            .append(r[0]).append(',').append(r[1]).append('\n');
                    regionLines += r[1] - r[0] + 1;
                }
                regionCount += regions.size();
                renameCounts.add(applied.renamed);
                kept++;
            } catch (Throwable t) {
                dropError++;
            }
        }

        Files.writeString(outDir.resolve("labels.csv"), labels.toString(), StandardCharsets.UTF_8);

        System.out.println();
        System.out.printf("attempted=%d  kept=%d  survival=%.1f%%%n",
                attempted, kept, attempted == 0 ? 0.0 : kept * 100.0 / attempted);
        System.out.println("dropped:");
        report("no renameable local variable", dropNoTarget);
        report("Spoon refused every rename (name conflict)", dropRefactorRefused);
        report("rename produced no textual change", dropNoChange);
        report("LEFT does not compile (seed itself)", dropLeftCompile);
        report("RIGHT does not compile (mutation broke it)", dropRightCompile);
        report("internal error", dropError);
        if (kept > 0) {
            System.out.printf("%nregions: %d total, %.1f per pair, %.1f lines per region%n",
                    regionCount, regionCount / (double) kept, regionLines / (double) regionCount);
            System.out.printf("renames actually applied per pair: %.1f%n",
                    renameCounts.stream().mapToInt(Integer::intValue).average().orElse(0));
        }
    }

    private static void report(String reason, int count) {
        if (count > 0) {
            System.out.printf("  %5d  %s%n", count, reason);
        }
    }

    private record Applied(String source, int renamed) { }

    /** Distinct local-variable names worth renaming, in model order, capped at {@code limit}. */
    private static List<String> renameTargets(Path seed, int limit, long rng) {
        CtModel model = build(seed).getModel();
        List<String> names = new ArrayList<>();
        for (CtLocalVariable<?> v : model.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            String n = v.getSimpleName();
            if (n != null && n.length() >= 1 && !names.contains(n)) {
                names.add(n);
            }
        }
        Collections.shuffle(names, new Random(rng));
        return names.subList(0, Math.min(limit, names.size()));
    }

    /** Rename each target with Spoon's conflict-checking refactoring; null when none succeeded. */
    private static Applied applyRenames(Path seed, List<String> targets) {
        Launcher launcher = build(seed);
        CtModel model = launcher.getModel();
        int renamed = 0;
        for (String name : targets) {
            for (CtLocalVariable<?> v : model.getElements(new TypeFilter<>(CtLocalVariable.class))) {
                if (!name.equals(v.getSimpleName())) {
                    continue;
                }
                CtRenameLocalVariableRefactoring refactoring = new CtRenameLocalVariableRefactoring();
                refactoring.setTarget(v);
                refactoring.setNewName(renamedTo(name));
                try {
                    refactoring.refactor();
                    renamed++;
                } catch (RuntimeException refused) {
                    // Spoon detected a name conflict or an invalid name: leave this variable alone.
                }
                break;
            }
        }
        if (renamed == 0) {
            return null;
        }
        return new Applied(printModel(model), renamed);
    }

    private static String renamedTo(String name) {
        return "v" + Integer.toHexString(name.hashCode() & 0xffff) + "_" + name;
    }

    private static String print(Path seed, String unusedName, int unusedFlag) {
        return printModel(build(seed).getModel());
    }

    private static String printModel(CtModel model) {
        StringBuilder out = new StringBuilder();
        for (CtType<?> type : model.getAllTypes()) {
            out.append(type.getPosition().getCompilationUnit().prettyprint());
        }
        return out.toString();
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

    /** Contiguous runs of differing lines, 1-based inclusive. Line counts are equal by construction. */
    private static List<int[]> changedRegions(String left, String right) {
        String[] a = left.split("\n", -1);
        String[] b = right.split("\n", -1);
        List<int[]> regions = new ArrayList<>();
        int n = Math.max(a.length, b.length);
        int start = -1;
        for (int i = 0; i < n; i++) {
            String x = i < a.length ? a[i] : "";
            String y = i < b.length ? b[i] : "";
            if (!x.equals(y)) {
                if (start < 0) {
                    start = i + 1;
                }
            } else if (start > 0) {
                regions.add(new int[]{start, i});
                start = -1;
            }
        }
        if (start > 0) {
            regions.add(new int[]{start, n});
        }
        return regions;
    }

    private static String publicTypeName(String source) {
        var matcher = java.util.regex.Pattern
                .compile("public\\s+(?:final\\s+|abstract\\s+)*(?:class|interface|enum|record)\\s+(\\w+)")
                .matcher(source);
        return matcher.find() ? matcher.group(1) : "Main";
    }

    private static boolean compiles(Path file) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("no system Java compiler; run on a JDK, not a JRE");
        }
        StringWriter diagnostics = new StringWriter();
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try {
            Path classes = Files.createTempDirectory("mutgen-classes");
            int rc = compiler.run(null, sink, sink,
                    "-proc:none", "-nowarn", "--release", "17",
                    "-d", classes.toString(), file.toString());
            deleteTree(classes);
            return rc == 0;
        } catch (Exception e) {
            new PrintWriter(diagnostics).println(e);
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
}
