package com.ziqi.mutgen;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Edit-locality probe: the property the ground truth actually depends on.
 *
 * A pair is (left = Spoon-printed seed, right = Spoon-printed seed + edits). The unchanged parts
 * therefore never have to match the ORIGINAL file — they only have to match EACH OTHER. What must
 * hold is that a local edit produces only local text change: if renaming one variable also
 * reshuffles formatting fifty lines away, those lines become undeclared differences between the
 * two sides and every region label is wrong.
 *
 * This probe prints each file twice from the same launcher configuration — once untouched, once
 * with a single local variable renamed — and reports how many lines differ versus how many lines
 * the rename actually touches. Ideal: they are equal.
 *
 * Usage: EditLocalityProbe <dir> [max-files]
 */
public final class EditLocalityProbe {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        int max = args.length > 1 ? Integer.parseInt(args[1]) : 40;

        List<Path> files;
        try (var stream = Files.walk(dir)) {
            files = stream.filter(p -> p.toString().endsWith(".java")).sorted().limit(max).toList();
        }

        int clean = 0, noisy = 0, skipped = 0, failed = 0, reported = 0;
        long totalExpected = 0, totalActual = 0;

        for (Path file : files) {
            try {
                String before = print(file, null);
                String target = pickRenameTarget(file);
                if (target == null) {
                    skipped++;
                    continue;
                }
                String after = print(file, target);
                if (after == null) {
                    skipped++;
                    continue;
                }
                int[] diff = diffStats(before, after, target);
                int changedLines = diff[0];
                int linesMentioningName = diff[1];
                totalActual += changedLines;
                totalExpected += linesMentioningName;
                if (changedLines == linesMentioningName && changedLines > 0) {
                    clean++;
                } else {
                    noisy++;
                    if (reported < 4) {
                        System.out.printf("[noisy] %-22s renamed '%s': %d lines changed, but only %d lines involve that name%n",
                                file.getFileName(), target, changedLines, linesMentioningName);
                        reported++;
                    }
                }
            } catch (Throwable t) {
                failed++;
                if (reported < 4) {
                    System.out.println("[fail] " + file.getFileName() + " -> " + t.getClass().getSimpleName());
                    reported++;
                }
            }
        }

        int scored = clean + noisy;
        System.out.println();
        System.out.printf("files=%d  edit-local=%d  extra-noise=%d  skipped(no local var)=%d  failed=%d%n",
                files.size(), clean, noisy, skipped, failed);
        if (scored > 0) {
            System.out.printf("edit-local rate = %.1f%%   avg lines changed per rename = %.1f (expected %.1f)%n",
                    clean * 100.0 / scored, totalActual / (double) scored, totalExpected / (double) scored);
        }
    }

    /** Name of the first local variable that occurs in the file, or null. */
    private static String pickRenameTarget(Path file) {
        CtModel model = buildModel(file).getModel();
        List<CtLocalVariable<?>> vars = model.getElements(new TypeFilter<>(CtLocalVariable.class));
        for (CtLocalVariable<?> v : vars) {
            String n = v.getSimpleName();
            if (n != null && n.length() >= 2 && !n.startsWith("zz")) {
                return n;
            }
        }
        return null;
    }

    /** Print the file; when renameFrom is non-null, rename that local variable first. */
    private static String print(Path file, String renameFrom) {
        Launcher launcher = buildModel(file);
        CtModel model = launcher.getModel();

        if (renameFrom != null) {
            String to = "zz_" + renameFrom;
            boolean touched = false;
            for (CtLocalVariable<?> v : model.getElements(new TypeFilter<>(CtLocalVariable.class))) {
                if (renameFrom.equals(v.getSimpleName())) {
                    for (CtVariableReference<?> ref : model.getElements(
                            (TypeFilter<CtVariableReference<?>>) new TypeFilter(CtVariableReference.class))) {
                        if (renameFrom.equals(ref.getSimpleName())) {
                            ref.setSimpleName(to);
                        }
                    }
                    v.setSimpleName(to);
                    touched = true;
                }
            }
            if (!touched) {
                return null;
            }
        }

        StringBuilder out = new StringBuilder();
        for (CtType<?> type : model.getAllTypes()) {
            out.append(type.getPosition().getCompilationUnit().prettyprint());
        }
        return out.toString();
    }

    private static Launcher buildModel(Path file) {
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

    /** [lines that differ, lines that mention the renamed identifier on either side]. */
    private static int[] diffStats(String before, String after, String name) {
        String[] a = before.split("\n", -1);
        String[] b = after.split("\n", -1);
        int changed = 0, mentions = 0;
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            String x = i < a.length ? a[i] : "";
            String y = i < b.length ? b[i] : "";
            boolean differs = !x.equals(y);
            if (differs) {
                changed++;
            }
            if (differs && (mentionsIdentifier(x, name) || mentionsIdentifier(y, "zz_" + name))) {
                mentions++;
            }
        }
        return new int[]{changed, mentions};
    }

    private static boolean mentionsIdentifier(String line, String name) {
        int idx = line.indexOf(name);
        while (idx >= 0) {
            boolean leftOk = idx == 0 || !Character.isJavaIdentifierPart(line.charAt(idx - 1));
            int end = idx + name.length();
            boolean rightOk = end >= line.length() || !Character.isJavaIdentifierPart(line.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            idx = line.indexOf(name, idx + 1);
        }
        return false;
    }
}
