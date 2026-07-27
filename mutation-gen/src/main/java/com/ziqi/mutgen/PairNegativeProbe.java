package com.ziqi.mutgen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How often are two unrelated CodeNet files actually free of shared code?
 *
 * Pair-level negatives are only valid if the two files really contain no clone. The earlier
 * measurement that found sharing negligible was taken at a different granularity — a
 * three-statement self-contained block against a whole seed, median 4 and max 5 shared consecutive
 * tokens — and cannot be carried over: the injection experiment produced direct counter-evidence at
 * whole-file granularity, where unrelated submissions were found to share fast-reader and IO
 * templates that the detector correctly reported as clones.
 *
 * This measures the quantity a clone detector actually responds to: the longest run of CONSECUTIVE
 * shared tokens between two whole files. Token-set overlap is not used — it is high for any two
 * Java files and says nothing about cloning. Same-problem and different-problem pairs are reported
 * separately, because a negative stratum drawn from different problems is the one the protocol
 * calls for and the same-problem figure shows what the filter has to remove.
 *
 * Usage: PairNegativeProbe &lt;corpus-dir&gt; &lt;pairs&gt; [rng-seed]
 */
public final class PairNegativeProbe {

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_]\\w*|\\d+|[^\\s\\w]");

    public static void main(String[] args) throws Exception {
        Path corpus = Path.of(args[0]);
        int wantedPairs = args.length > 1 ? Integer.parseInt(args[1]) : 300;
        long rngSeed = args.length > 2 ? Long.parseLong(args[2]) : 42L;

        List<Path> files;
        try (var stream = Files.walk(corpus)) {
            files = new ArrayList<>(stream.filter(p -> p.toString().endsWith(".java")).toList());
        }
        Random rng = new Random(rngSeed);
        Collections.shuffle(files, rng);
        files = files.subList(0, Math.min(4000, files.size()));

        // Problem id is the parent directory name (p00001, p00002, ...).
        Map<String, List<Path>> byProblem = new LinkedHashMap<>();
        for (Path file : files) {
            byProblem.computeIfAbsent(file.getParent().getFileName().toString(), k -> new ArrayList<>()).add(file);
        }

        Map<Path, List<String>> cache = new LinkedHashMap<>();
        List<Integer> different = new ArrayList<>();
        List<Integer> same = new ArrayList<>();

        List<String> problems = new ArrayList<>(byProblem.keySet());
        for (int i = 0; i < wantedPairs && problems.size() > 1; i++) {
            String pa = problems.get(rng.nextInt(problems.size()));
            String pb = problems.get(rng.nextInt(problems.size()));
            if (pa.equals(pb)) {
                i--;
                continue;
            }
            Path a = pick(byProblem.get(pa), rng);
            Path b = pick(byProblem.get(pb), rng);
            if (a == null || b == null) {
                continue;
            }
            different.add(longestCommonRun(tokens(a, cache), tokens(b, cache)));
        }
        for (int i = 0; i < wantedPairs / 2; i++) {
            String problem = problems.get(rng.nextInt(problems.size()));
            List<Path> pool = byProblem.get(problem);
            if (pool.size() < 2) {
                continue;
            }
            Path a = pool.get(rng.nextInt(pool.size()));
            Path b = pool.get(rng.nextInt(pool.size()));
            if (a.equals(b)) {
                continue;
            }
            same.add(longestCommonRun(tokens(a, cache), tokens(b, cache)));
        }

        report("different-problem pairs (the negative stratum)", different);
        report("same-problem pairs (for contrast)", same);
    }

    private static Path pick(List<Path> pool, Random rng) {
        return pool == null || pool.isEmpty() ? null : pool.get(rng.nextInt(pool.size()));
    }

    private static void report(String label, List<Integer> runs) {
        if (runs.isEmpty()) {
            System.out.printf("%n%s: no data%n", label);
            return;
        }
        List<Integer> sorted = new ArrayList<>(runs);
        Collections.sort(sorted);
        System.out.printf("%n%s  (n=%d)%n", label, runs.size());
        System.out.printf("  longest shared consecutive token run: median=%d  p75=%d  p90=%d  p99=%d  max=%d%n",
                sorted.get(sorted.size() / 2),
                sorted.get(sorted.size() * 3 / 4),
                sorted.get(Math.min(sorted.size() - 1, sorted.size() * 9 / 10)),
                sorted.get(Math.min(sorted.size() - 1, sorted.size() * 99 / 100)),
                sorted.get(sorted.size() - 1));
        System.out.println("  share of pairs usable at each rejection threshold:");
        for (int threshold : new int[]{10, 20, 30, 50, 80, 120}) {
            long pass = runs.stream().filter(v -> v < threshold).count();
            System.out.printf("    reject run >= %-4d tokens : %5.1f%% usable%n",
                    threshold, pass * 100.0 / runs.size());
        }
    }

    private static List<String> tokens(Path file, Map<Path, List<String>> cache) {
        return cache.computeIfAbsent(file, path -> {
            String text;
            try {
                text = Files.readString(path, StandardCharsets.UTF_8);
            } catch (Exception e) {
                try {
                    text = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
                } catch (Exception ignored) {
                    return List.of();
                }
            }
            text = text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
            List<String> out = new ArrayList<>();
            Matcher matcher = TOKEN.matcher(text);
            while (matcher.find()) {
                out.add(matcher.group());
            }
            return out;
        });
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
}
