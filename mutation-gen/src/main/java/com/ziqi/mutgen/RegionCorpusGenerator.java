package com.ziqi.mutgen;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.refactoring.CtRenameLocalVariableRefactoring;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtBreak;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtContinue;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtThrow;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates contract-compliant clone pairs: one seed, several statement-level regions, one type each.
 *
 * The region-level contract has two rules that shape everything here. A syntactic type must never be
 * scoped to a declaration — so every mutated range sits INSIDE a method with untouched statements on
 * both sides, and a range is rejected if it would span a whole body. And Phase A grows regions
 * without function boundaries, so ranges may be spread across different methods or share one; the
 * only requirement is that each is a padded sub-method statement range.
 *
 * The previous corpus violated both: it injected a fragment as a nested static class and labelled
 * the whole class block as one region, which is boundary-aligned and class-scoped. The detector
 * emitting tighter sub-regions was correct behaviour; the reference was in breach.
 *
 * Left-hand line numbers are taken from a model built on the PRINTED left text, not on the original
 * file. Spoon regenerates source, so positions from the original model do not address the text that
 * is actually written out, and a ground truth off by a line or two would corrupt every localisation
 * score without failing anything. Right-hand ranges are not computed here at all: statement
 * insertion and deletion shift lines, so they are derived by the companion mapper from a diff of the
 * two printed sides, which is exact because edit locality was measured at 100%.
 *
 * Usage: RegionCorpusGenerator &lt;seed-dir&gt; &lt;out-dir&gt; [pairs] [ranges] [range-lines] [pad-lines] [rng]
 */
public final class RegionCorpusGenerator {

    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "public\\s+(?:final\\s+|abstract\\s+)*(?:class|interface|enum|record)\\s+(\\w+)");

    /**
     * Type-safe stand-ins for the MIF operator families, assigned round-robin so every one gets a
     * comparable sample.
     *
     * The first corpus used "first applicable operator wins", which looked reasonable and produced
     * 100 T3 references that were ALL {@code insert_statement}: delete and wrap were implemented,
     * validated at ~100% survival, and never once selected. What was reported as T3 recall was
     * insertion recall. Per-operator recall is a §6 requirement, so selection is now explicit.
     */
    private static final List<String> OPERATORS = List.of(
            "t1_add_eol_comment",       // <- mCC_EOL
            "t2_rename_local",          // <- mSRI, systematic renaming
            "t3_insert_statement",      // <- mIL
            "t1_add_blank_line",        // <- mCF_A
            "t2_change_int_literal",    // <- mRL_N
            "t3_delete_statement",      // <- mDL
            "t1_add_block_comment",     // <- mCC_BT
            "t2_change_string_literal", // <- mRL_S
            "t3_wrap_statement",        // <- mML
            "t1_reindent");             // <- mCW_A

    /**
     * T1 operators change layout and comments only, which Spoon's printer normalises away, so they
     * are applied to the PRINTED text rather than to the model. Their target range has to be located
     * in the printed right side, where a T3 insertion or deletion elsewhere may already have shifted
     * the line numbers -- hence the diff mapping rather than reusing the left numbers.
     */
    private static boolean isTextOperator(String operator) {
        return operator.startsWith("t1_");
    }

    public static void main(String[] args) throws Exception {
        Path seedDir = Path.of(args[0]);
        Path out = Path.of(args[1]);
        int wantedPairs = args.length > 2 ? Integer.parseInt(args[2]) : 100;
        int rangesPerPair = args.length > 3 ? Integer.parseInt(args[3]) : 3;
        int rangeLines = args.length > 4 ? Integer.parseInt(args[4]) : 6;
        int padLines = args.length > 5 ? Integer.parseInt(args[5]) : 3;
        long rngSeed = args.length > 6 ? Long.parseLong(args[6]) : 42L;
        // Optional and off by default. It was added to try to make region growth STOP at a run of
        // unrelated code, so a pair would yield several regions instead of one. Measured on 20
        // pairs, it does not: 85% still produced exactly one region (vs 82% without it) and the
        // donor lines fell inside a predicted region in 20 of 20 cases. Growth continues because
        // the code on both sides of the donor still corresponds. The sub-region breakdown does
        // isolate the block exactly, so the information is available at that scale instead.
        // Kept switchable rather than removed, so the experiment is reproducible.
        Path donorLib = args.length > 7 ? Path.of(args[7]) : null;

        // Loaded once: the library is small and every block was already validated to compile at
        // several unrelated destinations, so reuse across pairs is the intended usage.
        List<String> donors = new ArrayList<>();
        if (donorLib != null) {
            try (var stream = Files.walk(donorLib.resolve("blocks"))) {
                for (Path block : stream.filter(f -> f.toString().endsWith(".txt")).sorted().toList()) {
                    donors.add(Files.readString(block, StandardCharsets.UTF_8));
                }
            }
            if (donors.isEmpty()) {
                throw new IllegalStateException("donor library has no blocks: " + donorLib);
            }
            System.out.printf("[gen] donor library: %d blocks%n", donors.size());
        }

        // -Dmutgen.excludeSeeds=<file> lists seed file NAMES already consumed by an earlier batch.
        // Batches shuffle the same corpus with different RNG seeds, so without this a later batch
        // silently reuses files an earlier one already used: the corpus would contain near-duplicate
        // pairs and the statistics would treat them as independent. Names are unique across CodeNet
        // Java250 (verified: zero duplicate basenames in 75,000 files).
        Set<String> excluded = new HashSet<>();
        String excludeFile = System.getProperty("mutgen.excludeSeeds", "").strip();
        if (!excludeFile.isEmpty()) {
            for (String line : Files.readAllLines(Path.of(excludeFile), StandardCharsets.UTF_8)) {
                String name = line.strip();
                if (!name.isEmpty()) {
                    excluded.add(name);
                }
            }
            System.out.printf("[gen] excluding %d seeds used by earlier batches%n", excluded.size());
        }

        List<Path> seeds;
        try (var stream = Files.walk(seedDir)) {
            seeds = new ArrayList<>(stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !excluded.contains(p.getFileName().toString()))
                    .sorted().toList());
        }
        Collections.shuffle(seeds, new Random(rngSeed));

        // A balanced schedule, shuffled once. Indexing OPERATORS by position instead gave
        // slot0 = 2i and slot1 = 2i+1, so each operator was ALWAYS paired with the same partner --
        // only five distinct combinations across the whole corpus. A region carries one type and
        // contains both of a pair's mutations, so "the region-level type accuracy of operator X"
        // was really measuring X's fixed partner: t2_change_int_literal scored 5% because it always
        // sat beside t3_delete_statement, which pulled every shared region to T3.
        List<String> schedule = new ArrayList<>();
        int slots = wantedPairs * rangesPerPair;
        while (schedule.size() < slots + OPERATORS.size()) {
            schedule.addAll(OPERATORS);
        }
        Collections.shuffle(schedule, new Random(rngSeed ^ 0x5eed));

        Files.createDirectories(out.resolve("pairs"));
        StringBuilder regions = new StringBuilder(
                "pair_id,ref_index,kind,clone_type,operator,left_begin,left_end,right_begin,right_end\n");
        // seed_problem is the CodeNet problem directory. Confidence intervals must cluster by problem
        // (§7), and the seed's basename alone cannot say which problem it belongs to -- recovering it
        // later means rescanning 75,000 files to build the mapping.
        StringBuilder manifest = new StringBuilder(
                "pair_id,seed_file,seed_problem,left_path,right_path\n");
        Map<String, Integer> drops = new LinkedHashMap<>();

        int attempted = 0;
        int kept = 0;
        for (Path seed : seeds) {
            if (kept >= wantedPairs) {
                break;
            }
            attempted++;
            String reason;
            try {
                reason = generate(seed, out, kept, rangesPerPair, rangeLines, padLines,
                        rngSeed + attempted, donors, schedule, regions, manifest);
            } catch (Throwable t) {
                reason = "internal error: " + t.getClass().getSimpleName();
            }
            if (reason == null) {
                kept++;
            } else {
                drops.merge(reason, 1, Integer::sum);
            }
        }

        Files.writeString(out.resolve("regions_left.csv"), regions.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("manifest_raw.csv"), manifest.toString(), StandardCharsets.UTF_8);

        System.out.printf("%nattempted=%d  kept=%d (%.1f%%)%n", attempted, kept,
                attempted == 0 ? 0.0 : kept * 100.0 / attempted);
        if (!drops.isEmpty()) {
            System.out.println("dropped:");
            drops.entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .forEach(e -> System.out.printf("  %5d  %s%n", e.getValue(), e.getKey()));
        }
        System.out.printf("%nwrote %s%n", out.toAbsolutePath());
    }

    /** @return null on success, otherwise the drop reason. */
    private static String generate(Path seed, Path out, int index, int wantedRanges, int rangeLines,
                                   int padLines, long rng, List<String> donors,
                                   List<String> schedule, StringBuilder regions, StringBuilder manifest)
            throws Exception {
        String leftText = printModel(build(seed).getModel());
        if (!compiles(leftText)) {
            return "seed does not compile";
        }

        // Re-parse the printed text: only positions taken from it address the file we write out.
        Path scratch = Files.createTempDirectory("regen-");
        String typeName = publicTypeName(leftText);
        Path leftScratch = scratch.resolve(typeName + ".java");
        Files.writeString(leftScratch, leftText, StandardCharsets.UTF_8);

        Launcher launcher = build(leftScratch);
        CtModel model = launcher.getModel();
        List<Range> chosen = chooseRanges(model, wantedRanges, rangeLines, padLines, rng);
        if (chosen.size() < wantedRanges) {
            deleteTree(scratch);
            return "seed offers fewer than " + wantedRanges + " padded ranges";
        }

        // Round-robin over the catalogue, continuing across pairs so counts stay balanced even
        // though each pair only carries a couple of ranges.
        List<String> applied = new ArrayList<>();
        Random random = new Random(rng);
        for (int i = 0; i < chosen.size(); i++) {
            Range range = chosen.get(i);
            // Indexed by the KEPT pair, so a dropped attempt retries the same schedule slot with a
            // different seed rather than consuming it -- balance survives the drop rate.
            String wanted = schedule.get((index * chosen.size() + i) % schedule.size());
            if (isTextOperator(wanted)) {
                applied.add(wanted);   // applied after printing, below
                continue;
            }
            String operator = applyOperator(wanted, model, range, launcher.getFactory(), random);
            if (operator == null) {
                deleteTree(scratch);
                return "operator " + wanted + " not applicable in a chosen range";
            }
            applied.add(operator);
        }

        String rightText = printModel(model);

        // Layout/comment operators run here: bottom-up so an edit never moves a range still to be
        // edited, and against ranges mapped through the diff because the Spoon edits above may
        // already have shifted the right-hand line numbers.
        for (int i = chosen.size() - 1; i >= 0; i--) {
            if (!isTextOperator(applied.get(i))) {
                continue;
            }
            int[] target = mapRange(leftText, rightText,
                    chosen.get(i).beginLine, chosen.get(i).endLine);
            if (target == null) {
                deleteTree(scratch);
                return "could not locate " + applied.get(i) + " range in the printed output";
            }
            String edited = applyTextOperator(applied.get(i), rightText, target[0], target[1], random);
            if (edited == null) {
                deleteTree(scratch);
                return "operator " + applied.get(i) + " not applicable in a chosen range";
            }
            rightText = edited;
        }

        if (rightText.equals(leftText)) {
            deleteTree(scratch);
            return "mutations produced no textual change";
        }

        // Splice the donor as TEXT after printing, not as Spoon statements before it. The block has
        // no counterpart in the seed, so its lines are the NON_CLONE reference and their positions
        // must be exact; computing them here from the splice point is exact by construction, where
        // recovering them from a diff would have to be told apart from the T3 insertion.
        int donorBegin = 0;
        int donorEnd = 0;
        String donorId = "";
        if (!donors.isEmpty()) {
            // Separate stream from the operator RNG: reusing it would make donor choice depend on
            // how many operator attempts happened, which is not a property we want to entangle.
            Random donorRandom = new Random(rng * 31 + 7);
            int pick = donorRandom.nextInt(donors.size());
            String block = donors.get(pick).stripTrailing();
            List<Integer> points = spliceCandidates(rightText);
            if (points.isEmpty()) {
                deleteTree(scratch);
                return "no valid splice point for the donor block";
            }
            int at = points.get(donorRandom.nextInt(points.size()));
            // Rename the donor's locals per pair: the library prefixes them already, but a pair that
            // drew the same block twice, or a seed that happens to use the same name, would collide.
            String tagged = block.replace("zz", "d" + Integer.toHexString((int) (rng & 0xfff)) + "z");
            String[] lines = rightText.split("\n", -1);
            StringBuilder merged = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                merged.append(lines[i]).append('\n');
                if (i + 1 == at) {
                    donorBegin = i + 2;
                    merged.append(tagged).append('\n');
                    donorEnd = donorBegin + tagged.split("\n", -1).length - 1;
                }
            }
            rightText = merged.toString();
            donorId = String.format("D%04d", pick + 1);
        }

        if (!compiles(rightText)) {
            deleteTree(scratch);
            return "mutant does not compile";
        }
        deleteTree(scratch);

        String pairId = String.format("R%05d", index);
        Path pairDir = out.resolve("pairs").resolve(pairId);
        Path leftPath = pairDir.resolve("left").resolve(typeName + ".java");
        Path rightPath = pairDir.resolve("right").resolve(typeName + ".java");
        Files.createDirectories(leftPath.getParent());
        Files.createDirectories(rightPath.getParent());
        Files.writeString(leftPath, leftText, StandardCharsets.UTF_8);
        Files.writeString(rightPath, rightText, StandardCharsets.UTF_8);

        // Two reference kinds per §6. CLONE_INTERVAL is the block that corresponds and is scored
        // against a region; MUTATION is the lines the operator actually touched and is scored
        // against a sub-region. Conflating them is what made a 9-line "T3" reference out of a single
        // inserted statement plus eight identical lines.
        List<Opcode> opcodes = diff(leftText, rightText);
        int refIndex = 0;
        for (int i = 0; i < chosen.size(); i++) {
            Range range = chosen.get(i);
            String operator = applied.get(i);
            // Derived from the operator name, so an operator that forgets its prefix fails loudly
            // instead of being silently filed as T3 -- which is exactly what t2_rename_local did
            // when it returned "rename_local_xN": every renamed range was labelled T3.
            String type = cloneTypeOf(operator);
            // No counterpart means no interval corresponds, so no CLONE_INTERVAL is emitted -- only
            // the MUTATION below, which is what a deletion actually is. A half-empty clone interval
            // would ask the scorer to match a region against nothing.
            int[] mapped = mapRange(leftText, rightText, range.beginLine, range.endLine);
            if (mapped != null) {
                regions.append(pairId).append(',').append(refIndex++).append(",CLONE_INTERVAL,")
                        .append(type).append(',').append(operator).append(',')
                        .append(range.beginLine).append(',').append(range.endLine).append(',')
                        .append(mapped[0]).append(',').append(mapped[1]).append('\n');
            }

            // One reference per CONTIGUOUS run of changed lines, not one span from the first change
            // to the last. A rename touches its declaration and each use, which are scattered: a
            // single spanning interval swallowed the untouched lines between them, and the scorer --
            // picking the best-overlapping run -- then matched a T1 gap rather than one of the
            // renamed lines. Measured: 12 of 100 renames per batch were typed T1 that way while the
            // detector had correctly marked each renamed line T2 and each gap T1.
            List<int[]> runs = operator.startsWith("t3_wrap")
                    ? List.of(wrapMutationInterval(leftText, rightText, opcodes,
                            range.beginLine, range.endLine))
                    : mutationRuns(opcodes, range.beginLine, range.endLine);
            for (int[] mutation : runs) {
                if (mutation[1] < mutation[0] || (mutation[1] == 0 && mutation[3] == 0)) {
                    continue;
                }
                regions.append(pairId).append(',').append(refIndex++).append(",MUTATION,")
                        .append(type).append(',').append(operator).append(',')
                        .append(mutation[0] == 0 ? "" : mutation[0]).append(',')
                        .append(mutation[1] == 0 ? "" : mutation[1]).append(',')
                        .append(mutation[2] == 0 ? "" : mutation[2]).append(',')
                        .append(mutation[3] == 0 ? "" : mutation[3]).append('\n');
            }
        }
        for (int[] run : untouchedRuns(opcodes, chosen)) {
            regions.append(pairId).append(',').append(refIndex++).append(",UNTOUCHED,T1,none,")
                    .append(run[0]).append(',').append(run[1]).append(',')
                    .append(run[2]).append(',').append(run[3]).append('\n');
        }
        if (donorEnd >= donorBegin && donorBegin > 0) {
            regions.append(pairId).append(',').append(chosen.size()).append(",NON_CLONE,donor_")
                    .append(donorId).append(",,,").append(donorBegin).append(',')
                    .append(donorEnd).append('\n');
        }
        manifest.append(pairId).append(',').append(seed.getFileName()).append(',')
                .append(seed.getParent() == null ? "" : seed.getParent().getFileName()).append(',')
                .append(leftPath.toAbsolutePath()).append(',')
                .append(rightPath.toAbsolutePath()).append('\n');
        return null;
    }

    /**
     * Lines after which a statement may be spliced in: a line ending in {@code ;} or {@code \}} at a
     * brace depth of two or more, i.e. inside a method body rather than at class level.
     *
     * Depth is counted on the text because the donor is spliced into printed output, and a splice
     * after an unbraced {@code if} header or at class scope would not compile.
     */
    private static List<Integer> spliceCandidates(String text) {
        List<Integer> points = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        int depth = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int before = depth;
            for (char c : line.toCharArray()) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
            }
            String trimmed = line.strip();
            boolean closesSomething = trimmed.equals("}") || trimmed.endsWith(";");
            if (closesSomething && depth >= 2 && before >= 2) {
                points.add(i + 1);
            }
        }
        return points;
    }

    private record Range(CtBlock<?> body, List<CtStatement> statements, int beginLine, int endLine) { }

    /**
     * Disjoint statement ranges, each with untouched statements before and after it inside its own
     * method. The padding is what keeps a range from coinciding with a method body: without it the
     * label would be method-scoped, which the contract forbids for a syntactic type.
     */
    private static List<Range> chooseRanges(CtModel model, int wanted, int rangeLines, int padLines,
                                            long rng) {
        List<Range> candidates = new ArrayList<>();
        for (CtExecutable<?> executable : model.getElements(new TypeFilter<>(CtExecutable.class))) {
            CtBlock<?> body = executable.getBody();
            if (body == null || body.getPosition() == null || !body.getPosition().isValidPosition()) {
                continue;
            }
            List<CtStatement> statements = new ArrayList<>();
            for (CtStatement statement : body.getStatements()) {
                if (!(statement instanceof CtComment) && statement.getPosition() != null
                        && statement.getPosition().isValidPosition()) {
                    statements.add(statement);
                }
            }
            if (statements.size() < 3) {
                continue;
            }
            int bodyBegin = body.getPosition().getLine();
            int bodyEnd = body.getPosition().getEndLine();
            for (int start = 0; start < statements.size(); start++) {
                for (int end = start; end < statements.size(); end++) {
                    int beginLine = statements.get(start).getPosition().getLine();
                    int endLine = statements.get(end).getPosition().getEndLine();
                    if (endLine - beginLine + 1 < rangeLines) {
                        continue;
                    }
                    boolean paddedBefore = beginLine - bodyBegin >= padLines && start > 0;
                    boolean paddedAfter = bodyEnd - endLine >= padLines && end < statements.size() - 1;
                    if (paddedBefore && paddedAfter) {
                        candidates.add(new Range(body, new ArrayList<>(statements.subList(start, end + 1)),
                                beginLine, endLine));
                    }
                    break;
                }
            }
        }
        Collections.shuffle(candidates, new Random(rng));
        List<Range> chosen = new ArrayList<>();
        for (Range candidate : candidates) {
            boolean overlaps = false;
            for (Range existing : chosen) {
                // Disjoint AND separated: adjacent ranges would merge into one region.
                if (candidate.beginLine <= existing.endLine + padLines
                        && existing.beginLine <= candidate.endLine + padLines) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                chosen.add(candidate);
            }
            if (chosen.size() >= wanted) {
                break;
            }
        }
        chosen.sort((a, b) -> Integer.compare(a.beginLine, b.beginLine));
        return chosen;
    }

    /**
     * Rename only variables whose declaration AND every reference fall inside the range. A variable
     * used outside it would carry the edit into a neighbouring region and mislabel that region.
     */
    /** The clone type an operator produces, from its name prefix. Unprefixed names are a bug. */
    private static String cloneTypeOf(String operator) {
        if (operator.startsWith("t1_")) {
            return "T1";
        }
        if (operator.startsWith("t2_")) {
            return "T2";
        }
        if (operator.startsWith("t3_")) {
            return "T3";
        }
        throw new IllegalStateException("operator name carries no type prefix: " + operator);
    }

    /** One aligned block from an LCS line diff: left [i1,i2) and right [j1,j2), 0-based, half-open. */
    private record Opcode(boolean equal, int i1, int i2, int j1, int j2) { }

    /**
     * Line-level LCS alignment of the two printed sides.
     *
     * This is a fact about the two texts, not an opinion of the detector, so using it to PLACE the
     * references keeps the ground truth independent of the system under test. The TYPES never come
     * from here -- they come from which operator the generator applied.
     */
    private static List<Opcode> diff(String leftText, String rightText) {
        List<String> left = List.of(leftText.split("\n", -1));
        List<String> right = List.of(rightText.split("\n", -1));
        int[][] dp = new int[left.size() + 1][right.size() + 1];
        for (int i = left.size() - 1; i >= 0; i--) {
            for (int j = right.size() - 1; j >= 0; j--) {
                dp[i][j] = left.get(i).equals(right.get(j))
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
        List<Opcode> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < left.size() || j < right.size()) {
            boolean equal = i < left.size() && j < right.size() && left.get(i).equals(right.get(j));
            int si = i;
            int sj = j;
            if (equal) {
                while (i < left.size() && j < right.size() && left.get(i).equals(right.get(j))) {
                    i++;
                    j++;
                }
            } else {
                while (i < left.size() || j < right.size()) {
                    if (i < left.size() && j < right.size() && left.get(i).equals(right.get(j))) {
                        break;
                    }
                    if (j >= right.size() || (i < left.size() && dp[i + 1][j] >= dp[i][j + 1])) {
                        i++;
                    } else {
                        j++;
                    }
                }
            }
            out.add(new Opcode(equal, si, i, sj, j));
        }
        return out;
    }

    /**
     * The lines one operator actually changed, on each side, restricted to its own range.
     *
     * Exactly one operator acts in each range, so every difference inside that range is that
     * operator's doing and no attribution is needed. Recorded because protocol §6 asks for the clone
     * interval AND the mutation interval: the first says a block corresponds, the second says where
     * the edit is. The earlier corpus conflated them into one label, which is why a 9-line "T3"
     * reference contained a single inserted statement and eight identical lines.
     *
     * @return {leftBegin, leftEnd, rightBegin, rightEnd}; a zero means that side has no extent,
     *         which is the normal case for a pure insertion or deletion.
     */
    /**
     * Wrapping adds ONE statement -- an `if` -- around one that does not change, so its mutation
     * interval is that statement's own line and nothing else.
     *
     * The generic rule takes every changed line in the range, which for wrap includes the wrapped
     * statement: a line diff sees the whole thing as a replace because the statement is re-indented.
     * That made the reference span code the operator did not touch, and since the genuinely added
     * line has no left-hand counterpart, requiring coverage on both sides left the correct T3 run
     * unmatchable while the unchanged statement's T1 run won. Measured over 3,000 pairs that alone
     * reported 7.3% type accuracy for an operator the detector was identifying correctly; with the
     * interval corrected it is 99.7%.
     *
     * A statement's own extent excludes what is nested inside it, so an `if` spans its header line
     * and the closing brace is not a statement of its own. Comparison strips indentation, otherwise
     * every re-indented body line looks new.
     */
    private static int[] wrapMutationInterval(String leftText, String rightText,
                                              List<Opcode> opcodes, int begin, int end) {
        List<String> left = new ArrayList<>();
        for (String line : leftText.split("\n", -1)) {
            left.add(line.strip());
        }
        List<String> right = new ArrayList<>();
        for (String line : rightText.split("\n", -1)) {
            right.add(line.strip());
        }
        int[] generic = mutationInterval(opcodes, begin, end);
        int lo = generic[2] > 0 ? generic[2] : begin;
        int hi = (generic[3] > 0 ? generic[3] : end) + 2;
        for (Opcode op : opcodes) {
            if (op.equal()) {
                continue;
            }
            List<String> block = left.subList(Math.min(op.i1(), left.size()),
                    Math.min(op.i2(), left.size()));
            for (int j = op.j1(); j < op.j2() && j < right.size(); j++) {
                int line = j + 1;
                if (line >= lo && line <= hi && !block.contains(right.get(j))) {
                    return new int[]{0, 0, line, line};
                }
            }
        }
        return generic;
    }

    /**
     * The changed lines inside a range, split into contiguous runs.
     *
     * Each run is one localisable edit. Merging them into a single interval would claim the
     * untouched lines between two renamed lines as mutated, which is both false and, at scoring
     * time, enough to make an unchanged T1 run the best overlap.
     */
    private static List<int[]> mutationRuns(List<Opcode> opcodes, int begin, int end) {
        List<int[]> runs = new ArrayList<>();
        for (Opcode op : opcodes) {
            if (op.equal()) {
                continue;
            }
            int lo = Math.max(begin, op.i1() + 1);
            int hi = Math.min(end, op.i2());
            boolean touchesLeft = lo <= hi;
            boolean atBoundary = op.i1() + 1 >= begin && op.i1() <= end;
            if (!touchesLeft && !atBoundary) {
                continue;
            }
            int lb = touchesLeft ? lo : 0;
            int le = touchesLeft ? hi : 0;
            int rb = op.j2() > op.j1() ? op.j1() + 1 : 0;
            int re = op.j2() > op.j1() ? op.j2() : 0;
            if (lb == 0 && rb == 0) {
                continue;
            }
            runs.add(new int[]{lb, le, rb, re});
        }
        return runs;
    }

    private static int[] mutationInterval(List<Opcode> opcodes, int begin, int end) {
        int lb = 0;
        int le = 0;
        int rb = 0;
        int re = 0;
        for (Opcode op : opcodes) {
            if (op.equal()) {
                continue;
            }
            int lo = Math.max(begin, op.i1() + 1);
            int hi = Math.min(end, op.i2());
            boolean touchesLeft = lo <= hi;
            // A pure insertion has no left extent, so attribute it to the range whose lines surround
            // the insertion point; otherwise every inserted statement would be unattributable.
            boolean atBoundary = op.i1() + 1 >= begin && op.i1() <= end;
            if (!touchesLeft && !atBoundary) {
                continue;
            }
            if (touchesLeft) {
                lb = lb == 0 ? lo : Math.min(lb, lo);
                le = Math.max(le, hi);
            }
            if (op.j2() > op.j1()) {
                rb = rb == 0 ? op.j1() + 1 : Math.min(rb, op.j1() + 1);
                re = Math.max(re, op.j2());
            }
        }
        return new int[]{lb, le, rb, re};
    }

    /**
     * Runs of byte-identical lines inside the hosting method bodies, excluding the mutated ranges.
     *
     * Type-1 by construction. Without them T1 is never tested at all: the corpus would only ask
     * about ranges that were deliberately changed, leaving "did it recognise the code that was NOT
     * touched" unmeasured.
     */
    private static List<int[]> untouchedRuns(List<Opcode> opcodes, List<Range> ranges) {
        List<int[]> runs = new ArrayList<>();
        // Distinct bodies only. Two ranges commonly sit in the SAME method, and iterating per range
        // emitted every untouched run of that body twice -- doubling the T1 references and with them
        // any per-type count computed from this table.
        List<int[]> bodies = new ArrayList<>();
        for (Range host : ranges) {
            int[] span = {host.body().getPosition().getLine(), host.body().getPosition().getEndLine()};
            if (bodies.stream().noneMatch(x -> x[0] == span[0] && x[1] == span[1])) {
                bodies.add(span);
            }
        }
        for (Opcode op : opcodes) {
            if (!op.equal()) {
                continue;
            }
            for (int[] body : bodies) {
                int bodyBegin = body[0];
                int bodyEnd = body[1];
                int lo = Math.max(op.i1() + 1, bodyBegin);
                int hi = Math.min(op.i2(), bodyEnd);
                for (int line = lo; line <= hi; line++) {
                    boolean mutated = false;
                    for (Range range : ranges) {
                        if (line >= range.beginLine() && line <= range.endLine()) {
                            mutated = true;
                            break;
                        }
                    }
                    if (mutated) {
                        continue;
                    }
                    int right = op.j1() + (line - (op.i1() + 1)) + 1;
                    int[] last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
                    if (last != null && last[1] == line - 1 && last[3] == right - 1) {
                        last[1] = line;
                        last[3] = right;
                    } else {
                        runs.add(new int[]{line, line, right, right});
                    }
                }
            }
        }
        return runs;
    }

    /**
     * Map a left line range onto the printed right side via an LCS line alignment.
     *
     * Needed because a T3 edit in one range shifts every line after it, so a T1 operator assigned to
     * a later range can no longer be applied at the left-hand numbers. Returns null when the range
     * has no unambiguous counterpart, which is treated as a drop rather than guessed at.
     */
    private static int[] mapRange(String leftText, String rightText, int begin, int end) {
        int lo = -1;
        int hi = -1;
        for (Opcode op : diff(leftText, rightText)) {
            int lineFrom = Math.max(begin, op.i1() + 1);
            int lineTo = Math.min(end, op.i2());
            if (lineFrom > lineTo) {
                continue;
            }
            int rightFrom;
            int rightTo;
            if (op.equal()) {
                rightFrom = op.j1() + (lineFrom - (op.i1() + 1)) + 1;
                rightTo = op.j1() + (lineTo - (op.i1() + 1)) + 1;
            } else {
                // A changed block has no line-for-line counterpart, so the whole opposing extent is
                // taken. Counting only the identical lines would collapse the mapped range: a
                // renamed six-line block mapped to the single line the rename did not touch.
                rightFrom = op.j1() + 1;
                rightTo = op.j2();
            }
            if (rightTo < rightFrom) {
                continue;
            }
            lo = lo < 0 ? rightFrom : Math.min(lo, rightFrom);
            hi = hi < 0 ? rightTo : Math.max(hi, rightTo);
        }
        return lo < 0 ? null : new int[]{lo, hi};
    }

    /**
     * Layout and comment edits, confined to {@code [begin,end]} of the printed right side. None of
     * them can break compilation, and after removing layout and comments the range is unchanged --
     * which is exactly what makes the range a Type-1 clone rather than a Type-2 one.
     */
    private static String applyTextOperator(String operator, String text, int begin, int end,
                                            Random random) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        if (begin < 1 || end > lines.size() || end < begin) {
            return null;
        }
        // Modify-in-place picks any line of the range. Insert-style operators must pick a GAP
        // strictly inside it: inserting before the range's first line puts the new line outside the
        // range it is supposed to belong to, which measured as 3 of 12 blank lines and 1 of 12 block
        // comments landing outside their own reference.
        int target = begin - 1 + random.nextInt(end - begin + 1);
        int gap = end > begin ? begin + random.nextInt(end - begin) : begin;
        switch (operator) {
            case "t1_add_eol_comment" -> lines.set(target, lines.get(target) + " // note " + random.nextInt(97));
            case "t1_add_blank_line" -> lines.add(gap, "");
            case "t1_add_block_comment" -> {
                String indent = lines.get(gap).replaceAll("\\S.*$", "");
                lines.add(gap, indent + "/* note " + random.nextInt(97) + " */");
            }
            case "t1_reindent" -> {
                // Extra leading whitespace only: no token gains, loses or changes text.
                for (int k = begin - 1; k < end; k++) {
                    if (!lines.get(k).isBlank()) {
                        lines.set(k, "    " + lines.get(k));
                    }
                }
            }
            default -> throw new IllegalArgumentException("unknown text operator: " + operator);
        }
        return String.join("\n", lines);
    }

    /** Apply one named operator inside one range; null when it has nothing to work on there. */
    private static String applyOperator(String operator, CtModel model, Range range,
                                        Factory factory, Random random) {
        return switch (operator) {
            case "t2_rename_local" -> applyT2(model, range);
            case "t2_change_int_literal" -> changeLiteral(range, factory, random, false);
            case "t2_change_string_literal" -> changeLiteral(range, factory, random, true);
            case "t3_insert_statement" -> insertStatement(range, factory, random);
            case "t3_delete_statement" -> deleteStatement(range, random);
            case "t3_wrap_statement" -> wrapStatement(range, factory, random);
            default -> throw new IllegalArgumentException("unknown operator: " + operator);
        };
    }

    /**
     * Replace a literal's value, keeping its type so the result still compiles. Normalisation maps
     * every literal of a kind to one placeholder, so this is a Type-2 change: the statement matches
     * after normalisation and no statement was added or removed.
     */
    private static String changeLiteral(Range range, Factory factory, Random random, boolean strings) {
        for (CtStatement statement : range.statements) {
            for (CtLiteral<?> literal : statement.getElements(new TypeFilter<>(CtLiteral.class))) {
                Object value = literal.getValue();
                if (strings && value instanceof String text) {
                    @SuppressWarnings("unchecked")
                    CtLiteral<Object> target = (CtLiteral<Object>) literal;
                    target.setValue(text + "_v" + random.nextInt(97));
                    return "t2_change_string_literal";
                }
                if (!strings && value instanceof Integer number) {
                    @SuppressWarnings("unchecked")
                    CtLiteral<Object> target = (CtLiteral<Object>) literal;
                    // A different value, never the same one: an unchanged literal is not a mutation.
                    target.setValue(number + 1 + random.nextInt(7));
                    return "t2_change_int_literal";
                }
            }
        }
        return null;
    }

    private static String insertStatement(Range range, Factory factory, Random random) {
        List<CtStatement> candidates = new ArrayList<>(range.statements);
        Collections.shuffle(candidates, random);
        for (CtStatement candidate : candidates) {
            if (abrupt(candidate)) {
                continue;
            }
            // The inserted statement must not normalise onto one already present. A plain
            // `int x = 5;` becomes `int ID = NUM ;` under Type-2 normalisation, which is what every
            // other int declaration becomes -- so the statement-level LCS paired the insertion with
            // an existing declaration and reported a RENAME rather than an insertion. Measured: 9
            // of 100 insertions per batch were typed T2 for this reason, and the detector was right
            // each time. A compound initialiser has a token shape ordinary declarations lack.
            CtLocalVariable<Integer> fresh = factory.Code().createLocalVariable(
                    factory.Type().integerPrimitiveType(),
                    "rIns" + Math.abs(random.nextInt(9973)),
                    factory.Code().createCodeSnippetExpression(
                            "((" + (random.nextInt(97) + 3) + " % " + (random.nextInt(29) + 5)
                                    + ") + (" + (random.nextInt(13) + 2) + " * "
                                    + (random.nextInt(7) + 2) + "))"));
            candidate.insertAfter(fresh);
            return "t3_insert_statement";
        }
        return null;
    }

    /**
     * Delete one SINGLE-LINE statement, mirroring mDL, which deletes a line.
     *
     * Allowing any deletable statement let it remove a whole {@code while} loop with its body: all
     * 16 lines of the range vanished, leaving a "clone interval" with no counterpart at all. A
     * multi-line deletion is a different edit from the operator it stands for.
     */
    private static String deleteStatement(Range range, Random random) {
        List<CtStatement> candidates = new ArrayList<>(range.statements);
        Collections.shuffle(candidates, random);
        // Prefer a statement whose normalised shape is unique in its method. Deleting one with a
        // normalised twin lets the statement-level LCS re-pair the survivors and report a rename
        // instead of a deletion -- the mirror of the insertion problem above, and equally not a
        // detector error. Falls back to any deletable statement rather than dropping the pair.
        List<CtStatement> unique = new ArrayList<>();
        List<CtStatement> rest = new ArrayList<>();
        for (CtStatement candidate : candidates) {
            if (!deletable(candidate) || candidate.getPosition() == null
                    || !candidate.getPosition().isValidPosition()
                    || candidate.getPosition().getEndLine() != candidate.getPosition().getLine()) {
                continue;
            }
            if (normalisedTwinExists(candidate)) {
                rest.add(candidate);
            } else {
                unique.add(candidate);
            }
        }
        unique.addAll(rest);
        for (CtStatement candidate : unique) {
            candidate.delete();
            return "t3_delete_statement";
        }
        return null;
    }

    private static String wrapStatement(Range range, Factory factory, Random random) {
        List<CtStatement> candidates = new ArrayList<>(range.statements);
        Collections.shuffle(candidates, random);
        for (CtStatement candidate : candidates) {
            if (candidate instanceof CtLocalVariable || containsAbrupt(candidate)) {
                continue;
            }
            CtIf conditional = factory.Core().createIf();
            conditional.setCondition(factory.Code().createLiteral(true));
            CtBlock<?> then = factory.Core().createBlock();
            then.addStatement(candidate.clone());
            conditional.setThenStatement(then);
            candidate.replace(conditional);
            return "t3_wrap_statement";
        }
        return null;
    }

    /** Does another statement in the same method share this one's Type-2 normalised shape? */
    private static boolean normalisedTwinExists(CtStatement statement) {
        CtElement scope = statement.getParent(CtExecutable.class);
        if (scope == null) {
            return false;
        }
        String shape = normalisedShape(statement);
        int seen = 0;
        for (CtStatement other : scope.getElements(new TypeFilter<>(CtStatement.class))) {
            if (other instanceof CtBlock || other instanceof CtComment) {
                continue;
            }
            if (normalisedShape(other).equals(shape)) {
                seen++;
                if (seen > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Identifiers, numbers and strings collapsed, mirroring the detector's Type-2 view. */
    private static String normalisedShape(CtStatement statement) {
        String text = statement.toString();
        text = text.replaceAll("\"([^\"\\\\]|\\\\.)*\"", "STR");
        text = text.replaceAll("\\b\\d[\\d_]*(\\.\\d+)?[fFdDlL]?\\b", "NUM");
        text = text.replaceAll("[A-Za-z_$][\\w$]*", "ID");
        return text.replaceAll("\\s+", "");
    }

    private static String applyT2(CtModel model, Range range) {
        int renamed = 0;
        for (CtStatement statement : range.statements) {
            for (CtLocalVariable<?> declaration : statement.getElements(new TypeFilter<>(CtLocalVariable.class))) {
                if (!confinedTo(declaration, range)) {
                    continue;
                }
                CtRenameLocalVariableRefactoring refactoring = new CtRenameLocalVariableRefactoring();
                refactoring.setTarget(declaration);
                refactoring.setNewName("r" + Integer.toHexString(declaration.getSimpleName().hashCode() & 0xfff)
                        + "_" + declaration.getSimpleName());
                try {
                    refactoring.refactor();
                    renamed++;
                } catch (RuntimeException refused) {
                    // Spoon detected a conflict; leave this variable alone.
                }
            }
        }
        return renamed > 0 ? "t2_rename_local_x" + renamed : null;
    }

    private static boolean confinedTo(CtLocalVariable<?> declaration, Range range) {
        String name = declaration.getSimpleName();
        CtElement scope = declaration.getParent(CtExecutable.class);
        if (scope == null) {
            return false;
        }
        for (CtVariableReference<?> ref : scope.getElements(
                (TypeFilter<CtVariableReference<?>>) new TypeFilter(CtVariableReference.class))) {
            if (!name.equals(ref.getSimpleName())) {
                continue;
            }
            CtElement holder = ref.getParent();
            if (holder == null || holder.getPosition() == null || !holder.getPosition().isValidPosition()) {
                return false;
            }
            int line = holder.getPosition().getLine();
            if (line < range.beginLine || line > range.endLine) {
                return false;
            }
        }
        return true;
    }

    private static boolean abrupt(CtStatement statement) {
        return statement instanceof CtReturn || statement instanceof CtBreak
                || statement instanceof CtContinue || statement instanceof CtThrow;
    }

    private static boolean containsAbrupt(CtStatement statement) {
        return abrupt(statement)
                || !statement.getElements(new TypeFilter<>(CtReturn.class)).isEmpty()
                || !statement.getElements(new TypeFilter<>(CtThrow.class)).isEmpty()
                || !statement.getElements(new TypeFilter<>(CtBreak.class)).isEmpty()
                || !statement.getElements(new TypeFilter<>(CtContinue.class)).isEmpty();
    }

    private static boolean deletable(CtStatement statement) {
        if (abrupt(statement)) {
            return false;
        }
        CtElement scope = statement.getParent(CtExecutable.class);
        if (scope == null) {
            return false;
        }
        if (statement instanceof CtLocalVariable<?> declaration) {
            String name = declaration.getSimpleName();
            for (CtVariableReference<?> ref : scope.getElements(
                    (TypeFilter<CtVariableReference<?>>) new TypeFilter(CtVariableReference.class))) {
                if (name.equals(ref.getSimpleName())) {
                    return false;
                }
            }
        }
        if (statement instanceof CtAssignment<?, ?> assignment
                && assignment.getAssigned() instanceof CtVariableWrite<?> write
                && write.getVariable() != null) {
            String name = write.getVariable().getSimpleName();
            for (CtVariableRead<?> read : scope.getElements(
                    (TypeFilter<CtVariableRead<?>>) new TypeFilter(CtVariableRead.class))) {
                if (read.getVariable() != null && name.equals(read.getVariable().getSimpleName())) {
                    return false;
                }
            }
        }
        return true;
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

    private static String publicTypeName(String source) {
        Matcher matcher = PUBLIC_TYPE.matcher(source);
        return matcher.find() ? matcher.group(1) : "Main";
    }

    private static boolean compiles(String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try {
            Path root = Files.createTempDirectory("regen-c-");
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
