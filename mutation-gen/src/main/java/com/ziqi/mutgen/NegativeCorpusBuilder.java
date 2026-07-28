package com.ziqi.mutgen;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The negative stratum: pairs of unrelated files, verified unrelated rather than assumed.
 *
 * Two CodeNet submissions to different problems are not automatically clone-free. Measured over 400
 * different-problem pairs, the longest run of CONSECUTIVE shared tokens has median 16 and p90 27,
 * and inspection shows what those runs are: {@code Scanner sc = new Scanner ( System . in ) ;} and
 * {@code class Main { public static void main ( String [ ] args ) { Scanner}. Competition
 * scaffolding, present in nearly every file. Same-problem pairs share no more (median 17), which is
 * what shows the runs are not problem logic.
 *
 * A pair is therefore accepted only when that longest run stays below a threshold placed above the
 * scaffolding ceiling. Token-set overlap is deliberately not used: it is high between any two Java
 * files and says nothing about cloning.
 *
 * <p>Residual scaffolding still exceeds RegionGrower's floor of two substantive aligned pairs, so
 * the detector may legitimately report a template region inside a pair labelled non-clone. That is
 * expected, and is why the protocol asks for both false-positive definitions plus an audit rather
 * than a single number.
 *
 * Usage: NegativeCorpusBuilder &lt;corpus-dir&gt; &lt;out-dir&gt; [pairs] [max-shared-tokens] [rng]
 */
public final class NegativeCorpusBuilder {

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_]\\w*|\\d+|[^\\s\\w]");
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "public\\s+(?:final\\s+|abstract\\s+)*(?:class|interface|enum|record)\\s+(\\w+)");

    public static void main(String[] args) throws Exception {
        Path corpus = Path.of(args[0]);
        Path out = Path.of(args[1]);
        int wantedPairs = args.length > 2 ? Integer.parseInt(args[2]) : 100;
        int maxSharedTokens = args.length > 3 ? Integer.parseInt(args[3]) : 30;
        long rngSeed = args.length > 4 ? Long.parseLong(args[4]) : 42L;

        Map<String, List<Path>> byProblem = new LinkedHashMap<>();
        try (var stream = Files.walk(corpus)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                byProblem.computeIfAbsent(file.getParent().getFileName().toString(),
                        k -> new ArrayList<>()).add(file);
            }
        }
        List<String> problems = new ArrayList<>(byProblem.keySet());
        Random rng = new Random(rngSeed);
        Collections.shuffle(problems, rng);

        Files.createDirectories(out.resolve("pairs"));
        StringBuilder manifest = new StringBuilder("pair_id,left_path,right_path,left_problem,right_problem,shared_tokens\n");
        Map<String, Integer> drops = new LinkedHashMap<>();
        Map<Path, List<String>> tokenCache = new LinkedHashMap<>();
        Map<Path, Boolean> compileCache = new LinkedHashMap<>();

        int attempted = 0;
        int kept = 0;
        List<Integer> accepted = new ArrayList<>();
        while (kept < wantedPairs && attempted < wantedPairs * 60) {
            attempted++;
            String pa = problems.get(rng.nextInt(problems.size()));
            String pb = problems.get(rng.nextInt(problems.size()));
            if (pa.equals(pb)) {
                drops.merge("same problem", 1, Integer::sum);
                continue;
            }
            Path left = pick(byProblem.get(pa), rng);
            Path right = pick(byProblem.get(pb), rng);
            if (left == null || right == null) {
                continue;
            }
            if (!compilesCached(left, compileCache) || !compilesCached(right, compileCache)) {
                drops.merge("a side does not compile standalone", 1, Integer::sum);
                continue;
            }
            int shared = longestCommonRun(tokens(left, tokenCache), tokens(right, tokenCache));
            if (shared >= maxSharedTokens) {
                drops.merge("shares >= " + maxSharedTokens + " consecutive tokens", 1, Integer::sum);
                continue;
            }

            String pairId = String.format("N%05d", kept);
            Path dir = out.resolve("pairs").resolve(pairId);
            // Both sides keep the public type name they already have; CodeNet uses `Main` on both,
            // so they must live in separate directories or one would overwrite the other.
            Path leftOut = dir.resolve("left").resolve(publicTypeName(Files.readString(left)) + ".java");
            Path rightOut = dir.resolve("right").resolve(publicTypeName(Files.readString(right)) + ".java");
            Files.createDirectories(leftOut.getParent());
            Files.createDirectories(rightOut.getParent());
            Files.copy(left, leftOut, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.copy(right, rightOut, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            manifest.append(pairId).append(',').append(leftOut.toAbsolutePath()).append(',')
                    .append(rightOut.toAbsolutePath()).append(',')
                    .append(pa).append(',').append(pb).append(',').append(shared).append('\n');
            accepted.add(shared);
            kept++;
        }

        Files.writeString(out.resolve("manifest_raw.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Collections.sort(accepted);
        System.out.printf("%nattempted=%d  kept=%d%n", attempted, kept);
        if (!accepted.isEmpty()) {
            System.out.printf("shared consecutive tokens in accepted pairs: median=%d  max=%d (threshold %d)%n",
                    accepted.get(accepted.size() / 2), accepted.get(accepted.size() - 1), maxSharedTokens);
        }
        if (!drops.isEmpty()) {
            System.out.println("rejected:");
            drops.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                    .forEach(e -> System.out.printf("  %6d  %s%n", e.getValue(), e.getKey()));
        }
        System.out.printf("%nwrote %s%n", out.toAbsolutePath());
    }

    private static Path pick(List<Path> pool, Random rng) {
        return pool == null || pool.isEmpty() ? null : pool.get(rng.nextInt(pool.size()));
    }

    private static boolean compilesCached(Path file, Map<Path, Boolean> cache) {
        return cache.computeIfAbsent(file, path -> {
            try {
                return compiles(Files.readString(path, StandardCharsets.UTF_8));
            } catch (Exception e) {
                return false;
            }
        });
    }

    private static List<String> tokens(Path file, Map<Path, List<String>> cache) {
        return cache.computeIfAbsent(file, path -> {
            String text;
            try {
                text = Files.readString(path, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return List.of();
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

    private static String publicTypeName(String source) {
        Matcher matcher = PUBLIC_TYPE.matcher(source);
        return matcher.find() ? matcher.group(1) : "Main";
    }

    private static boolean compiles(String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try {
            Path root = Files.createTempDirectory("neg-");
            Path file = root.resolve(publicTypeName(source) + ".java");
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
}
