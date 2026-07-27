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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

    public static void main(String[] args) throws Exception {
        Path seedDir = Path.of(args[0]);
        Path out = Path.of(args[1]);
        int wantedPairs = args.length > 2 ? Integer.parseInt(args[2]) : 100;
        int rangesPerPair = args.length > 3 ? Integer.parseInt(args[3]) : 3;
        int rangeLines = args.length > 4 ? Integer.parseInt(args[4]) : 6;
        int padLines = args.length > 5 ? Integer.parseInt(args[5]) : 3;
        long rngSeed = args.length > 6 ? Long.parseLong(args[6]) : 42L;

        List<Path> seeds;
        try (var stream = Files.walk(seedDir)) {
            seeds = new ArrayList<>(stream.filter(p -> p.toString().endsWith(".java")).sorted().toList());
        }
        Collections.shuffle(seeds, new Random(rngSeed));

        Files.createDirectories(out.resolve("pairs"));
        StringBuilder regions = new StringBuilder("pair_id,region_index,clone_type,operator,left_begin,left_end\n");
        StringBuilder manifest = new StringBuilder("pair_id,seed_file,left_path,right_path\n");
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
                        rngSeed + attempted, regions, manifest);
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
                                   int padLines, long rng, StringBuilder regions, StringBuilder manifest)
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

        // Assign a type per range, alternating so a pair carries more than one relationship.
        List<String> applied = new ArrayList<>();
        Random random = new Random(rng);
        for (int i = 0; i < chosen.size(); i++) {
            Range range = chosen.get(i);
            String operator = (i % 2 == 0) ? applyT2(model, range) : applyT3(model, range, launcher.getFactory(), random);
            if (operator == null) {
                deleteTree(scratch);
                return "no applicable operator in a chosen range";
            }
            applied.add(operator);
        }

        String rightText = printModel(model);
        if (rightText.equals(leftText)) {
            deleteTree(scratch);
            return "mutations produced no textual change";
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

        for (int i = 0; i < chosen.size(); i++) {
            Range range = chosen.get(i);
            String operator = applied.get(i);
            String type = operator.startsWith("rename") ? "T2" : "T3";
            regions.append(pairId).append(',').append(i).append(',').append(type).append(',')
                    .append(operator).append(',').append(range.beginLine).append(',')
                    .append(range.endLine).append('\n');
        }
        manifest.append(pairId).append(',').append(seed.getFileName()).append(',')
                .append(leftPath.toAbsolutePath()).append(',')
                .append(rightPath.toAbsolutePath()).append('\n');
        return null;
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
        return renamed > 0 ? "rename_local_x" + renamed : null;
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

    /** One statement-level edit inside the range, using the operators measured at ~100% survival. */
    private static String applyT3(CtModel model, Range range, Factory factory, Random random) {
        List<CtStatement> candidates = new ArrayList<>(range.statements);
        Collections.shuffle(candidates, random);

        for (CtStatement candidate : candidates) {
            if (!abrupt(candidate)) {
                CtLocalVariable<Integer> fresh = factory.Code().createLocalVariable(
                        factory.Type().integerPrimitiveType(),
                        "rIns" + Math.abs(random.nextInt(9973)),
                        factory.Code().createLiteral(random.nextInt(97)));
                candidate.insertAfter(fresh);
                return "insert_statement";
            }
        }
        for (CtStatement candidate : candidates) {
            if (deletable(candidate)) {
                candidate.delete();
                return "delete_statement";
            }
        }
        for (CtStatement candidate : candidates) {
            if (candidate instanceof CtLocalVariable || containsAbrupt(candidate)) {
                continue;
            }
            CtIf conditional = factory.Core().createIf();
            CtLiteral<Boolean> alwaysTrue = factory.Code().createLiteral(true);
            conditional.setCondition(alwaysTrue);
            CtBlock<?> then = factory.Core().createBlock();
            then.addStatement(candidate.clone());
            conditional.setThenStatement(then);
            candidate.replace(conditional);
            return "wrap_statement";
        }
        return null;
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
