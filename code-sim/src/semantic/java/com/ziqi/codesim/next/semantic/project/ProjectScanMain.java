package com.ziqi.codesim.next.semantic.project;

import com.ziqi.codesim.next.NextJsonReportFormatter;
import com.ziqi.codesim.next.semantic.PipelineExecution;
import com.ziqi.codesim.next.semantic.SourceAnalysisInput;
import com.ziqi.codesim.next.semantic.WalaNextPipelineRunner;
import com.ziqi.codesim.next.semantic.compilation.JavaCompilationCoordinator;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/** CLI: retrieve suspicious Java file pairs from two roots, then run the existing detector. */
public final class ProjectScanMain {
    private static final String SCHEMA_VERSION = "project-scan-1.0";

    private ProjectScanMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: ProjectScanMain <left-project-root> <right-project-root> "
                    + "<out.jsonl> [--method-top-k N] [--file-pair-limit N] "
                    + "[--enable-dynamic] [--no-stubs]");
            System.exit(2);
        }
        // Project trees may contain arbitrary code. Dynamic T4 execution is therefore opt-in;
        // the default project scan remains static WALA+SMT only.
        System.setProperty("codesim.skipDynamic", "true");
        Path leftRoot = Path.of(args[0]).toAbsolutePath().normalize();
        Path rightRoot = Path.of(args[1]).toAbsolutePath().normalize();
        Path output = Path.of(args[2]).toAbsolutePath().normalize();
        int methodTopK = 8;
        int filePairLimit = 100;
        boolean allowStubs = true;
        boolean dynamicEnabled = false;
        for (int i = 3; i < args.length; i++) {
            if ("--method-top-k".equals(args[i]) && i + 1 < args.length) {
                methodTopK = Integer.parseInt(args[++i]);
            } else if ("--file-pair-limit".equals(args[i]) && i + 1 < args.length) {
                filePairLimit = Integer.parseInt(args[++i]);
            } else if ("--enable-dynamic".equals(args[i])) {
                System.clearProperty("codesim.skipDynamic");
                dynamicEnabled = true;
            } else if ("--skip-dynamic".equals(args[i])) {
                // Retained as a compatible no-op for early project-scan scripts.
                System.setProperty("codesim.skipDynamic", "true");
                dynamicEnabled = false;
            } else if ("--no-stubs".equals(args[i])) {
                allowStubs = false;
            } else {
                throw new IllegalArgumentException("Unknown or incomplete option: " + args[i]);
            }
        }

        ProjectCandidateIndexer.ProjectScanPlan plan = new ProjectCandidateIndexer()
                .plan(leftRoot, rightRoot, methodTopK, filePairLimit);
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        WalaNextPipelineRunner runner = new WalaNextPipelineRunner();
        NextJsonReportFormatter formatter = new NextJsonReportFormatter();
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write(manifest(plan, methodTopK, filePairLimit, allowStubs, dynamicEnabled));
            writer.newLine();
            int index = 0;
            for (ProjectCandidateIndexer.ProjectFileCandidate candidate : plan.candidates()) {
                index++;
                long start = System.currentTimeMillis();
                try {
                    String leftSource = Files.readString(candidate.leftFile(), StandardCharsets.UTF_8);
                    String rightSource = Files.readString(candidate.rightFile(), StandardCharsets.UTF_8);
                    SourceAnalysisInput leftInput = new SourceAnalysisInput(leftSource,
                            candidate.leftFile().getFileName().toString(), leftRoot, List.of(), allowStubs);
                    SourceAnalysisInput rightInput = new SourceAnalysisInput(rightSource,
                            candidate.rightFile().getFileName().toString(), rightRoot, List.of(), allowStubs);
                    PipelineExecution execution = runner.runDetailed(leftInput, rightInput);
                    writer.write(result(index, candidate, System.currentTimeMillis() - start,
                            execution, compactJson(formatter.format(execution.result())), null));
                } catch (Exception ex) {
                    writer.write(result(index, candidate, System.currentTimeMillis() - start,
                            null, null, ex.toString()));
                }
                writer.newLine();
                writer.flush();
                System.err.printf("[project-scan] %d/%d file pairs complete%n",
                        index, plan.candidates().size());
            }
        }
    }

    private static String manifest(ProjectCandidateIndexer.ProjectScanPlan plan,
                                   int methodTopK, int filePairLimit,
                                   boolean allowStubs, boolean dynamicEnabled) {
        return "{\"schemaVersion\":\"" + SCHEMA_VERSION + "\",\"recordType\":\"manifest\""
                + ",\"leftRoot\":\"" + esc(plan.leftRoot().toString()) + "\""
                + ",\"rightRoot\":\"" + esc(plan.rightRoot().toString()) + "\""
                + ",\"leftJavaFiles\":" + plan.leftJavaFiles()
                + ",\"rightJavaFiles\":" + plan.rightJavaFiles()
                + ",\"leftMethods\":" + plan.leftMethods()
                + ",\"rightMethods\":" + plan.rightMethods()
                + ",\"methodTopK\":" + methodTopK
                + ",\"filePairLimit\":" + filePairLimit
                + ",\"candidateFilePairs\":" + plan.candidates().size()
                + ",\"javaRelease\":" + JavaCompilationCoordinator.JAVA_RELEASE
                + ",\"allowStubs\":" + allowStubs
                + ",\"dynamicEnabled\":" + dynamicEnabled
                + ",\"warningCount\":" + plan.warnings().size()
                + ",\"warnings\":" + quotedArray(plan.warnings()) + "}";
    }

    private static String result(int rank, ProjectCandidateIndexer.ProjectFileCandidate candidate,
                                 long wallMs, PipelineExecution execution,
                                 String report, String error) {
        StringBuilder out = new StringBuilder();
        out.append("{\"schemaVersion\":\"").append(SCHEMA_VERSION)
                .append("\",\"recordType\":\"filePair\"");
        out.append(",\"rank\":").append(rank);
        out.append(",\"leftFile\":\"").append(esc(candidate.leftFile().toString())).append('"');
        out.append(",\"rightFile\":\"").append(esc(candidate.rightFile().toString())).append('"');
        out.append(",\"retrievalScore\":").append(candidate.retrievalScore());
        out.append(",\"supportingMethodPairs\":").append(candidate.supportingMethodPairs());
        out.append(",\"exampleMethodPairs\":").append(quotedArray(candidate.exampleMethodPairs()));
        out.append(",\"wallMs\":").append(wallMs);
        out.append(",\"status\":\"").append(error == null ? "ok" : "error").append('"');
        if (execution != null) {
            out.append(",\"analysisMode\":\"").append(execution.analysisMode().name()).append('"');
            out.append(",\"fallbackStage\":\"").append(esc(execution.fallbackStage())).append('"');
            out.append(",\"fallbackReason\":\"").append(esc(execution.fallbackReason())).append('"');
            out.append(",\"stages\":{");
            boolean firstStage = true;
            for (Map.Entry<String, PipelineExecution.StageOutcome> entry
                    : execution.stages().entrySet()) {
                if (!firstStage) out.append(',');
                out.append('"').append(esc(entry.getKey())).append("\":{\"status\":\"")
                        .append(entry.getValue().status()).append("\",\"durationMs\":")
                        .append(entry.getValue().durationMs()).append(",\"detail\":\"")
                        .append(esc(entry.getValue().detail())).append("\"}");
                firstStage = false;
            }
            out.append('}');
            out.append(",\"compilations\":{");
            boolean first = true;
            for (Map.Entry<String, PipelineExecution.CompilationProvenance> entry
                    : execution.compilations().entrySet()) {
                if (!first) out.append(',');
                out.append('"').append(entry.getKey()).append("\":{\"mode\":\"")
                        .append(entry.getValue().mode()).append("\",\"cacheKey\":\"")
                        .append(esc(entry.getValue().cacheKey())).append("\",\"cacheHit\":")
                        .append(entry.getValue().cacheHit()).append(",\"generatedStubCount\":")
                        .append(entry.getValue().generatedStubCount()).append(",\"javaRelease\":")
                        .append(entry.getValue().javaRelease())
                        .append(",\"supportClasspathEntries\":")
                        .append(entry.getValue().supportClasspathEntries())
                        .append(",\"diagnosticSummary\":\"")
                        .append(esc(entry.getValue().diagnosticSummary())).append("\"}");
                first = false;
            }
            out.append('}');
            out.append(",\"report\":").append(report);
        }
        if (error != null) {
            out.append(",\"error\":\"").append(esc(error)).append('"');
        }
        return out.append('}').toString();
    }

    private static String compactJson(String json) {
        StringBuilder compact = new StringBuilder(json.length());
        boolean quoted = false;
        boolean escaped = false;
        for (char current : json.toCharArray()) {
            if (quoted) {
                compact.append(current);
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
            } else if (current == '"') {
                compact.append(current);
                quoted = true;
            } else if (!Character.isWhitespace(current)) {
                compact.append(current);
            }
        }
        return compact.toString();
    }

    private static String quotedArray(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) out.append(',');
            out.append('"').append(esc(values.get(index))).append('"');
        }
        return out.append(']').toString();
    }

    private static String esc(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
