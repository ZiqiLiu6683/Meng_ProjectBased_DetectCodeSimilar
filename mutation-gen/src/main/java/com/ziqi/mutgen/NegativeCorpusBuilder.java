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
 * <h2>Hard negatives, not merely different-problem negatives</h2>
 *
 * Protocol E5 asks for different-problem negatives <em>matched by token length and basic structural
 * complexity</em>. Sampling uniformly does not do that, and the difference is measurable: over 50
 * uniformly sampled pairs the two sides' token counts differed by a median of 38 %, and 16 of 50
 * differed by more than half. A pair of wildly different-sized programs is an easy negative, and
 * specificity measured on easy negatives overstates what the detector would do on the cases that
 * actually matter.
 *
 * <p>So candidates are drawn from length-sorted neighbours and accepted only when both the token
 * count and a basic structural-complexity count stay within tolerance. Every earlier filter still
 * applies on top: different problems, both sides compile standalone, and the longest shared
 * consecutive token run below the scaffolding ceiling.
 *
 * <p>Each program is used at most once, so no file appears in two pairs. Uniform sampling drew with
 * replacement; at 1,000 pairs from 75,000 files collisions are rare but not impossible, and a
 * repeated file makes two pairs that the statistics would treat as independent.
 *
 * <p>The manifest keeps the problem id AND the source program id for both sides, as E5 requires.
 * The output file is always {@code Main.java}, so without the source id a pair cannot be traced
 * back to the corpus and clustered inference cannot be checked.
 *
 * Usage: NegativeCorpusBuilder &lt;corpus-dir&gt; &lt;out-dir&gt; [pairs] [max-shared-tokens] [rng]
 *        [-Dmutgen.excludeSeeds=FILE] [-Dmutgen.lengthTolerance=0.15]
 *        [-Dmutgen.complexityTolerance=0.30]
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

        double lengthTolerance = Double.parseDouble(
                System.getProperty("mutgen.lengthTolerance", "0.15"));
        double complexityTolerance = Double.parseDouble(
                System.getProperty("mutgen.complexityTolerance", "0.30"));

        // Files already spent as mutation seeds, so the two strata do not share programs. Matched
        // on file name, which is what used_seeds.txt records.
        java.util.Set<String> excluded = new java.util.HashSet<>();
        String excludeFile = System.getProperty("mutgen.excludeSeeds");
        if (excludeFile != null && Files.exists(Path.of(excludeFile))) {
            for (String line : Files.readAllLines(Path.of(excludeFile))) {
                if (!line.isBlank()) {
                    excluded.add(line.strip());
                }
            }
        }

        List<Candidate> all = new ArrayList<>();
        try (var stream = Files.walk(corpus)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                if (excluded.contains(file.getFileName().toString())) {
                    continue;
                }
                List<String> tok = tokens(file, new LinkedHashMap<>());
                if (tok.isEmpty()) {
                    continue;
                }
                all.add(new Candidate(file, file.getParent().getFileName().toString(),
                        tok.size(), complexity(tok)));
            }
        }
        // Length order puts the closest-sized programs next to each other, which is what makes the
        // scan below produce hard pairs rather than merely legal ones.
        all.sort((a, b) -> Integer.compare(a.tokens, b.tokens));
        System.out.printf("corpus: %d files eligible (%d excluded as spent mutation seeds)%n",
                all.size(), excluded.size());

        Random rng = new Random(rngSeed);
        Files.createDirectories(out.resolve("pairs"));
        StringBuilder manifest = new StringBuilder("pair_id,left_path,right_path,"
                + "left_problem,right_problem,left_program,right_program,"
                + "left_tokens,right_tokens,left_complexity,right_complexity,shared_tokens\n");
        Map<String, Integer> drops = new LinkedHashMap<>();
        Map<Path, List<String>> tokenCache = new LinkedHashMap<>();
        Map<Path, Boolean> compileCache = new LinkedHashMap<>();
        boolean[] used = new boolean[all.size()];

        // Start points are shuffled so the corpus is sampled across its whole length range rather
        // than exhausted from the short end, which would make every negative a tiny program.
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            starts.add(i);
        }
        Collections.shuffle(starts, rng);

        int attempted = 0;
        int kept = 0;
        List<Integer> accepted = new ArrayList<>();
        List<Double> lengthGaps = new ArrayList<>();
        for (int start : starts) {
            if (kept >= wantedPairs) {
                break;
            }
            if (used[start]) {
                continue;
            }
            Candidate left = all.get(start);
            Candidate right = null;
            // Walk outward from the length-matched position until a partner passes every filter.
            for (int step = 1; step < 400 && right == null; step++) {
                for (int j : new int[]{start - step, start + step}) {
                    if (j < 0 || j >= all.size() || used[j]) {
                        continue;
                    }
                    Candidate other = all.get(j);
                    attempted++;
                    if (other.problem.equals(left.problem)) {
                        drops.merge("same problem", 1, Integer::sum);
                        continue;
                    }
                    if (relativeGap(left.tokens, other.tokens) > lengthTolerance) {
                        drops.merge("token length gap > " + lengthTolerance, 1, Integer::sum);
                        continue;
                    }
                    if (relativeGap(left.complexity, other.complexity) > complexityTolerance) {
                        drops.merge("complexity gap > " + complexityTolerance, 1, Integer::sum);
                        continue;
                    }
                    if (!compilesCached(left.path, compileCache)
                            || !compilesCached(other.path, compileCache)) {
                        drops.merge("a side does not compile standalone", 1, Integer::sum);
                        continue;
                    }
                    int run = longestCommonRun(tokens(left.path, tokenCache),
                            tokens(other.path, tokenCache));
                    if (run >= maxSharedTokens) {
                        drops.merge("shares >= " + maxSharedTokens + " consecutive tokens",
                                1, Integer::sum);
                        continue;
                    }
                    right = other;
                    used[start] = true;
                    used[j] = true;
                    accepted.add(run);
                    lengthGaps.add(relativeGap(left.tokens, other.tokens));
                    break;
                }
            }
            if (right == null) {
                drops.merge("no partner within tolerance", 1, Integer::sum);
                continue;
            }
            String pa = left.problem;
            String pb = right.problem;
            int shared = accepted.get(accepted.size() - 1);

            String pairId = String.format("N%05d", kept);
            Path dir = out.resolve("pairs").resolve(pairId);
            // Both sides keep the public type name they already have; CodeNet uses `Main` on both,
            // so they must live in separate directories or one would overwrite the other.
            Path leftOut = dir.resolve("left")
                    .resolve(publicTypeName(Files.readString(left.path)) + ".java");
            Path rightOut = dir.resolve("right")
                    .resolve(publicTypeName(Files.readString(right.path)) + ".java");
            Files.createDirectories(leftOut.getParent());
            Files.createDirectories(rightOut.getParent());
            Files.copy(left.path, leftOut, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.copy(right.path, rightOut, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            manifest.append(pairId).append(',').append(leftOut.toAbsolutePath()).append(',')
                    .append(rightOut.toAbsolutePath()).append(',')
                    .append(pa).append(',').append(pb).append(',')
                    .append(left.path.getFileName()).append(',')
                    .append(right.path.getFileName()).append(',')
                    .append(left.tokens).append(',').append(right.tokens).append(',')
                    .append(left.complexity).append(',').append(right.complexity).append(',')
                    .append(shared).append('\n');
            kept++;
        }
        if (!lengthGaps.isEmpty()) {
            List<Double> sorted = new ArrayList<>(lengthGaps);
            Collections.sort(sorted);
            System.out.printf("token-length gap in accepted pairs: median=%.3f  max=%.3f "
                            + "(tolerance %.2f)%n",
                    sorted.get(sorted.size() / 2), sorted.get(sorted.size() - 1), lengthTolerance);
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

    /** One corpus file with the two quantities E5 asks pairs to be matched on. */
    private record Candidate(Path path, String problem, int tokens, int complexity) { }

    /**
     * Basic structural complexity: the number of branch and loop constructs.
     *
     * Counted on tokens rather than on an AST deliberately -- this only has to rank programs for
     * matching, and parsing 75,000 files to do it would cost minutes for no gain in what the number
     * is used for. It is recorded in the manifest, so a reader can check the matching rather than
     * take it on trust.
     */
    private static int complexity(List<String> tokens) {
        int count = 0;
        for (String token : tokens) {
            switch (token) {
                case "if", "for", "while", "switch", "catch", "case", "?", "&&", "||" -> count++;
                default -> { }
            }
        }
        return count;
    }

    /** Difference as a fraction of the larger value, so it is comparable across scales. */
    private static double relativeGap(int a, int b) {
        int larger = Math.max(a, b);
        return larger == 0 ? 0.0 : Math.abs(a - b) / (double) larger;
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
