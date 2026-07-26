package com.ziqi.codesim.next.semantic.project;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.ziqi.codesim.ast.AstTokenizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded project-level retrieval in front of the expensive pairwise WALA pipeline.
 *
 * <p>It indexes normalized AST shingles and coarse method shapes in an inverted index, retrieves
 * only Top-K method neighbors, then aggregates them into ranked file pairs. It does not assign clone
 * types; every returned file pair still goes through the existing detector.
 */
public final class ProjectCandidateIndexer {
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".idea", ".gradle", "target", "build", "out", "node_modules",
            "generated", "generated-sources");
    private static final int MAX_POSTING = 2_000;

    public ProjectScanPlan plan(Path leftRoot, Path rightRoot, int methodTopK, int filePairLimit)
            throws IOException {
        Path left = leftRoot.toAbsolutePath().normalize();
        Path right = rightRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(left) || !Files.isDirectory(right)) {
            throw new IllegalArgumentException("Both project roots must be directories");
        }
        IndexSide leftSide = index(left);
        IndexSide rightSide = left.equals(right) ? leftSide : index(right);
        List<ProjectFileCandidate> candidates = retrieve(
                leftSide.methods, rightSide.methods, left.equals(right), methodTopK, filePairLimit);
        List<String> warnings = new ArrayList<>(leftSide.warnings);
        if (rightSide != leftSide) {
            warnings.addAll(rightSide.warnings);
        }
        return new ProjectScanPlan(left, right, leftSide.fileCount, rightSide.fileCount,
                leftSide.methods.size(), rightSide.methods.size(), List.copyOf(warnings), candidates);
    }

    private static IndexSide index(Path root) throws IOException {
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !excluded(root.relativize(path)))
                    .sorted()
                    .toList();
        }
        List<MethodFingerprint> methods = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Path file : files) {
            try {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                CompilationUnit cu = AstTokenizer.parse(source);
                List<MethodDeclaration> declarations = cu.findAll(MethodDeclaration.class);
                for (int i = 0; i < declarations.size(); i++) {
                    methods.add(fingerprint(root, file, declarations.get(i), i));
                }
            } catch (RuntimeException | IOException ex) {
                warnings.add(root.relativize(file) + ": " + compact(ex.getMessage()));
            }
        }
        return new IndexSide(files.size(), List.copyOf(methods), List.copyOf(warnings));
    }

    private static MethodFingerprint fingerprint(Path root, Path file,
                                                 MethodDeclaration method, int ordinal) {
        List<String> tokens = AstTokenizer.tokenize(method);
        Set<String> features = new LinkedHashSet<>();
        for (int i = 0; i + 2 < tokens.size(); i++) {
            features.add("tri:" + tokens.get(i) + '|' + tokens.get(i + 1) + '|' + tokens.get(i + 2));
        }
        Map<String, Integer> nodeCounts = new HashMap<>();
        for (String token : tokens) {
            if (token.startsWith("N:")) {
                nodeCounts.merge(token, 1, Integer::sum);
            }
        }
        nodeCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> features.add("hist:" + entry.getKey() + ':' + bucket(entry.getValue())));
        int statements = method.findAll(com.github.javaparser.ast.stmt.Statement.class).size();
        int controls = method.findAll(com.github.javaparser.ast.stmt.IfStmt.class).size()
                + method.findAll(com.github.javaparser.ast.stmt.ForStmt.class).size()
                + method.findAll(com.github.javaparser.ast.stmt.ForEachStmt.class).size()
                + method.findAll(com.github.javaparser.ast.stmt.WhileStmt.class).size()
                + method.findAll(com.github.javaparser.ast.stmt.SwitchStmt.class).size();
        String shape = method.getParameters().size() + ":" + bucket(statements) + ":"
                + bucket(controls) + ":" + returnCategory(method);
        features.add("shape:" + shape);
        return new MethodFingerprint(file.toAbsolutePath().normalize(),
                root.relativize(file).toString(), method.getNameAsString() + "#" + ordinal,
                Set.copyOf(features), tokens.size(), shape);
    }

    private static List<ProjectFileCandidate> retrieve(List<MethodFingerprint> left,
                                                       List<MethodFingerprint> right,
                                                       boolean sameProject,
                                                       int methodTopK,
                                                       int filePairLimit) {
        if (methodTopK < 1 || filePairLimit < 1) {
            throw new IllegalArgumentException("Top-K and file-pair limit must be positive");
        }
        Map<String, List<Integer>> postings = new HashMap<>();
        Map<String, List<Integer>> byShape = new HashMap<>();
        for (int index = 0; index < right.size(); index++) {
            MethodFingerprint method = right.get(index);
            byShape.computeIfAbsent(method.shape, ignored -> new ArrayList<>()).add(index);
            for (String feature : method.features) {
                postings.computeIfAbsent(feature, ignored -> new ArrayList<>()).add(index);
            }
        }
        Map<FilePair, Aggregate> filePairs = new LinkedHashMap<>();
        for (MethodFingerprint query : left) {
            Map<Integer, Integer> shared = new HashMap<>();
            for (String feature : query.features) {
                List<Integer> posting = postings.getOrDefault(feature, List.of());
                if (posting.size() > MAX_POSTING) {
                    continue;
                }
                for (int candidate : posting) {
                    shared.merge(candidate, 1, Integer::sum);
                }
            }
            // Shape is the semantic-divergence safety net: methods with few shared tokens still get
            // bounded candidates with the same arity/control/size/return profile.
            for (int candidate : byShape.getOrDefault(query.shape, List.of()).stream()
                    .limit(Math.max(methodTopK * 8L, 32L)).toList()) {
                shared.putIfAbsent(candidate, 0);
            }
            shared.entrySet().stream()
                    .map(entry -> new MethodNeighbor(entry.getKey(), score(
                            query, right.get(entry.getKey()), entry.getValue())))
                    .filter(neighbor -> neighbor.score > 0.0)
                    .sorted(Comparator.comparingDouble(MethodNeighbor::score).reversed()
                            .thenComparing(neighbor -> right.get(neighbor.index).relativeFile))
                    .limit(methodTopK)
                    .forEach(neighbor -> {
                        MethodFingerprint candidate = right.get(neighbor.index);
                        if (query.file.equals(candidate.file)) {
                            return;
                        }
                        Path leftFile = query.file;
                        Path rightFile = candidate.file;
                        if (sameProject && leftFile.toString().compareTo(rightFile.toString()) > 0) {
                            Path swap = leftFile;
                            leftFile = rightFile;
                            rightFile = swap;
                        }
                        FilePair key = new FilePair(leftFile, rightFile);
                        filePairs.computeIfAbsent(key, ignored -> new Aggregate())
                                .add(neighbor.score, query.methodId, candidate.methodId);
                    });
        }
        return filePairs.entrySet().stream()
                .map(entry -> entry.getValue().candidate(entry.getKey()))
                .sorted(Comparator.comparingDouble(ProjectFileCandidate::retrievalScore).reversed()
                        .thenComparing(candidate -> candidate.leftFile().toString())
                        .thenComparing(candidate -> candidate.rightFile().toString()))
                .limit(filePairLimit)
                .toList();
    }

    private static double score(MethodFingerprint left, MethodFingerprint right, int shared) {
        double structural = shared / Math.sqrt(
                Math.max(1.0, (double) left.features.size() * right.features.size()));
        double length = 1.0 - Math.abs(left.tokenCount - right.tokenCount)
                / (double) Math.max(1, Math.max(left.tokenCount, right.tokenCount));
        double shape = left.shape.equals(right.shape) ? 1.0 : 0.0;
        return Math.min(1.0, 0.65 * structural + 0.25 * length + 0.10 * shape);
    }

    private static int bucket(int value) {
        if (value <= 0) return 0;
        if (value <= 2) return 1;
        if (value <= 5) return 2;
        if (value <= 10) return 3;
        if (value <= 20) return 4;
        if (value <= 50) return 5;
        return 6;
    }

    private static String returnCategory(MethodDeclaration method) {
        String type = method.getType().asString();
        if (type.equals("void")) return "void";
        if (Set.of("byte", "short", "int", "long", "float", "double", "char").contains(type)) {
            return "numeric";
        }
        if (type.equals("boolean")) return "boolean";
        if (type.endsWith("[]")) return "array";
        return "reference";
    }

    private static boolean excluded(Path relative) {
        for (Path part : relative) {
            if (EXCLUDED_DIRECTORIES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static String compact(String message) {
        if (message == null) return "unknown parse failure";
        String compact = message.replaceAll("\\s+", " ").strip();
        return compact.length() <= 300 ? compact : compact.substring(0, 300);
    }

    public record ProjectScanPlan(
            Path leftRoot,
            Path rightRoot,
            int leftJavaFiles,
            int rightJavaFiles,
            int leftMethods,
            int rightMethods,
            List<String> warnings,
            List<ProjectFileCandidate> candidates
    ) {
    }

    public record ProjectFileCandidate(
            Path leftFile,
            Path rightFile,
            double retrievalScore,
            int supportingMethodPairs,
            List<String> exampleMethodPairs
    ) {
    }

    private record IndexSide(int fileCount, List<MethodFingerprint> methods, List<String> warnings) {
    }

    private record MethodFingerprint(Path file, String relativeFile, String methodId,
                                     Set<String> features, int tokenCount, String shape) {
    }

    private record MethodNeighbor(int index, double score) {
    }

    private record FilePair(Path left, Path right) {
    }

    private static final class Aggregate {
        private double maxScore;
        private int support;
        private final Set<String> examples = new LinkedHashSet<>();

        void add(double score, String leftMethod, String rightMethod) {
            maxScore = Math.max(maxScore, score);
            support++;
            if (examples.size() < 5) {
                examples.add(leftMethod + " -> " + rightMethod);
            }
        }

        ProjectFileCandidate candidate(FilePair pair) {
            double supportScore = Math.min(1.0, Math.log1p(support) / Math.log(6.0));
            return new ProjectFileCandidate(pair.left, pair.right,
                    0.85 * maxScore + 0.15 * supportScore, support, List.copyOf(examples));
        }
    }
}
