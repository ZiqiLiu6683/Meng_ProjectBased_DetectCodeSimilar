package com.ziqi.codesim.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ziqi.codesim.pipeline.FullPipelineResult;
import com.ziqi.codesim.pipeline.JsonReportFormatter;
import com.ziqi.codesim.pipeline.PipelineRunner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class WebAppMain {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", WebAppMain::handleIndex);
        server.createContext("/api/analyze", WebAppMain::handleAnalyze);
        server.setExecutor(null);
        server.start();
        System.out.println("Code Similarity Web App: http://localhost:" + port + "/");
    }

    private static void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed");
            return;
        }
        send(exchange, 200, "text/html; charset=utf-8", html());
    }

    private static void handleAnalyze(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json; charset=utf-8", "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = parseForm(body);
            String sourceA = form.getOrDefault("sourceA", "");
            String sourceB = form.getOrDefault("sourceB", "");
            String fileA = form.getOrDefault("fileA", "A.java");
            String fileB = form.getOrDefault("fileB", "B.java");

            FullPipelineResult result = new PipelineRunner().runFull(sourceA, sourceB);
            String json = new JsonReportFormatter().format(result, fileA, fileB);
            send(exchange, 200, "application/json; charset=utf-8", json);
        } catch (Exception ex) {
            send(exchange, 500, "application/json; charset=utf-8",
                    "{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        if (body.isBlank()) return values;
        for (String part : body.split("&", -1)) {
            int idx = part.indexOf('=');
            String key = idx >= 0 ? part.substring(0, idx) : part;
            String value = idx >= 0 ? part.substring(idx + 1) : "";
            values.put(urlDecode(key), urlDecode(value));
        }
        return values;
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String html() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Code Similarity</title>
                  <style>
                    :root {
                      color-scheme: light;
                      --bg: #f6f7f9;
                      --panel: #ffffff;
                      --line: #d8dde5;
                      --text: #1f2933;
                      --muted: #5d6978;
                      --accent: #0f766e;
                      --accent-dark: #0b5f59;
                      --warn: #9f580a;
                      --bad: #b42318;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      background: var(--bg);
                      color: var(--text);
                      font-family: Arial, Helvetica, sans-serif;
                    }
                    header {
                      border-bottom: 1px solid var(--line);
                      background: var(--panel);
                    }
                    .bar {
                      max-width: 1320px;
                      margin: 0 auto;
                      padding: 16px 20px;
                      display: flex;
                      align-items: center;
                      justify-content: space-between;
                      gap: 16px;
                    }
                    h1 {
                      margin: 0;
                      font-size: 20px;
                      font-weight: 700;
                    }
                    main {
                      max-width: 1320px;
                      margin: 0 auto;
                      padding: 20px;
                    }
                    .workspace {
                      display: grid;
                      grid-template-columns: minmax(0, 1fr) minmax(460px, 560px);
                      gap: 18px;
                      align-items: start;
                    }
                    .editors {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 14px;
                    }
                    .panel {
                      background: var(--panel);
                      border: 1px solid var(--line);
                      border-radius: 8px;
                    }
                    .panel-head {
                      padding: 12px;
                      border-bottom: 1px solid var(--line);
                      display: flex;
                      gap: 10px;
                      align-items: center;
                    }
                    input, textarea, button {
                      font: inherit;
                    }
                    input {
                      width: 100%;
                      border: 1px solid var(--line);
                      border-radius: 6px;
                      padding: 8px 10px;
                    }
                    textarea {
                      width: 100%;
                      min-height: 520px;
                      border: 0;
                      resize: vertical;
                      padding: 12px;
                      line-height: 1.45;
                      font-family: Consolas, "Courier New", monospace;
                      font-size: 13px;
                      color: #111827;
                    }
                    button {
                      border: 1px solid var(--accent);
                      background: var(--accent);
                      color: white;
                      border-radius: 6px;
                      padding: 9px 13px;
                      cursor: pointer;
                      white-space: nowrap;
                    }
                    button.secondary {
                      background: white;
                      color: var(--accent);
                    }
                    button:hover { background: var(--accent-dark); }
                    button.secondary:hover {
                      background: #eef8f6;
                      color: var(--accent-dark);
                    }
                    .actions {
                      display: flex;
                      gap: 10px;
                    }
                    .summary {
                      padding: 16px;
                      display: grid;
                      gap: 14px;
                    }
                    #resultPanel { overflow: hidden; }
                    .status-banner {
                      border: 1px solid var(--line);
                      border-left: 8px solid var(--accent);
                      border-radius: 8px;
                      padding: 14px;
                      background: #f7fbfa;
                    }
                    .status-banner.warn {
                      border-left-color: var(--warn);
                      background: #fffaf2;
                    }
                    .status-banner.bad {
                      border-left-color: var(--bad);
                      background: #fff7f6;
                    }
                    .status-kicker {
                      color: var(--muted);
                      font-size: 12px;
                      font-weight: 700;
                      text-transform: uppercase;
                      letter-spacing: .05em;
                    }
                    .status-title {
                      margin-top: 4px;
                      font-size: 26px;
                      line-height: 1.1;
                      font-weight: 800;
                    }
                    .status-subtitle {
                      margin-top: 8px;
                      color: var(--muted);
                      line-height: 1.4;
                    }
                    .metric-strip {
                      display: grid;
                      grid-template-columns: repeat(3, minmax(0, 1fr));
                      gap: 10px;
                    }
                    .big-cell {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 12px;
                      background: #fbfcfd;
                    }
                    .big-value {
                      margin-top: 4px;
                      font-size: 24px;
                      line-height: 1;
                      font-weight: 800;
                    }
                    .big-note {
                      margin-top: 6px;
                      color: var(--muted);
                      font-size: 12px;
                      line-height: 1.35;
                    }
                    .decision {
                      border-bottom: 1px solid var(--line);
                      padding-bottom: 14px;
                    }
                    .decision-title {
                      font-size: 14px;
                      color: var(--muted);
                      margin-bottom: 4px;
                    }
                    .decision-text {
                      font-size: 30px;
                      line-height: 1.15;
                      font-weight: 800;
                    }
                    .decision-note {
                      margin-top: 8px;
                      color: var(--muted);
                      line-height: 1.45;
                    }
                    .badges {
                      display: flex;
                      flex-wrap: wrap;
                      gap: 8px;
                    }
                    .badge {
                      border: 1px solid var(--line);
                      border-radius: 999px;
                      padding: 6px 9px;
                      background: #fbfcfd;
                      font-size: 12px;
                      color: var(--muted);
                    }
                    .score {
                      display: grid;
                      gap: 6px;
                    }
                    .score-row {
                      display: flex;
                      align-items: center;
                      justify-content: space-between;
                      gap: 12px;
                      font-size: 13px;
                    }
                    .track {
                      height: 8px;
                      border-radius: 999px;
                      background: #e7ebf0;
                      overflow: hidden;
                    }
                    .fill {
                      height: 100%;
                      border-radius: 999px;
                      background: var(--accent);
                    }
                    .fill.warn { background: var(--warn); }
                    .fill.bad { background: var(--bad); }
                    .section-title {
                      font-size: 13px;
                      font-weight: 700;
                      margin: 4px 0 8px;
                    }
                    .section-head {
                      display: flex;
                      justify-content: space-between;
                      align-items: baseline;
                      gap: 12px;
                      margin: 4px 0 8px;
                    }
                    .section-note {
                      color: var(--muted);
                      font-size: 12px;
                    }
                    .metric {
                      border-bottom: 1px solid var(--line);
                      padding-bottom: 10px;
                    }
                    .label {
                      color: var(--muted);
                      font-size: 12px;
                      text-transform: uppercase;
                      letter-spacing: .04em;
                    }
                    .value {
                      margin-top: 4px;
                      font-size: 28px;
                      font-weight: 700;
                    }
                    .grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 10px;
                    }
                    .cell {
                      border: 1px solid var(--line);
                      border-radius: 6px;
                      padding: 10px;
                      background: #fbfcfd;
                    }
                    .pairs {
                      display: grid;
                      gap: 8px;
                    }
                    .pair {
                      border: 1px solid var(--line);
                      border-radius: 6px;
                      padding: 10px;
                    }
                    .method-row {
                      display: grid;
                      grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
                      gap: 10px;
                      align-items: center;
                    }
                    .method-side {
                      min-width: 0;
                      overflow-wrap: anywhere;
                      font-weight: 700;
                    }
                    .method-arrow {
                      color: var(--accent);
                      font-weight: 800;
                    }
                    .pair strong {
                      display: block;
                      overflow-wrap: anywhere;
                    }
                    .pair-meta {
                      margin-top: 6px;
                      color: var(--muted);
                      line-height: 1.45;
                      font-size: 13px;
                    }
                    .insight-list {
                      margin: 0;
                      padding-left: 18px;
                      line-height: 1.55;
                    }
                    .evidence-summary {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 10px;
                    }
                    .evidence-card {
                      border: 1px solid var(--line);
                      border-left: 5px solid var(--accent);
                      border-radius: 8px;
                      padding: 10px;
                      background: #fbfcfd;
                    }
                    .evidence-card.warn { border-left-color: var(--warn); }
                    .evidence-card.bad { border-left-color: var(--bad); }
                    .evidence-card.scope { border-left-color: #2563eb; }
                    .evidence-card.muted { border-left-color: #94a3b8; }
                    .evidence-count {
                      font-size: 20px;
                      font-weight: 800;
                      margin-top: 4px;
                    }
                    .evidence-text {
                      color: var(--muted);
                      font-size: 12px;
                      line-height: 1.35;
                      margin-top: 4px;
                    }
                    details {
                      border-top: 1px solid var(--line);
                      padding: 12px 16px;
                    }
                    summary {
                      cursor: pointer;
                      font-weight: 700;
                    }
                    pre {
                      margin: 10px 0 0;
                      overflow: auto;
                      max-height: 360px;
                      font-size: 12px;
                      line-height: 1.45;
                    }
                    .empty, .error {
                      color: var(--muted);
                      line-height: 1.5;
                    }
                    .error { color: var(--bad); }
                    @media (max-width: 900px) {
                      .workspace, .editors { grid-template-columns: 1fr; }
                      .metric-strip, .evidence-summary { grid-template-columns: 1fr; }
                      textarea { min-height: 320px; }
                      .bar { align-items: flex-start; flex-direction: column; }
                    }
                  </style>
                </head>
                <body>
                  <header>
                    <div class="bar">
                      <h1>Code Similarity Analyzer</h1>
                      <div class="actions">
                        <button class="secondary" id="sampleBtn" type="button">Load Sample</button>
                        <button id="analyzeBtn" type="button">Analyze</button>
                      </div>
                    </div>
                  </header>
                  <main>
                    <div class="workspace">
                      <section class="editors">
                        <div class="panel">
                          <div class="panel-head"><input id="fileA" value="A.java" aria-label="File A name"></div>
                          <textarea id="sourceA" spellcheck="false" aria-label="Source A"></textarea>
                        </div>
                        <div class="panel">
                          <div class="panel-head"><input id="fileB" value="B.java" aria-label="File B name"></div>
                          <textarea id="sourceB" spellcheck="false" aria-label="Source B"></textarea>
                        </div>
                      </section>
                      <aside class="panel" id="resultPanel">
                        <div class="summary">
                          <div class="empty">Paste two Java files and run analysis.</div>
                        </div>
                      </aside>
                    </div>
                  </main>
                  <script>
                    const sourceA = document.querySelector('#sourceA');
                    const sourceB = document.querySelector('#sourceB');
                    const fileA = document.querySelector('#fileA');
                    const fileB = document.querySelector('#fileB');
                    const resultPanel = document.querySelector('#resultPanel');
                    const sampleA = `import java.util.List;
                class B1 {
                    List<Integer> filterPositive(int[] nums) {
                        List<Integer> out = new java.util.ArrayList<>();
                        for (int n : nums) {
                            if (n > 0) out.add(n);
                        }
                        return out;
                    }
                }`;
                    const sampleB = `import java.util.List;
                class B2 {
                    List<Integer> getPositiveValues(int[] values) {
                        List<Integer> result = new java.util.ArrayList<>();
                        for (int value : values) {
                            if (value > 0) result.add(value);
                        }
                        return result;
                    }
                }`;
                    document.querySelector('#sampleBtn').addEventListener('click', () => {
                      sourceA.value = sampleA;
                      sourceB.value = sampleB;
                      fileA.value = 'B1.java';
                      fileB.value = 'B2.java';
                    });
                    document.querySelector('#analyzeBtn').addEventListener('click', analyze);
                    async function analyze() {
                      resultPanel.innerHTML = '<div class="summary"><div class="empty">Analyzing...</div></div>';
                      const body = new URLSearchParams({
                        fileA: fileA.value || 'A.java',
                        fileB: fileB.value || 'B.java',
                        sourceA: sourceA.value,
                        sourceB: sourceB.value
                      });
                      try {
                        const response = await fetch('/api/analyze', {
                          method: 'POST',
                          headers: {'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'},
                          body
                        });
                        const data = await response.json();
                        if (!response.ok) throw new Error(data.error || 'Analysis failed');
                        render(data);
                      } catch (err) {
                        resultPanel.innerHTML = `<div class="summary"><div class="error">${escapeHtml(err.message)}</div></div>`;
                      }
                    }
                    function render(data) {
                      const summary = data.summary;
                      const stage3 = data.stage3;
                      const stage4 = data.stage4;
                      const clone = cloneText(summary.cloneType);
                      const scope = scopeText(summary.scopeType);
                      const partial = partialText(summary.scopeType, stage3.partialCloneSignal);
                      const decision = decisionText(summary.cloneType, summary.scopeType, summary.confidenceLevel);
                      const fileCoverage = (Number(stage3.coverageA) + Number(stage3.coverageB)) / 2;
                      const detailedSignals = [
                        ['Confidence', summary.confidence],
                        ['Method Correspondence', stage3.matchScoreAvg],
                        ['Code Text Similarity', stage3.centroidS3],
                        ['Code Structure Similarity', stage3.centroidS4],
                        ['File A Matched', stage3.coverageA],
                        ['File B Matched', stage3.coverageB],
                        ['Partial Match Signal', stage3.partialCloneSignal]
                      ].map(([label, value]) => scoreBar(label, value)).join('');
                      const pairs = stage3.mergedPairs.slice(0, 6).map((pair, index) => `
                        <div class="pair">
                          <div class="label">Matched Method ${index + 1} - ${directionText(pair.direction)}</div>
                          <div class="method-row">
                            <div class="method-side">${escapeHtml(shortMethod(pair.methodAId))}</div>
                            <div class="method-arrow">-></div>
                            <div class="method-side">${escapeHtml(shortMethod(pair.methodBId))}</div>
                          </div>
                          <div class="pair-meta">
                            Match ${percent(pair.matchScore)} - Text ${percent(pair.feature.s3)} - Structure ${percent(pair.feature.s4)}
                            <br>${pairReason(pair)}
                          </div>
                        </div>`).join('');
                      const evidenceSummary = evidenceSummaryCards(stage4.evidenceChain);
                      const evidence = evidenceList(stage4.evidenceChain);
                      resultPanel.innerHTML = `
                        <div class="summary">
                          <div class="status-banner ${statusClass(summary.cloneType, summary.scopeType, summary.confidenceLevel)}">
                            <div class="status-kicker">Analysis Result</div>
                            <div class="status-title">${decision}</div>
                            <div class="status-subtitle">${clone.short} - ${scope.short}</div>
                          </div>
                          <div class="badges">
                            <span class="badge">Type: ${summary.cloneType} - ${clone.short}</span>
                            <span class="badge">Scope: ${summary.scopeType} - ${scope.short}</span>
                            <span class="badge">Partial: ${partial}</span>
                            <span class="badge">Confidence: ${summary.confidenceLevel} - ${percent(summary.confidence)}</span>
                          </div>
                          <div class="metric-strip">
                            ${metricCard('Confidence', summary.confidence, summary.confidenceLevel)}
                            ${metricCard('Method Correspondence', stage3.matchScoreAvg, `${summary.methodPairCount} selected match${summary.methodPairCount === 1 ? '' : 'es'}`)}
                            ${metricCard('File Coverage', fileCoverage, 'Average of both files')}
                          </div>
                          <div>
                            <div class="section-head">
                              <div class="section-title">Scope Snapshot</div>
                              <div class="section-note">How much code was matched</div>
                            </div>
                            ${scoreBar('File A Matched', stage3.coverageA)}
                            ${scoreBar('File B Matched', stage3.coverageB)}
                            ${scoreBar('Partial Match Signal', stage3.partialCloneSignal)}
                          </div>
                          <div>
                            <div class="section-head">
                              <div class="section-title">Matched Methods</div>
                              <div class="section-note">${summary.methodPairCount} selected</div>
                            </div>
                            <div class="pairs">${pairs || '<div class="empty">No matched method pairs.</div>'}</div>
                          </div>
                          <div>
                            <div class="section-title">Evidence Summary</div>
                            ${evidenceSummary}
                          </div>
                        </div>
                        <details>
                          <summary>Detailed Signals</summary>
                          <div class="summary">${detailedSignals}</div>
                        </details>
                        <details>
                          <summary>Analyze Details</summary>
                          <div class="summary">
                            <div class="grid">
                              ${cell('Input Pattern', inputPatternText(data.stage0.primaryMode))}
                              ${cell('Compared Method Pairs', String(data.stage1.pairMatrix.length))}
                              ${cell('Computed Pair Features', String(data.stage2.pairFeatures.length))}
                              ${cell('Selected Method Matches', String(stage3.mergedPairs.length))}
                              ${cell('Evidence Strength', percent(stage4.evidenceStrength))}
                              ${cell('Pipeline Reliability', percent(stage4.pipelineReliability))}
                            </div>
                          </div>
                        </details>
                        <details>
                          <summary>Evidence</summary>
                          <div class="summary">${evidence}</div>
                        </details>
                        <details>
                          <summary>Developer JSON</summary>
                          <pre>${escapeHtml(JSON.stringify(data, null, 2))}</pre>
                        </details>`;
                    }
                    function decisionText(cloneType, scopeType, confidenceLevel) {
                      if (cloneType === 'NON_CLONE') return 'NO CLEAR SIMILARITY';
                      if (scopeType === 'PARTIAL') return 'PARTIAL SIMILARITY';
                      if (scopeType === 'MIXED') return 'MIXED-SCOPE SIMILARITY';
                      return 'BROADLY SIMILAR';
                    }
                    function statusClass(cloneType, scopeType, confidenceLevel) {
                      if (cloneType === 'NON_CLONE') return 'bad';
                      if (confidenceLevel !== 'HIGH' || scopeType === 'PARTIAL' || scopeType === 'MIXED') return 'warn';
                      return '';
                    }
                    function cloneText(type) {
                      const map = {
                        T1: ['Exact clone', 'The two files are almost identical.'],
                        T2: ['Renamed or reformatted clone', 'The structure is mostly the same; differences are likely names, formatting, or literals.'],
                        T3: ['Modified clone', 'The files still have clear correspondences, but statements, structure, or local logic changed.'],
                        T4_WEAK: ['Weak semantic clone', 'Direct structural evidence is weak, but API or weak semantic signals may still indicate a relationship.'],
                        NON_CLONE: ['No clear clone', 'The current evidence is not enough to mark the files as similar.']
                      };
                      const item = map[type] || [type, 'The system produced a similarity decision.'];
                      return { short: item[0], description: item[1] };
                    }
                    function scopeText(type) {
                      const map = {
                        FULL: ['Full-file similarity', 'Most important methods on both sides have corresponding matches.'],
                        PARTIAL: ['Partial similarity', 'Only part of the methods or fragments have corresponding matches.'],
                        MIXED: ['Mixed-scope similarity', 'The result contains both broad and partial similarity signals.'],
                        NONE: ['No clear scope', 'The matches do not form a stable similarity scope.']
                      };
                      const item = map[type] || [type, 'The scope decision comes from method coverage.'];
                      return { short: item[0], description: item[1] };
                    }
                    function inputPatternText(mode) {
                      const map = {
                        BCB_SINGLE_METHOD: 'Single method snippet',
                        SINGLE_METHOD_REAL_FILE: 'One main method in a file',
                        ONE_TO_MANY_METHOD: 'One method compared with multiple methods',
                        MULTI_METHOD_BALANCED: 'Multiple methods on both sides',
                        CLASS_CONTEXT_HEAVY: 'Class-level context is important',
                        PARSE_UNSTABLE: 'Parsing was unstable'
                      };
                      return map[mode] || mode;
                    }
                    function partialText(scopeType, signal) {
                      if (scopeType === 'PARTIAL') return `Yes - ${percent(signal)}`;
                      if (scopeType === 'MIXED') return `Possible - ${percent(signal)}`;
                      return `No - ${percent(signal)}`;
                    }
                    function metricCard(label, value, note) {
                      return `<div class="big-cell">
                        <div class="label">${escapeHtml(label)}</div>
                        <div class="big-value">${percent(value)}</div>
                        <div class="big-note">${escapeHtml(note)}</div>
                      </div>`;
                    }
                    function scoreBar(label, value) {
                      const n = Number(value) || 0;
                      const cls = n < 0.35 ? 'bad' : n < 0.65 ? 'warn' : '';
                      return `<div class="score">
                        <div class="score-row"><span>${escapeHtml(label)}</span><strong>${percent(n)}</strong></div>
                        <div class="track"><div class="fill ${cls}" style="width:${Math.max(0, Math.min(100, n * 100))}%"></div></div>
                      </div>`;
                    }
                    function directionText(direction) {
                      if (direction === 'BIDIRECTIONAL') return 'Bidirectional match';
                      if (direction === 'A_TO_B_ONLY') return 'A method found in B';
                      if (direction === 'B_TO_A_ONLY') return 'B method found in A';
                      return direction;
                    }
                    function pairReason(pair) {
                      if (pair.matchScore >= 0.8) return 'Assessment: likely the same function or a strong method correspondence.';
                      if (pair.matchScore >= 0.5) return 'Assessment: possible correspondence; manual review is recommended.';
                      return 'Assessment: weak correspondence; use as supporting context only.';
                    }
                    function shortMethod(methodId) {
                      const hash = methodId.indexOf('#');
                      if (hash < 0) return methodId;
                      const type = methodId.slice(0, hash);
                      const rest = methodId.slice(hash + 1).replace(/#\\d+$/, '');
                      return `${type}.${rest}`;
                    }
                    function evidenceSummaryCards(chain) {
                      return `<div class="evidence-summary">
                        ${evidenceCard('Supports Similarity', chain.supportingEvidence.length, strongest(chain.supportingEvidence), '')}
                        ${evidenceCard('Suggests Modification', chain.opposingEvidence.length, strongest(chain.opposingEvidence), 'warn')}
                        ${evidenceCard('Supports Scope', chain.scopeEvidence.length, strongest(chain.scopeEvidence), 'scope')}
                        ${evidenceCard('Reliability Warnings', chain.reliabilityWarnings.length, strongest(chain.reliabilityWarnings), chain.reliabilityWarnings.length ? 'bad' : 'muted')}
                      </div>`;
                    }
                    function evidenceCard(title, count, strength, cls) {
                      const note = count === 0 ? 'No signals in this group' : `${strength} signal${count === 1 ? '' : 's'}`;
                      return `<div class="evidence-card ${cls}">
                        <div class="label">${escapeHtml(title)}</div>
                        <div class="evidence-count">${count}</div>
                        <div class="evidence-text">${escapeHtml(note)}</div>
                      </div>`;
                    }
                    function strongest(items) {
                      if (items.some(item => item.strength === 'HIGH')) return 'High';
                      if (items.some(item => item.strength === 'MEDIUM')) return 'Medium';
                      if (items.some(item => item.strength === 'LOW')) return 'Low';
                      return 'No';
                    }
                    function evidenceList(chain) {
                      const groups = [
                        ['Supporting Evidence', chain.supportingEvidence],
                        ['Opposing or Weakening Evidence', chain.opposingEvidence],
                        ['Scope Evidence', chain.scopeEvidence],
                        ['Reliability Warnings', chain.reliabilityWarnings]
                      ];
                      return groups.map(([title, items]) => `
                        <div>
                          <div class="section-title">${title}</div>
                          ${items.length ? `<ul class="insight-list">${items.map(evidenceItem).join('')}</ul>` : '<div class="empty">(none)</div>'}
                        </div>`).join('');
                    }
                    function evidenceItem(item) {
                      return `<li><strong>${escapeHtml(signalDisplay(item.signal))}</strong> = ${escapeHtml(item.value)}
                        <br>${escapeHtml(interpretationDisplay(item))} - Supports: ${escapeHtml(supportsDisplay(item.supports))}</li>`;
                    }
                    function signalDisplay(signal) {
                      const map = {
                        structural_exactness_avg: 'Exact Structure Overlap',
                        method_similarity_strength: 'Method Similarity Strength',
                        modification_strength: 'Modification Signal',
                        confirmed_ratio: 'Confirmed Match Ratio',
                        'coverage_A/B': 'Two-Way File Match Coverage',
                        S5_NOT_APPLICABLE: 'API Signal Not Available',
                        S1_if_reliable: 'File Context Similarity',
                        partial_clone_signal: 'Partial Match Signal',
                        match_score_avg: 'Method Correspondence',
                        magnitude_avg: 'Overall Match Strength',
                        scope_confidence: 'Scope Confidence',
                        evidence_strength: 'Evidence Strength',
                        pipeline_reliability: 'Pipeline Reliability'
                      };
                      return map[signal] || signal.replaceAll('_', ' ');
                    }
                    function interpretationDisplay(item) {
                      const map = {
                        structural_exactness_avg: 'Many method structures match exactly, which supports a rename-style similarity.',
                        method_similarity_strength: 'The matched methods are strongly similar across text and structure signals.',
                        modification_strength: 'There are signs of edits beyond simple renaming or formatting.',
                        confirmed_ratio: 'Many method matches are confirmed in both comparison directions.',
                        'coverage_A/B': 'Both files have a meaningful share of methods matched to the other file.',
                        S5_NOT_APPLICABLE: 'The files do not provide enough external API calls for this signal.',
                        S1_if_reliable: 'The non-method file context is also similar.',
                        partial_clone_signal: 'Only part of one file may correspond to the other.',
                        match_score_avg: 'The selected method correspondences are strong overall.',
                        magnitude_avg: 'The combined method-level evidence is strong.'
                      };
                      return map[item.signal] || item.interpretation;
                    }
                    function supportsDisplay(value) {
                      const map = {
                        T1: 'Exact Clone',
                        T2: 'Renamed/Reformatted Clone',
                        T3: 'Modified Clone',
                        T4_WEAK: 'Weak Semantic Similarity',
                        NON_CLONE: 'No Clear Clone',
                        FULL: 'Full-file Similarity',
                        PARTIAL: 'Partial Similarity',
                        MIXED: 'Mixed-scope Similarity',
                        LOWER_CONFIDENCE: 'Lower Confidence'
                      };
                      return map[value] || value;
                    }
                    function cell(label, value) {
                      return `<div class="cell"><div class="label">${label}</div><div>${escapeHtml(value)}</div></div>`;
                    }
                    function percent(value) {
                      return (Number(value) * 100).toFixed(2) + '%';
                    }
                    function escapeHtml(value) {
                      return String(value).replace(/[&<>"']/g, ch => ({
                        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;'
                      }[ch]));
                    }
                  </script>
                </body>
                </html>
                """;
    }
}
