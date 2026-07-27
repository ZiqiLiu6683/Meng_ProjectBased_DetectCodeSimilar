package com.ziqi.codesim.next.semantic.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ziqi.codesim.next.CandidateSource;
import com.ziqi.codesim.next.CloneRegionType;
import com.ziqi.codesim.next.CodeRegion;
import com.ziqi.codesim.next.LineSegment;
import com.ziqi.codesim.next.NextPipelineResult;
import com.ziqi.codesim.next.RegionCandidate;
import com.ziqi.codesim.next.RegionDecision;
import com.ziqi.codesim.next.RegionKind;
import com.ziqi.codesim.next.semantic.PipelineExecution;
import com.ziqi.codesim.next.semantic.WalaNextPipelineRunner;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP server for the modern SPA frontend, backed by the region pipeline. {@code POST /api/analyze}
 * runs {@link WalaNextPipelineRunner} and returns REGION-level JSON (per {@code web-ui/src/types.ts}):
 * each verdict is a Phase A region (T1/T2/T3) or a method-level behavioural T4 -- there is no
 * file-level clone type. Everything else on {@code /} serves the built SPA from {@code web-ui/dist}.
 *
 * <p>Run under the semantic profile so the WALA classes are on the classpath, e.g.
 * {@code mvn -Psemantic-analysis -Dexec.mainClass=com.ziqi.codesim.next.semantic.web.RegionWebServer exec:java}.
 */
public final class RegionWebServer {

    private final WalaNextPipelineRunner runner = new WalaNextPipelineRunner();
    private final Path webRoot;

    public RegionWebServer(Path webRoot) {
        this.webRoot = webRoot;
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Path webRoot = Path.of(System.getProperty("codesim.webui",
                args.length > 1 ? args[1] : "web-ui/dist"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        RegionWebServer app = new RegionWebServer(webRoot);
        server.createContext("/api/analyze", app::handleAnalyze);
        server.createContext("/", app::handleStatic);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("CodeSim region web server: http://localhost:" + port + "/");
        System.out.println("Serving SPA from: " + webRoot.toAbsolutePath()
                + (Files.exists(webRoot) ? "" : "  (not built yet -- run `npm run dev` in web-ui and open :5173)"));
    }

    private void handleAnalyze(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json", "{\"error\":\"method not allowed\"}");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = parseForm(body);
        String leftName = form.getOrDefault("leftName", "Left.java");
        String rightName = form.getOrDefault("rightName", "Right.java");
        String leftSource = form.getOrDefault("leftSource", "");
        String rightSource = form.getOrDefault("rightSource", "");

        // Server-Sent Events: stream each real pipeline stage as it is reached, then the result. The
        // progress consumer runs on this same thread (synchronous), so writing frames here is safe.
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0); // 0 => chunked, keeps the stream open
        OutputStream out = exchange.getResponseBody();
        try {
            PipelineExecution execution = runner.runDetailed(leftSource, rightSource,
                    stage -> writeEvent(out, "stage", "{\"stage\":\"" + esc(stage) + "\"}"));
            writeEvent(out, "result", toJson(
                    leftName, leftSource, rightName, rightSource, execution));
        } catch (Exception ex) {
            writeEvent(out, "error", "{\"error\":\"" + esc(String.valueOf(ex.getMessage())) + "\"}");
        } finally {
            out.close();
            exchange.close();
        }
    }

    private static void writeEvent(OutputStream out, String event, String data) {
        try {
            out.write(("event: " + event + "\ndata: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // Client disconnected mid-stream; nothing to do.
        }
    }

    // --- JSON assembly (matches web-ui/src/types.ts AnalyzeResponse) ---

    private static String toJson(String leftName, String leftSource, String rightName, String rightSource,
                                 PipelineExecution execution) {
        NextPipelineResult result = execution.result();
        List<String> regions = new ArrayList<>();
        int index = 1;
        for (RegionDecision decision : result.regionDecisions()) {
            if (!isCountedVerdict(decision)) {
                continue;
            }
            regions.add(regionJson("r" + index++, decision));
        }
        boolean regionBackend = execution.analysisMode()
                != PipelineExecution.AnalysisMode.SOURCE_ONLY_FALLBACK;
        return "{"
                + "\"regionBackend\":" + regionBackend + ","
                + "\"analysisMode\":\"" + execution.analysisMode().name() + "\","
                + "\"fallbackStage\":\"" + esc(execution.fallbackStage()) + "\","
                + "\"fallbackReason\":\"" + esc(execution.fallbackReason()) + "\","
                + "\"compilations\":" + compilationsJson(execution) + ","
                + "\"left\":{\"name\":\"" + esc(leftName) + "\",\"source\":\"" + esc(leftSource) + "\"},"
                + "\"right\":{\"name\":\"" + esc(rightName) + "\",\"source\":\"" + esc(rightSource) + "\"},"
                + "\"regions\":[" + String.join(",", regions) + "]"
                + "}";
    }

    private static String regionJson(String id, RegionDecision decision) {
        RegionCandidate candidate = decision.candidate();
        CodeRegion left = candidate.left();
        CodeRegion right = candidate.right();
        CloneRegionType type = decision.type();
        boolean regionScope = left.kind() == RegionKind.CALL_EXPANDED_REGION;
        boolean crossMethod = candidate.sources().stream()
                .map(CandidateSource::channel)
                .anyMatch("CROSS_METHOD_REGION"::equals);
        String similarity = (type == CloneRegionType.T3 && !Double.isNaN(decision.syntacticSimilarity()))
                ? String.format(Locale.ROOT, "%.4f", decision.syntacticSimilarity())
                : "null";
        List<String> tags = decision.tags().stream().map(t -> "\"" + esc(t.name()) + "\"").toList();
        List<String> path = decision.decisionPath().stream().map(s -> "\"" + esc(s) + "\"").toList();
        return "{"
                + "\"id\":\"" + id + "\","
                + "\"family\":\"" + family(type) + "\","
                + "\"type\":\"" + type.name() + "\","
                + "\"scope\":\"" + (regionScope ? "region" : "method") + "\","
                + "\"crossMethod\":" + crossMethod + ","
                // begin/end stay as the bounding box for anything that wants one span; "segments"
                // is what the verdict actually covers, so the UI can avoid highlighting code that
                // sits between two runs of a cross-method region and was never compared.
                + "\"left\":{\"begin\":" + left.beginLine() + ",\"end\":" + left.endLine()
                + ",\"segments\":" + segmentsJson(left) + "},"
                + "\"right\":{\"begin\":" + right.beginLine() + ",\"end\":" + right.endLine()
                + ",\"segments\":" + segmentsJson(right) + "},"
                + "\"similarity\":" + similarity + ","
                + "\"tags\":[" + String.join(",", tags) + "],"
                + "\"path\":[" + String.join(",", path) + "]"
                + "}";
    }

    private static String segmentsJson(CodeRegion region) {
        StringBuilder out = new StringBuilder("[");
        List<LineSegment> segments = region.segments();
        for (int i = 0; i < segments.size(); i++) {
            LineSegment segment = segments.get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"begin\":").append(segment.begin())
                    .append(",\"end\":").append(segment.end()).append('}');
        }
        return out.append(']').toString();
    }

    /** Region-only counting: a Phase A region of a clone type, OR a method-level behavioural T4. */
    private static boolean isCountedVerdict(RegionDecision decision) {
        if (decision.type() == CloneRegionType.NON_CLONE) {
            return false;
        }
        boolean regionLevel = decision.candidate().left().kind() == RegionKind.CALL_EXPANDED_REGION;
        return regionLevel || family(decision.type()).equals("T4");
    }

    private static String family(CloneRegionType type) {
        return switch (type) {
            case T1 -> "T1";
            case T2 -> "T2";
            case T3 -> "T3";
            case T4_CONFIRMED, T4_DYNAMIC_EVIDENCE, POSSIBLE_T4_CANDIDATE -> "T4";
            case NON_CLONE -> "NON_CLONE";
        };
    }

    private static String compilationsJson(PipelineExecution execution) {
        List<String> entries = new ArrayList<>();
        execution.compilations().forEach((side, provenance) -> entries.add(
                "\"" + esc(side) + "\":{"
                        + "\"mode\":\"" + esc(provenance.mode()) + "\","
                        + "\"cacheHit\":" + provenance.cacheHit() + ","
                        + "\"generatedStubCount\":" + provenance.generatedStubCount() + ","
                        + "\"javaRelease\":" + provenance.javaRelease() + ","
                        + "\"diagnosticSummary\":\""
                        + esc(provenance.diagnosticSummary()) + "\"}"));
        return "{" + String.join(",", entries) + "}";
    }

    // --- Static file serving for the built SPA ---

    private void handleStatic(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getPath();
        String rel = rawPath.equals("/") ? "index.html" : rawPath.replaceFirst("^/+", "");
        Path file = webRoot.resolve(rel).normalize();
        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            // SPA fallback: unknown paths serve index.html when the build exists.
            Path index = webRoot.resolve("index.html");
            if (Files.isRegularFile(index)) {
                file = index;
            } else {
                send(exchange, 200, "text/html",
                        "<h2>CodeSim</h2><p>Frontend not built. In <code>web-ui</code> run "
                                + "<code>npm run dev</code> and open <a href=\"http://localhost:5173\">:5173</a> "
                                + "(it proxies /api here), or <code>npm run build</code> to serve it from here.</p>");
                return;
            }
        }
        byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType(file.toString()));
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String contentType(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    // --- helpers ---

    private static Map<String, String> parseForm(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        if (body == null || body.isBlank()) {
            return values;
        }
        for (String part : body.split("&", -1)) {
            int idx = part.indexOf('=');
            String key = idx >= 0 ? part.substring(0, idx) : part;
            String value = idx >= 0 ? part.substring(idx + 1) : "";
            values.put(dec(key), dec(value));
        }
        return values;
    }

    private static String dec(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
