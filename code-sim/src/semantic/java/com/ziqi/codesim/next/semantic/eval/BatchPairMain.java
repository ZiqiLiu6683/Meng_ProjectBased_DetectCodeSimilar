package com.ziqi.codesim.next.semantic.eval;

import com.ziqi.codesim.next.NextJsonReportFormatter;
import com.ziqi.codesim.next.semantic.PipelineExecution;
import com.ziqi.codesim.next.semantic.WalaNextPipelineRunner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
 * Manifest format (CSV, header required): {@code pair_id,left_path,right_path}. Paths must not
 * contain commas (the extraction scripts guarantee this).
 *
 * <p>Usage: {@code BatchPairMain <manifest.csv> <out.jsonl> [--limit N]}
 */
public final class BatchPairMain {
    private static final String SCHEMA_VERSION = "2.0-dev";
    private static final String CONFIG_ID = System.getProperty("codesim.configId", "v3-development-default");
    private static final String DATASET_ID = System.getProperty("codesim.datasetId", "unknown");
    private static final String CODE_COMMIT = System.getProperty("codesim.codeCommit", "unknown");
    private static final String DIRTY_WORKTREE = System.getProperty("codesim.dirtyWorktree", "unknown");

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: BatchPairMain <manifest.csv> <out.jsonl> [--limit N]");
            System.exit(2);
        }
        Path manifest = Path.of(args[0]);
        Path out = Path.of(args[1]);
        int limit = Integer.MAX_VALUE;
        for (int i = 2; i < args.length; i++) {
            if ("--limit".equals(args[i]) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            }
        }

        Set<String> done = alreadyDone(out);
        NextJsonReportFormatter formatter = new NextJsonReportFormatter();
        WalaNextPipelineRunner runner = new WalaNextPipelineRunner();

        int ran = 0;
        int skipped = 0;
        long sweepStart = System.currentTimeMillis();
        try (BufferedReader in = Files.newBufferedReader(manifest, StandardCharsets.UTF_8);
             BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String header = in.readLine(); // skip header
            if (header == null) {
                System.err.println("Empty manifest: " + manifest);
                System.exit(2);
            }
            String line;
            while ((line = in.readLine()) != null && ran < limit) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", 3);
                if (cols.length < 3) {
                    System.err.println("[batch] bad manifest row skipped: " + line);
                    continue;
                }
                String pairId = cols[0].trim();
                if (done.contains(pairId)) {
                    skipped++;
                    continue;
                }
                w.write(runOne(runner, formatter, pairId, cols[1].trim(), cols[2].trim()));
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
        System.err.printf("[batch] finished: %d run, %d skipped (already done)%n", ran, skipped);
    }

    private static String runOne(WalaNextPipelineRunner runner, NextJsonReportFormatter formatter,
                                 String pairId, String leftPath, String rightPath) {
        long start = System.currentTimeMillis();
        String leftSha = "";
        String rightSha = "";
        try {
            String left = Files.readString(Path.of(leftPath), StandardCharsets.UTF_8);
            String right = Files.readString(Path.of(rightPath), StandardCharsets.UTF_8);
            leftSha = sha256(left);
            rightSha = sha256(right);
            PipelineExecution execution = runner.runDetailed(left, right);
            long wall = System.currentTimeMillis() - start;
            return row(pairId, "ok", wall, leftSha, rightSha, execution,
                    formatter.format(execution.result()), null);
        } catch (Exception | AssertionError ex) {
            long wall = System.currentTimeMillis() - start;
            return row(pairId, "error", wall, leftSha, rightSha, null, null, String.valueOf(ex));
        }
    }

    /** Minimal JSON assembly; the report payload is already JSON, everything else is escaped. */
    private static String row(String pairId, String status, long wallMs,
                              String leftSha, String rightSha,
                              PipelineExecution execution, String reportJson, String error) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"schemaVersion\":\"").append(SCHEMA_VERSION).append('"');
        sb.append(",\"pairId\":\"").append(esc(pairId)).append('"');
        sb.append(",\"status\":\"").append(status).append('"');
        sb.append(",\"wallMs\":").append(wallMs);
        sb.append(",\"attempt\":1");
        sb.append(",\"configId\":\"").append(esc(CONFIG_ID)).append('"');
        sb.append(",\"datasetId\":\"").append(esc(DATASET_ID)).append('"');
        sb.append(",\"codeCommit\":\"").append(esc(CODE_COMMIT)).append('"');
        sb.append(",\"dirtyWorktree\":\"").append(esc(DIRTY_WORKTREE)).append('"');
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

    private static Set<String> alreadyDone(Path out) {
        Set<String> done = new HashSet<>();
        if (!Files.exists(out)) {
            return done;
        }
        try (BufferedReader r = Files.newBufferedReader(out, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                int i = line.indexOf("\"pairId\":\"");
                if (i >= 0) {
                    int s = i + "\"pairId\":\"".length();
                    int e = line.indexOf('"', s);
                    if (e > s) {
                        done.add(line.substring(s, e));
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("[batch] could not read existing output, running all: " + ex);
        }
        return done;
    }
}
