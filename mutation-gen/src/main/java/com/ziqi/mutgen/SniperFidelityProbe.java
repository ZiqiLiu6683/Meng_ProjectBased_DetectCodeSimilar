package com.ziqi.mutgen;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Round-trip fidelity probe for Spoon's sniper printer.
 *
 * The whole mutant-generation plan rests on one assumption: Spoon can rewrite the parts we
 * deliberately change and leave everything else BYTE-IDENTICAL. If that does not hold, then
 * "unchanged" regions would silently differ between the two sides of a pair, every ground-truth
 * region map would be wrong, and Type-1 (formatting/comments) could not be expressed at all.
 *
 * So before writing a single mutation operator, this probe parses each input file and prints it
 * back with NO modifications, then compares byte for byte. Anything less than a perfect round
 * trip has to be known now, not after a corpus has been generated.
 *
 * Usage: SniperFidelityProbe <file-or-dir> [max-files]
 */
public final class SniperFidelityProbe {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: SniperFidelityProbe <file-or-dir> [max-files]");
            System.exit(2);
        }
        Path input = Path.of(args[0]);
        int max = args.length > 1 ? Integer.parseInt(args[1]) : 50;

        List<Path> files;
        if (Files.isDirectory(input)) {
            try (var stream = Files.walk(input)) {
                files = stream.filter(p -> p.toString().endsWith(".java"))
                        .sorted()
                        .limit(max)
                        .toList();
            }
        } else {
            files = List.of(input);
        }

        int identical = 0;
        int differing = 0;
        int failed = 0;
        int reported = 0;

        for (Path file : files) {
            String original = Files.readString(file, StandardCharsets.UTF_8);
            String printed;
            try {
                printed = roundTrip(file);
            } catch (Throwable t) {
                failed++;
                if (reported < 3) {
                    System.out.println("[fail] " + file.getFileName() + " -> "
                            + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()).lines().findFirst().orElse(""));
                    reported++;
                }
                continue;
            }
            if (normalizeTrailing(original).equals(normalizeTrailing(printed))) {
                identical++;
            } else {
                differing++;
                if (reported < 3) {
                    System.out.println("[diff] " + file.getFileName() + " " + firstDifference(original, printed));
                    reported++;
                }
            }
        }

        int total = identical + differing + failed;
        System.out.println();
        System.out.printf("files=%d  byte-identical=%d (%.1f%%)  differing=%d  parse/print-failed=%d%n",
                total, identical, total == 0 ? 0.0 : identical * 100.0 / total, differing, failed);
    }

    /** Parse with Spoon and print straight back out using the sniper printer, changing nothing. */
    private static String roundTrip(Path file) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(file.toString());
        Environment env = launcher.getEnvironment();
        env.setAutoImports(false);
        env.setCommentEnabled(true);
        env.setNoClasspath(true);
        env.setComplianceLevel(17);
        env.setPrettyPrinterCreator(() -> new SniperJavaPrettyPrinter(env));
        launcher.buildModel();

        CtModel model = launcher.getModel();
        StringBuilder out = new StringBuilder();
        for (CtType<?> type : model.getAllTypes()) {
            out.append(type.getPosition().getCompilationUnit().prettyprint());
        }
        return out.toString();
    }

    private static String normalizeTrailing(String text) {
        return text.replace("\r\n", "\n").stripTrailing();
    }

    private static String firstDifference(String a, String b) {
        String[] la = normalizeTrailing(a).split("\n", -1);
        String[] lb = normalizeTrailing(b).split("\n", -1);
        for (int i = 0; i < Math.min(la.length, lb.length); i++) {
            if (!la[i].equals(lb[i])) {
                return "first diff at line " + (i + 1)
                        + "\n        orig: " + trim(la[i])
                        + "\n        out : " + trim(lb[i]);
            }
        }
        return "line counts differ: orig=" + la.length + " out=" + lb.length;
    }

    private static String trim(String s) {
        String t = s.strip();
        return t.length() > 70 ? t.substring(0, 70) + "..." : t;
    }
}
