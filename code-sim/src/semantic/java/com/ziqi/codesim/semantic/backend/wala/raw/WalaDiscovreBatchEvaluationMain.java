package com.ziqi.codesim.semantic.backend.wala.raw;

import com.ziqi.codesim.semantic.knn.KnnFeatureView;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class WalaDiscovreBatchEvaluationMain {
    private static final List<KnnFeatureView> VIEWS = List.of(
            KnnFeatureView.DISCOVRE_NUMERIC,
            KnnFeatureView.RAW_HASH_BUCKET,
            KnnFeatureView.HYBRID_NUMERIC_HASH
    );
    private static final Pattern PUBLIC_CLASS = Pattern.compile("\\bpublic\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)");

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 6) {
            System.err.println("Usage: WalaDiscovreBatchEvaluationMain <pairs-csv> <output-csv> <work-dir> [blockTopK] [methodTopK] [limit]");
            System.exit(2);
        }
        Path pairsCsv = Path.of(args[0]);
        Path outputCsv = Path.of(args[1]);
        Path workDir = Path.of(args[2]);
        int blockTopK = args.length >= 4 ? Integer.parseInt(args[3]) : 8;
        int methodTopK = args.length >= 5 ? Integer.parseInt(args[4]) : 2;
        int limit = args.length >= 6 ? Integer.parseInt(args[5]) : 0;

        List<Map<String, String>> pairs = readCsv(pairsCsv);
        if (limit > 0 && limit < pairs.size()) {
            pairs = pairs.subList(0, limit);
        }

        Files.createDirectories(outputCsv.toAbsolutePath().getParent());
        Files.createDirectories(workDir);
        try (BufferedWriter writer = Files.newBufferedWriter(outputCsv, StandardCharsets.UTF_8)) {
            writeLine(writer, List.of(
                    "project",
                    "pair_id",
                    "expected_type",
                    "expected_scope",
                    "view",
                    "top_k",
                    "method_top_k",
                    "method_count",
                    "avg_exhaustive_similarity",
                    "avg_constrained_similarity",
                    "avg_similarity_delta",
                    "max_exhaustive_similarity",
                    "max_constrained_similarity",
                    "max_non_constructor_constrained_similarity",
                    "best_left_method",
                    "best_right_method",
                    "best_non_constructor_left_method",
                    "best_non_constructor_right_method",
                    "avg_candidate_reduction",
                    "total_exhaustive_candidate_pairs",
                    "total_constrained_candidate_pairs",
                    "runtime_ms",
                    "status",
                    "error",
                    "transformation_tag",
                    "difficulty",
                    "limitation_tag",
                    "notes"
            ));

            Path root = Path.of("").toAbsolutePath();
            for (int i = 0; i < pairs.size(); i++) {
                Map<String, String> pair = pairs.get(i);
                Path pairDir = workDir.resolve(pair.get("pair_id"));
                Path leftDir = pairDir.resolve("left");
                Path rightDir = pairDir.resolve("right");
                try {
                    compileSource(root.resolve("evaluation").resolve(pair.get("file_a")), leftDir);
                    compileSource(root.resolve("evaluation").resolve(pair.get("file_b")), rightDir);
                    for (KnnFeatureView view : VIEWS) {
                        long start = System.nanoTime();
                        Path reportPath = pairDir.resolve(view.name() + ".csv");
                        WalaDiscovreComparisonMain.main(new String[]{
                                leftDir.toString(),
                                rightDir.toString(),
                                reportPath.toString(),
                                view.name(),
                                Integer.toString(blockTopK),
                                Integer.toString(methodTopK)
                        });
                        long runtimeMs = Math.round((System.nanoTime() - start) / 1_000_000.0);
                        Summary summary = summarizeReport(reportPath);
                        writeResult(writer, pair, view, blockTopK, methodTopK, runtimeMs, "success", "", summary);
                    }
                } catch (Exception e) {
                    for (KnnFeatureView view : VIEWS) {
                        writeResult(writer, pair, view, blockTopK, methodTopK, 0, "failed", e.getMessage(), Summary.empty());
                    }
                }
                writer.flush();
                System.out.println("[" + (i + 1) + "/" + pairs.size() + "] " + pair.get("pair_id"));
            }
        }
        System.out.println("Wrote batch discovRE evaluation results to " + outputCsv.toAbsolutePath());
    }

    private static void compileSource(Path source, Path outputDir) throws IOException {
        deleteDirectory(outputDir);
        Files.createDirectories(outputDir);
        Path compileInput = prepareCompileSource(source, outputDir);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler is available. Run with a JDK, not a JRE.");
        }
        StringBuilder errors = new StringBuilder();
        int status = compiler.run(null, null, new StringOutputStream(errors),
                "-g",
                "-d",
                outputDir.toString(),
                compileInput.toString());
        if (status != 0) {
            throw new IllegalStateException(errors.toString().trim());
        }
    }

    private static Path prepareCompileSource(Path source, Path outputDir) throws IOException {
        String text = Files.readString(source, StandardCharsets.UTF_8);
        Matcher matcher = PUBLIC_CLASS.matcher(text);
        if (!matcher.find()) {
            return source;
        }
        Path sourceDir = outputDir.resolve("__src");
        Files.createDirectories(sourceDir);
        Path renamed = sourceDir.resolve(matcher.group(1) + ".java");
        Files.writeString(renamed, text, StandardCharsets.UTF_8);
        return renamed;
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to delete " + path, e);
                }
            });
        }
    }

    private static Summary summarizeReport(Path reportPath) throws IOException {
        List<Map<String, String>> rows = readCsv(reportPath);
        if (rows.isEmpty()) {
            return Summary.empty();
        }
        double avgExhaustive = average(rows, "exhaustiveSimilarity");
        double avgConstrained = average(rows, "constrainedSimilarity");
        double avgDelta = average(rows, "similarityDelta");
        double avgReduction = average(rows, "candidateReduction");
        int totalExhaustivePairs = rows.stream().mapToInt(row -> integer(row.get("exhaustiveCandidatePairs"))).sum();
        int totalConstrainedPairs = rows.stream().mapToInt(row -> integer(row.get("constrainedCandidatePairs"))).sum();
        Map<String, String> best = maxBy(rows, "constrainedSimilarity");
        Map<String, String> bestNonConstructor = rows.stream()
                .filter(row -> !isConstructor(row.get("leftMethodSignature")))
                .filter(row -> !isConstructor(row.get("rightMethodSignature")))
                .max(java.util.Comparator.comparingDouble(row -> decimal(row.get("constrainedSimilarity"))))
                .orElse(Map.of());
        return new Summary(
                rows.size(),
                avgExhaustive,
                avgConstrained,
                avgDelta,
                rows.stream().mapToDouble(row -> decimal(row.get("exhaustiveSimilarity"))).max().orElse(0.0),
                decimal(best.get("constrainedSimilarity")),
                decimal(bestNonConstructor.get("constrainedSimilarity")),
                best.getOrDefault("leftMethodSignature", ""),
                best.getOrDefault("rightMethodSignature", ""),
                bestNonConstructor.getOrDefault("leftMethodSignature", ""),
                bestNonConstructor.getOrDefault("rightMethodSignature", ""),
                avgReduction,
                totalExhaustivePairs,
                totalConstrainedPairs
        );
    }

    private static void writeResult(
            BufferedWriter writer,
            Map<String, String> pair,
            KnnFeatureView view,
            int blockTopK,
            int methodTopK,
            long runtimeMs,
            String status,
            String error,
            Summary summary) throws IOException {
        writeLine(writer, List.of(
                pair.getOrDefault("project", ""),
                pair.getOrDefault("pair_id", ""),
                pair.getOrDefault("expected_type", ""),
                pair.getOrDefault("expected_scope", ""),
                view.name(),
                Integer.toString(blockTopK),
                Integer.toString(methodTopK),
                Integer.toString(summary.methodCount()),
                Double.toString(summary.avgExhaustiveSimilarity()),
                Double.toString(summary.avgConstrainedSimilarity()),
                Double.toString(summary.avgSimilarityDelta()),
                Double.toString(summary.maxExhaustiveSimilarity()),
                Double.toString(summary.maxConstrainedSimilarity()),
                Double.toString(summary.maxNonConstructorConstrainedSimilarity()),
                summary.bestLeftMethod(),
                summary.bestRightMethod(),
                summary.bestNonConstructorLeftMethod(),
                summary.bestNonConstructorRightMethod(),
                Double.toString(summary.avgCandidateReduction()),
                Integer.toString(summary.totalExhaustiveCandidatePairs()),
                Integer.toString(summary.totalConstrainedCandidatePairs()),
                Long.toString(runtimeMs),
                status,
                error == null ? "" : error,
                pair.getOrDefault("transformation_tag", ""),
                pair.getOrDefault("difficulty", ""),
                pair.getOrDefault("limitation_tag", ""),
                pair.getOrDefault("notes", "")
        ));
    }

    private static List<Map<String, String>> readCsv(Path csvPath) throws IOException {
        List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> header = parseCsvLine(lines.get(0));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            List<String> values = parseCsvLine(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < header.size(); j++) {
                row.put(header.get(j), j < values.size() ? values.get(j) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private static void writeLine(BufferedWriter writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(csv(values.get(i)));
        }
        writer.newLine();
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static double average(List<Map<String, String>> rows, String key) {
        return rows.stream().mapToDouble(row -> decimal(row.get(key))).average().orElse(0.0);
    }

    private static Map<String, String> maxBy(List<Map<String, String>> rows, String key) {
        return rows.stream()
                .max(java.util.Comparator.comparingDouble(row -> decimal(row.get(key))))
                .orElse(Map.of());
    }

    private static boolean isConstructor(String signature) {
        return signature != null && (signature.contains(".<init>(") || signature.contains(".<clinit>("));
    }

    private static double decimal(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(value);
    }

    private static int integer(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private record Summary(
            int methodCount,
            double avgExhaustiveSimilarity,
            double avgConstrainedSimilarity,
            double avgSimilarityDelta,
            double maxExhaustiveSimilarity,
            double maxConstrainedSimilarity,
            double maxNonConstructorConstrainedSimilarity,
            String bestLeftMethod,
            String bestRightMethod,
            String bestNonConstructorLeftMethod,
            String bestNonConstructorRightMethod,
            double avgCandidateReduction,
            int totalExhaustiveCandidatePairs,
            int totalConstrainedCandidatePairs) {
        static Summary empty() {
            return new Summary(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, "", "", "", "", 0.0, 0, 0);
        }
    }

    private static class StringOutputStream extends java.io.OutputStream {
        private final StringBuilder target;

        StringOutputStream(StringBuilder target) {
            this.target = target;
        }

        @Override
        public void write(int b) {
            target.append((char) b);
        }
    }
}
