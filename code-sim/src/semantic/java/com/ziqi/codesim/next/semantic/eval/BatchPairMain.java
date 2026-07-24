package com.ziqi.codesim.next.semantic.eval;

import com.ziqi.codesim.next.NextJsonReportFormatter;
import com.ziqi.codesim.next.semantic.PipelineExecution;
import com.ziqi.codesim.next.semantic.SourceAnalysisInput;
import com.ziqi.codesim.next.semantic.WalaNextPipelineRunner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Headless batch runner for the final (WALA region) pipeline over a manifest of file pairs.
 * Designed for large benchmark sweeps (BCB / SemanticCloneBench / injection corpora):
 *
 * <ul>
 *   <li><b>One JVM, many pairs</b>: avoids per-pair JVM+WALA startup cost.</li>
 *   <li><b>Resume</b>: pair_ids already present in the output file are skipped, so an interrupted
 *       run continues where it stopped (safe for overnight sweeps).</li>
 *   <li><b>JSON-lines output</b>: one line per pair: {@code {"pairId":..., "status":"ok"|"error",
 *       "wallMs":..., "stages":{...}, "report":<full pipeline JSON>}}. A per-pair error is recorded
 *       as a row, never a crash of the sweep.</li>
 * </ul>
 *
 * Manifest format is RFC-4180-style CSV with the required columns
 * {@code pair_id,left_path,right_path}. Clean-room execution manifests also provide
 * {@code dataset_id,left_sha256,right_sha256}; labels and reference ranges are deliberately
 * absent from product input. Relative paths are resolved from the manifest.
 *
 * <p>Usage: {@code BatchPairMain <manifest.csv> <out.jsonl> [--limit N]}
 */
public final class BatchPairMain {
    private static final String SCHEMA_VERSION = "4.1";
    private static final String CONFIG_ID = System.getProperty("codesim.configId", "v4-development-default");
    private static final String DATASET_ID = System.getProperty("codesim.datasetId", "unknown");
    private static final String CODE_COMMIT = System.getProperty("codesim.codeCommit", "unknown");
    private static final String DIRTY_WORKTREE = System.getProperty("codesim.dirtyWorktree", "unknown");
    private static final String FROZEN_MANIFEST_SHA256 =
            System.getProperty("codesim.manifestSha256", "");
    private static final String RUNTIME_CLASSPATH_SHA256 =
            System.getProperty("codesim.runtimeClasspathSha256", "unfrozen");
    private static final Pattern SAFE_PAIR_ID = Pattern.compile("[A-Za-z0-9_.:-]+");
    private static final Pattern OUTPUT_PAIR_ID = Pattern.compile("\\\"pairId\\\":\\\"([A-Za-z0-9_.:-]+)\\\"");
    private static final Pattern OUTPUT_STATUS = Pattern.compile("\\\"status\\\":\\\"(ok|error)\\\"");
    private static final Pattern OUTPUT_ATTEMPT = Pattern.compile("\\\"attempt\\\":([0-9]+)");

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: BatchPairMain <manifest.csv> <out.jsonl> [--limit N]");
            System.exit(2);
        }
        Path manifest = Path.of(args[0]);
        Path out = Path.of(args[1]);
        int limit = Integer.MAX_VALUE;
        int maxAttempts = 1;
        for (int i = 2; i < args.length; i++) {
            if ("--limit".equals(args[i]) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else if ("--max-attempts".equals(args[i]) && i + 1 < args.length) {
                maxAttempts = Integer.parseInt(args[++i]);
            }
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("--max-attempts must be >= 1");
        }

        Map<String, AttemptState> attempts = previousAttempts(out);
        NextJsonReportFormatter formatter = new NextJsonReportFormatter();
        WalaNextPipelineRunner runner = new WalaNextPipelineRunner();

        int ran = 0;
        int skipped = 0;
        int exhausted = 0;
        long sweepStart = System.currentTimeMillis();
        String manifestSha256 = FROZEN_MANIFEST_SHA256.isBlank()
                ? sha256(Files.readString(manifest, StandardCharsets.UTF_8))
                : FROZEN_MANIFEST_SHA256;
        try (BufferedReader in = Files.newBufferedReader(manifest, StandardCharsets.UTF_8);
             BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String header = in.readLine();
            if (header == null) {
                System.err.println("Empty manifest: " + manifest);
                System.exit(2);
            }
            ManifestLayout layout = ManifestLayout.fromHeader(parseCsvLine(header));
            String line;
            while ((line = in.readLine()) != null && ran < limit) {
                if (line.isBlank()) {
                    continue;
                }
                ManifestRow row = layout.read(parseCsvLine(line), manifest.toAbsolutePath().getParent());
                validateDatasetId(row);
                AttemptState previous = attempts.getOrDefault(row.pairId(), AttemptState.NONE);
                if (previous.succeeded()) {
                    skipped++;
                    continue;
                }
                int attempt = previous.maxAttempt() + 1;
                if (attempt > maxAttempts) {
                    exhausted++;
                    continue;
                }
                w.write(runOne(runner, formatter, row, attempt, manifestSha256, sha256(line)));
                w.newLine();
                w.flush(); // each row durable: resume-safe
                ran++;
                if (ran % 25 == 0) {
                    long elapsed = System.currentTimeMillis() - sweepStart;
                    System.err.printf("[batch] %d pairs done (%.1f s/pair avg)%n",
                            ran, elapsed / 1000.0 / ran);
                }
            }
        }
        System.err.printf("[batch] finished: %d run, %d skipped (successful), "
                + "%d skipped (attempt limit)%n", ran, skipped, exhausted);
    }

    private static String runOne(WalaNextPipelineRunner runner, NextJsonReportFormatter formatter,
                                 ManifestRow row, int attempt, String manifestSha256,
                                 String manifestRowSha256) {
        long start = System.currentTimeMillis();
        String leftSha = "";
        String rightSha = "";
        try {
            String left = Files.readString(row.leftPath(), StandardCharsets.UTF_8);
            String right = Files.readString(row.rightPath(), StandardCharsets.UTF_8);
            leftSha = sha256(left);
            rightSha = sha256(right);
            verifyExpectedHash(row.pairId(), "left", row.leftSha256(), leftSha);
            verifyExpectedHash(row.pairId(), "right", row.rightSha256(), rightSha);
            SourceAnalysisInput leftInput = new SourceAnalysisInput(left,
                    row.leftPath().getFileName().toString(), row.leftProjectRoot(),
                    row.leftClasspath(), !Boolean.getBoolean("codesim.disableStubs"));
            SourceAnalysisInput rightInput = new SourceAnalysisInput(right,
                    row.rightPath().getFileName().toString(), row.rightProjectRoot(),
                    row.rightClasspath(), !Boolean.getBoolean("codesim.disableStubs"));
            PipelineExecution execution = runner.runDetailed(leftInput, rightInput);
            long wall = System.currentTimeMillis() - start;
            return outputRow(row, "ok", wall, attempt, manifestSha256, manifestRowSha256,
                    leftSha, rightSha, execution,
                    compactJson(formatter.format(execution.result())), null);
        } catch (Exception | AssertionError ex) {
            long wall = System.currentTimeMillis() - start;
            return outputRow(row, "error", wall, attempt, manifestSha256, manifestRowSha256,
                    leftSha, rightSha, null, null, String.valueOf(ex));
        }
    }

    /** Minimal JSON assembly; the report payload is already JSON, everything else is escaped. */
    private static String outputRow(ManifestRow manifestRow, String status, long wallMs,
                                    int attempt, String manifestSha256,
                                    String manifestRowSha256, String leftSha, String rightSha,
                                    PipelineExecution execution, String reportJson, String error) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"schemaVersion\":\"").append(SCHEMA_VERSION).append('"');
        sb.append(",\"pairId\":\"").append(esc(manifestRow.pairId())).append('"');
        sb.append(",\"status\":\"").append(status).append('"');
        sb.append(",\"wallMs\":").append(wallMs);
        sb.append(",\"attempt\":").append(attempt);
        sb.append(",\"configId\":\"").append(esc(CONFIG_ID)).append('"');
        sb.append(",\"datasetId\":\"").append(esc(datasetId(manifestRow))).append('"');
        sb.append(",\"codeCommit\":\"").append(esc(CODE_COMMIT)).append('"');
        sb.append(",\"dirtyWorktree\":\"").append(esc(DIRTY_WORKTREE)).append('"');
        sb.append(",\"manifestSha256\":\"").append(manifestSha256).append('"');
        sb.append(",\"runtimeClasspathSha256\":\"")
                .append(esc(RUNTIME_CLASSPATH_SHA256)).append('"');
        sb.append(",\"manifestRowSha256\":\"").append(manifestRowSha256).append('"');
        sb.append(",\"leftSha256\":\"").append(leftSha).append('"');
        sb.append(",\"rightSha256\":\"").append(rightSha).append('"');
        sb.append(",\"analysisMode\":\"")
                .append(execution == null ? "ERROR" : execution.analysisMode().name()).append('"');
        sb.append(",\"stages\":{");
        boolean first = true;
        if (execution != null) {
            for (Map.Entry<String, PipelineExecution.StageOutcome> e : execution.stages().entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                PipelineExecution.StageOutcome outcome = e.getValue();
                sb.append('"').append(esc(e.getKey())).append("\":{");
                sb.append("\"status\":\"").append(outcome.status().name()).append('"');
                sb.append(",\"durationMs\":").append(outcome.durationMs());
                sb.append(",\"detail\":\"").append(esc(outcome.detail())).append("\"}");
                first = false;
            }
        }
        sb.append('}');
        if (execution != null) {
            sb.append(",\"fallbackStage\":\"").append(esc(execution.fallbackStage())).append('"');
            sb.append(",\"fallbackReason\":\"").append(esc(execution.fallbackReason())).append('"');
            sb.append(",\"compilations\":{");
            boolean firstCompilation = true;
            for (Map.Entry<String, PipelineExecution.CompilationProvenance> entry
                    : execution.compilations().entrySet()) {
                if (!firstCompilation) {
                    sb.append(',');
                }
                PipelineExecution.CompilationProvenance provenance = entry.getValue();
                sb.append('"').append(esc(entry.getKey())).append("\":{");
                sb.append("\"mode\":\"").append(esc(provenance.mode())).append('"');
                sb.append(",\"cacheKey\":\"").append(esc(provenance.cacheKey())).append('"');
                sb.append(",\"cacheHit\":").append(provenance.cacheHit());
                sb.append(",\"generatedStubCount\":").append(provenance.generatedStubCount());
                sb.append(",\"javaRelease\":").append(provenance.javaRelease());
                sb.append(",\"supportClasspathEntries\":")
                        .append(provenance.supportClasspathEntries());
                sb.append(",\"diagnosticSummary\":\"")
                        .append(esc(provenance.diagnosticSummary())).append("\"}");
                firstCompilation = false;
            }
            sb.append('}');
        }
        if (reportJson != null) {
            sb.append(",\"report\":").append(reportJson);
        }
        if (error != null) {
            sb.append(",\"error\":\"").append(esc(error)).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static void verifyExpectedHash(String pairId, String side,
                                           String expected, String actual) {
        if (!expected.isBlank() && !expected.equalsIgnoreCase(actual)) {
            throw new IllegalStateException(pairId + ": " + side + " SHA-256 mismatch; expected "
                    + expected + " but read " + actual);
        }
    }

    private static String datasetId(ManifestRow row) {
        if (!DATASET_ID.equals("unknown")) {
            return DATASET_ID;
        }
        return row.datasetId().isBlank() ? "unknown" : row.datasetId();
    }

    private static void validateDatasetId(ManifestRow row) {
        if (!DATASET_ID.equals("unknown") && !row.datasetId().isBlank()
                && !DATASET_ID.equals(row.datasetId())) {
            throw new IllegalStateException("dataset ID mismatch: runner=" + DATASET_ID
                    + ", manifest=" + row.datasetId());
        }
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String compactJson(String json) {
        StringBuilder compact = new StringBuilder(json.length());
        boolean quoted = false;
        boolean escaped = false;
        for (char current : json.toCharArray()) {
            if (quoted) {
                compact.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    quoted = false;
                }
            } else if (current == '"') {
                compact.append(current);
                quoted = true;
            } else if (!Character.isWhitespace(current)) {
                compact.append(current);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("report formatter produced unterminated JSON string");
        }
        return compact.toString();
    }

    private static Map<String, AttemptState> previousAttempts(Path out) throws Exception {
        Map<String, AttemptState> attempts = new HashMap<>();
        if (!Files.exists(out)) {
            return attempts;
        }
        Map<String, Boolean> seenAttempts = new HashMap<>();
        try (BufferedReader r = Files.newBufferedReader(out, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = r.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                Matcher pairMatcher = OUTPUT_PAIR_ID.matcher(line);
                Matcher statusMatcher = OUTPUT_STATUS.matcher(line);
                Matcher attemptMatcher = OUTPUT_ATTEMPT.matcher(line);
                if (!pairMatcher.find() || !statusMatcher.find() || !attemptMatcher.find()) {
                    throw new IllegalStateException("malformed existing JSONL at " + out
                            + ":" + lineNumber);
                }
                String pairId = pairMatcher.group(1);
                int attempt = Integer.parseInt(attemptMatcher.group(1));
                String attemptKey = pairId + "\n" + attempt;
                if (seenAttempts.put(attemptKey, Boolean.TRUE) != null) {
                    throw new IllegalStateException("duplicate attempt " + attempt
                            + " for " + pairId + " in " + out);
                }
                AttemptState old = attempts.getOrDefault(pairId, AttemptState.NONE);
                attempts.put(pairId, new AttemptState(Math.max(old.maxAttempt(), attempt),
                        old.succeeded() || "ok".equals(statusMatcher.group(1))));
            }
        }
        return attempts;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        value.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    value.append(current);
                }
            } else if (current == ',' ) {
                values.add(value.toString());
                value.setLength(0);
            } else if (current == '"' && value.length() == 0) {
                quoted = true;
            } else {
                value.append(current);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("unterminated quoted CSV field");
        }
        values.add(value.toString());
        return values;
    }

    private record ManifestRow(String pairId, Path leftPath, Path rightPath,
                               String datasetId, String leftSha256, String rightSha256,
                               Path leftProjectRoot, Path rightProjectRoot,
                               List<Path> leftClasspath, List<Path> rightClasspath) {
    }

    private record ManifestLayout(Map<String, Integer> indexes) {
        static ManifestLayout fromHeader(List<String> fields) {
            Map<String, Integer> indexes = new HashMap<>();
            for (int index = 0; index < fields.size(); index++) {
                String field = fields.get(index).strip();
                if (index == 0 && field.startsWith("\uFEFF")) {
                    field = field.substring(1);
                }
                if (indexes.put(field, index) != null) {
                    throw new IllegalArgumentException("duplicate manifest column: " + field);
                }
            }
            for (String required : List.of("pair_id", "left_path", "right_path")) {
                if (!indexes.containsKey(required)) {
                    throw new IllegalArgumentException("manifest missing column: " + required);
                }
            }
            return new ManifestLayout(Map.copyOf(indexes));
        }

        ManifestRow read(List<String> values, Path manifestDirectory) {
            String pairId = required(values, "pair_id").strip();
            if (!SAFE_PAIR_ID.matcher(pairId).matches()) {
                throw new IllegalArgumentException("unsafe or empty pair_id: " + pairId);
            }
            Path left = resolvePath(required(values, "left_path"), manifestDirectory);
            Path right = resolvePath(required(values, "right_path"), manifestDirectory);
            return new ManifestRow(pairId, left, right, optional(values, "dataset_id"),
                    optional(values, "left_sha256"), optional(values, "right_sha256"),
                    optionalPath(values, "left_project_root", manifestDirectory),
                    optionalPath(values, "right_project_root", manifestDirectory),
                    optionalClasspath(values, "left_classpath", manifestDirectory),
                    optionalClasspath(values, "right_classpath", manifestDirectory));
        }

        private String required(List<String> values, String name) {
            String value = optional(values, name);
            if (value.isBlank()) {
                throw new IllegalArgumentException("manifest row has empty " + name);
            }
            return value;
        }

        private String optional(List<String> values, String name) {
            Integer index = indexes.get(name);
            return index == null || index >= values.size() ? "" : values.get(index).strip();
        }

        private Path optionalPath(List<String> values, String name, Path manifestDirectory) {
            String value = optional(values, name);
            return value.isBlank() ? null : resolvePath(value, manifestDirectory);
        }

        private List<Path> optionalClasspath(List<String> values, String name,
                                             Path manifestDirectory) {
            String value = optional(values, name);
            if (value.isBlank()) {
                return List.of();
            }
            return java.util.Arrays.stream(value.split(java.util.regex.Pattern.quote(
                            java.io.File.pathSeparator)))
                    .filter(part -> !part.isBlank())
                    .map(part -> resolvePath(part, manifestDirectory))
                    .toList();
        }

        private static Path resolvePath(String value, Path manifestDirectory) {
            Path path = Path.of(value);
            return path.isAbsolute() ? path.normalize()
                    : manifestDirectory.resolve(path).normalize().toAbsolutePath();
        }
    }

    private record AttemptState(int maxAttempt, boolean succeeded) {
        private static final AttemptState NONE = new AttemptState(0, false);
    }
}
