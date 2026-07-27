package com.ziqi.mutgen;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a reusable library of portable donor blocks for the region-level NON_CLONE negative.
 *
 * A negative region is real code from an unrelated file, inserted into B with no counterpart in A.
 * Its label is only true if the block (a) does not resemble the destination and (b) still
 * compiles there. Measurement showed resemblance is a non-issue — the longest shared consecutive
 * token run between a self-contained block and a whole seed had median 4 and max 5 tokens — while
 * portability is the real constraint, and the earlier probe's self-containment test was too weak:
 * it checked variable references only, so blocks calling the donor's own methods
 * ({@code ni()}, {@code nextLong()}) or naming its nested helper classes ({@code FastReader})
 * passed the check and then failed to compile.
 *
 * Portability here therefore requires all three: every variable read is declared inside the block,
 * every invoked method belongs to a JDK type, and every named type is a JDK or primitive type.
 * Claiming portability is still not the same as having it, so every surviving block is
 * test-inserted into several unrelated seeds and compiled; only blocks that compile in all of them
 * enter the library. Blocks are reusable, which is what makes a low discovery rate acceptable.
 *
 * Usage: DonorLibraryBuilder &lt;corpus-dir&gt; &lt;out-dir&gt; [scan-files] [block-statements] [validation-seeds]
 */
public final class DonorLibraryBuilder {

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_]\\w*|\\d+|[^\\s\\w]");
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "public\\s+(?:final\\s+|abstract\\s+)*(?:class|interface|enum|record)\\s+(\\w+)");

    public static void main(String[] args) throws Exception {
        Path corpus = Path.of(args[0]);
        Path out = Path.of(args[1]);
        int scanFiles = args.length > 2 ? Integer.parseInt(args[2]) : 1500;
        int blockStatements = args.length > 3 ? Integer.parseInt(args[3]) : 3;
        int validationSeeds = args.length > 4 ? Integer.parseInt(args[4]) : 5;
        int minSubstantive = args.length > 5 ? Integer.parseInt(args[5]) : 3;

        List<Path> files;
        try (var stream = Files.walk(corpus)) {
            files = new ArrayList<>(stream.filter(p -> p.toString().endsWith(".java")).toList());
        }
        Collections.shuffle(files, new Random(7));

        // Validation destinations: unrelated seeds that compile on their own.
        List<String> destinations = new ArrayList<>();
        for (Path candidate : files.subList(0, Math.min(120, files.size()))) {
            try {
                String printed = printModel(build(candidate).getModel());
                if (compiles(printed) && !topLevelStatements(build(candidate).getModel()).isEmpty()) {
                    destinations.add(printed);
                }
            } catch (Throwable ignored) {
                // not usable as a destination
            }
            if (destinations.size() >= validationSeeds) {
                break;
            }
        }
        if (destinations.size() < validationSeeds) {
            throw new IllegalStateException("only " + destinations.size() + " validation destinations found");
        }

        List<Path> scanPool = files.subList(Math.min(120, files.size()), files.size());
        Files.createDirectories(out.resolve("blocks"));
        StringBuilder manifest = new StringBuilder("block_id,source_file,statements,lines,tokens,imports\n");

        int scanned = 0, candidates = 0, portable = 0, validated = 0;
        for (Path file : scanPool) {
            if (scanned >= scanFiles) {
                break;
            }
            scanned++;
            try {
                Launcher launcher = build(file);
                Block block = portableBlock(launcher.getModel(), blockStatements, minSubstantive);
                if (block == null) {
                    continue;
                }
                candidates++;
                portable++;

                String rendered = block.render("zz" + Integer.toHexString(scanned) + "_");
                boolean everywhere = true;
                for (String destination : destinations) {
                    if (!compiles(insert(destination, rendered, block.imports))) {
                        everywhere = false;
                        break;
                    }
                }
                if (!everywhere) {
                    continue;
                }
                validated++;

                String id = String.format("D%04d", validated);
                Files.writeString(out.resolve("blocks").resolve(id + ".txt"), rendered, StandardCharsets.UTF_8);
                manifest.append(id).append(',').append(file.getFileName()).append(',')
                        .append(block.statements.size()).append(',')
                        .append(rendered.split("\n", -1).length).append(',')
                        .append(tokenCount(rendered)).append(',')
                        .append('"').append(String.join(";", block.imports)).append('"').append('\n');
            } catch (Throwable ignored) {
                // unusable donor
            }
        }

        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        System.out.printf("%nscanned=%d  portable-by-analysis=%d (%.1f%%)  validated-by-compilation=%d (%.1f%%)%n",
                scanned, portable, pct(portable, scanned), validated, pct(validated, scanned));
        System.out.printf("validation destinations per block: %d%n", destinations.size());
        System.out.printf("library written to %s%n", out.toAbsolutePath());
    }

    private record Block(List<CtStatement> statements, Set<String> imports) {
        /**
         * Comments are dropped and every declared name is prefixed. Comments break the rendering
         * (an end-of-line comment pushes the appended terminator onto the next line) and carry the
         * donor's natural language into a region whose whole purpose is to be unrelated; the prefix
         * stops a donated name from colliding with one already in scope at the destination.
         */
        String render(String prefix) {
            Set<String> declared = new LinkedHashSet<>();
            List<CtStatement> copies = new ArrayList<>();
            for (CtStatement statement : statements) {
                CtStatement copy = statement.clone();
                copy.getElements(new TypeFilter<>(spoon.reflect.declaration.CtElement.class))
                        .forEach(e -> e.setComments(List.of()));
                copy.setComments(List.of());
                copies.add(copy);
                for (CtLocalVariable<?> declaration : copy.getElements(new TypeFilter<>(CtLocalVariable.class))) {
                    declared.add(declaration.getSimpleName());
                }
            }
            for (CtStatement copy : copies) {
                for (String name : declared) {
                    for (CtVariableReference<?> ref : copy.getElements(
                            (TypeFilter<CtVariableReference<?>>) new TypeFilter(CtVariableReference.class))) {
                        if (name.equals(ref.getSimpleName())) {
                            ref.setSimpleName(prefix + name);
                        }
                    }
                    for (CtLocalVariable<?> declaration : copy.getElements(new TypeFilter<>(CtLocalVariable.class))) {
                        if (name.equals(declaration.getSimpleName())) {
                            declaration.setSimpleName(prefix + name);
                        }
                    }
                }
            }
            StringBuilder sb = new StringBuilder();
            for (CtStatement copy : copies) {
                sb.append("        ").append(copy.toString()).append(";\n");
            }
            return sb.toString();
        }
    }

    /**
     * A run of consecutive statements that names nothing outside itself except JDK types.
     */
    /**
     * The portable run with the most substantive statements, not merely the first one found.
     *
     * Taking the first run yields blocks of bare declarations, because that is how a CodeNet main
     * begins. Such a block is portable and worthless: RegionGrower requires two SUBSTANTIVE
     * aligned pairs, and a declaration without work does not qualify, so the detector declining to
     * report it demonstrates nothing. A negative region only discriminates if it looks like code
     * that could plausibly have been reported.
     */
    private static Block portableBlock(CtModel model, int wanted, int minSubstantive) {
        Block best = null;
        int bestScore = -1;
        for (CtExecutable<?> executable : model.getElements(new TypeFilter<>(CtExecutable.class))) {
            CtBlock<?> body = executable.getBody();
            if (body == null) {
                continue;
            }
            List<CtStatement> statements = body.getStatements();
            for (int start = 0; start < statements.size(); start++) {
                Set<String> declared = new HashSet<>();
                Set<String> imports = new LinkedHashSet<>();
                List<CtStatement> run = new ArrayList<>();
                for (int end = start; end < statements.size(); end++) {
                    CtStatement statement = statements.get(end);
                    if (statement instanceof spoon.reflect.code.CtComment) {
                        // Spoon models a comment as a statement. A run of commented-out code has no
                        // variable references, counts as "not a declaration", and compiles - so it
                        // passes every check while being no code at all.
                        break;
                    }
                    if (statement instanceof CtLocalVariable<?> declaration) {
                        declared.add(declaration.getSimpleName());
                    }
                    if (!selfContained(statement, declared, imports)) {
                        break;
                    }
                    run.add(statement);
                    if (run.size() >= wanted) {
                        int score = substantive(run);
                        if (score >= minSubstantive && score > bestScore) {
                            bestScore = score;
                            best = new Block(new ArrayList<>(run), new LinkedHashSet<>(imports));
                        }
                        break;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Statements that do real work, mirroring RegionGrower's substantive-node kinds: a call, an
     * assignment, a branch, or a loop. A declaration counts only when its initialiser itself
     * computes something — `new ArrayList<>()` is allocation without computation, and a block of
     * six such declarations is not a region any detector would be expected to report.
     */
    private static int substantive(List<CtStatement> run) {
        int count = 0;
        for (CtStatement statement : run) {
            if (statement instanceof spoon.reflect.code.CtComment) {
                continue;
            }
            boolean works = !statement.getElements(new TypeFilter<>(CtInvocation.class)).isEmpty()
                    || !statement.getElements(new TypeFilter<>(spoon.reflect.code.CtAssignment.class)).isEmpty()
                    || !statement.getElements(new TypeFilter<>(spoon.reflect.code.CtBinaryOperator.class)).isEmpty()
                    || statement instanceof spoon.reflect.code.CtIf
                    || statement instanceof spoon.reflect.code.CtLoop;
            if (works) {
                count++;
            }
        }
        return count;
    }

    private static boolean selfContained(CtStatement statement, Set<String> declared, Set<String> imports) {
        for (CtVariableReference<?> ref : statement.getElements(
                (TypeFilter<CtVariableReference<?>>) new TypeFilter(CtVariableReference.class))) {
            if (!declared.contains(ref.getSimpleName())) {
                return false;
            }
        }
        // Every invoked method must belong to a JDK type: a call to the donor's own helper
        // (ni(), nextLong()) compiles in the donor and nowhere else.
        for (CtInvocation<?> invocation : statement.getElements(new TypeFilter<>(CtInvocation.class))) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null || executable.getDeclaringType() == null) {
                return false;
            }
            if (!jdkType(executable.getDeclaringType(), imports)) {
                return false;
            }
        }
        for (CtTypeReference<?> type : statement.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (!jdkType(type, imports)) {
                return false;
            }
        }
        return true;
    }

    /** JDK or primitive; records the import a destination file would need. */
    private static boolean jdkType(CtTypeReference<?> type, Set<String> imports) {
        if (type.isPrimitive() || type.getQualifiedName() == null) {
            return true;
        }
        String qualified = type.getQualifiedName();
        if (qualified.isEmpty() || qualified.equals("void")) {
            return true;
        }
        if (type.isArray()) {
            return true;
        }
        if (!qualified.startsWith("java.")) {
            return false;
        }
        if (!qualified.startsWith("java.lang.") || qualified.lastIndexOf('.') != 9) {
            imports.add("import " + qualified + ";");
        }
        return true;
    }

    /**
     * Insert the block immediately after the first statement of the destination's first method.
     * Appending at the end of the body puts it after any trailing `return`, which javac rejects as
     * unreachable — that, not the blocks themselves, was what failed validation.
     */
    private static String insert(String destination, String block, Set<String> imports) {
        Matcher method = Pattern.compile("\\)\\s*(?:throws [\\w, .]+)?\\s*\\{").matcher(destination);
        if (!method.find()) {
            return destination;
        }
        int cursor = method.end();
        int semicolon = destination.indexOf(';', cursor);
        int brace = destination.indexOf('{', cursor);
        int anchor = semicolon < 0 ? brace : (brace >= 0 && brace < semicolon ? brace : semicolon);
        if (anchor < 0) {
            return destination;
        }
        StringBuilder sb = new StringBuilder();
        for (String statement : imports) {
            sb.append(statement).append('\n');
        }
        sb.append(destination, 0, anchor + 1).append('\n').append(block)
                .append(destination.substring(anchor + 1));
        return sb.toString();
    }

    private static List<CtStatement> topLevelStatements(CtModel model) {
        List<CtStatement> statements = new ArrayList<>();
        for (CtExecutable<?> executable : model.getElements(new TypeFilter<>(CtExecutable.class))) {
            CtBlock<?> body = executable.getBody();
            if (body != null) {
                statements.addAll(body.getStatements());
            }
        }
        return statements;
    }

    private static int tokenCount(String source) {
        Matcher matcher = TOKEN.matcher(source);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
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
            Path root = Files.createTempDirectory("donorlib-");
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
