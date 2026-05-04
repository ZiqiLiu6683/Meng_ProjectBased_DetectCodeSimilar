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
                      --bg: #f5f7fa;
                      --panel: #ffffff;
                      --line: #d7dde5;
                      --text: #1f2933;
                      --muted: #5f6b7a;
                      --accent: #0f766e;
                      --accent-soft: #edf8f6;
                      --warn: #9a5b00;
                      --warn-soft: #fff7e8;
                      --bad: #b42318;
                      --bad-soft: #fff1f0;
                      --left-file: #2563eb;
                      --right-file: #0f766e;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      background: var(--bg);
                      color: var(--text);
                      font-family: Arial, Helvetica, sans-serif;
                    }
                    header {
                      background: var(--panel);
                      border-bottom: 1px solid var(--line);
                    }
                    .topbar {
                      max-width: 1320px;
                      margin: 0 auto;
                      padding: 16px 22px;
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      gap: 16px;
                    }
                    h1, h2, h3 {
                      margin: 0;
                    }
                    h1 {
                      font-size: 20px;
                      font-weight: 800;
                    }
                    main {
                      max-width: 1320px;
                      margin: 0 auto;
                      padding: 28px 24px;
                    }
                    button {
                      font: inherit;
                      border: 1px solid var(--accent);
                      border-radius: 6px;
                      padding: 9px 13px;
                      background: var(--accent);
                      color: white;
                      cursor: pointer;
                    }
                    button.secondary {
                      background: white;
                      color: var(--accent);
                    }
                    button:hover {
                      border-color: #0b5f59;
                      background: #0b5f59;
                    }
                    button.secondary:hover {
                      background: var(--accent-soft);
                      color: #0b5f59;
                    }
                    .actions {
                      display: flex;
                      gap: 10px;
                      flex-wrap: wrap;
                    }
                    .input-view {
                      display: grid;
                      gap: 16px;
                    }
                    .editor-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 16px;
                    }
                    .panel {
                      background: var(--panel);
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      overflow: hidden;
                    }
                    .panel-head {
                      padding: 12px;
                      border-bottom: 1px solid var(--line);
                    }
                    input, textarea {
                      width: 100%;
                      font: inherit;
                    }
                    input {
                      border: 1px solid var(--line);
                      border-radius: 6px;
                      padding: 8px 10px;
                    }
                    textarea {
                      min-height: 560px;
                      border: 0;
                      resize: vertical;
                      padding: 14px;
                      line-height: 1.45;
                      font-family: Consolas, "Courier New", monospace;
                      font-size: 13px;
                      color: #111827;
                    }
                    .report-view {
                      display: none;
                      gap: 18px;
                    }
                    .report-shell {
                      display: grid;
                      grid-template-columns: 230px minmax(0, 1fr);
                      gap: 24px;
                      align-items: start;
                    }
                    .report-head {
                      display: flex;
                      justify-content: space-between;
                      align-items: flex-start;
                      gap: 16px;
                      margin-bottom: 18px;
                    }
                    .report-title {
                      display: grid;
                      gap: 8px;
                    }
                    .compare-line {
                      display: flex;
                      align-items: center;
                      gap: 10px;
                      flex-wrap: wrap;
                    }
                    .file-pill {
                      display: inline-flex;
                      max-width: 220px;
                      border: 1px solid var(--line);
                      border-radius: 999px;
                      padding: 7px 10px;
                      background: white;
                      font-size: 13px;
                      font-weight: 700;
                      overflow: hidden;
                      text-overflow: ellipsis;
                      white-space: nowrap;
                    }
                    .file-pill.left {
                      border-color: var(--left-file);
                    }
                    .file-pill.right {
                      border-color: var(--right-file);
                    }
                    .vs {
                      color: var(--muted);
                      font-size: 12px;
                      font-weight: 700;
                      text-transform: uppercase;
                    }
                    .side-nav {
                      position: sticky;
                      top: 18px;
                      background: var(--panel);
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 8px;
                      display: grid;
                      gap: 6px;
                    }
                    .nav-button {
                      width: 100%;
                      border: 0;
                      border-radius: 6px;
                      background: transparent;
                      color: var(--text);
                      text-align: left;
                      padding: 10px 11px;
                      font-weight: 700;
                    }
                    .nav-button:hover {
                      background: #eef2f6;
                      color: var(--text);
                    }
                    .nav-button.active {
                      background: var(--accent-soft);
                      color: #0b5f59;
                    }
                    .content {
                      display: grid;
                      gap: 22px;
                    }
                    .section {
                      background: var(--panel);
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 24px;
                    }
                    .section-head {
                      display: flex;
                      justify-content: space-between;
                      align-items: baseline;
                      gap: 12px;
                      margin-bottom: 14px;
                    }
                    .muted {
                      color: var(--muted);
                      line-height: 1.45;
                    }
                    .label {
                      color: var(--muted);
                      font-size: 12px;
                      font-weight: 700;
                      letter-spacing: .04em;
                      text-transform: uppercase;
                    }
                    .status-card {
                      border: 1px solid var(--line);
                      border-left: 8px solid var(--accent);
                      border-radius: 8px;
                      padding: 18px;
                      background: #f8fcfb;
                      display: grid;
                      gap: 8px;
                    }
                    .status-card.warn {
                      border-left-color: var(--warn);
                      background: var(--warn-soft);
                    }
                    .status-card.bad {
                      border-left-color: var(--bad);
                      background: var(--bad-soft);
                    }
                    .status-title {
                      font-size: 32px;
                      line-height: 1.05;
                      font-weight: 800;
                    }
                    .tag-row {
                      display: flex;
                      flex-wrap: wrap;
                      gap: 8px;
                    }
                    .tag {
                      border: 1px solid var(--line);
                      border-radius: 999px;
                      padding: 6px 9px;
                      background: white;
                      color: var(--muted);
                      font-size: 12px;
                    }
                    .metric-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 16px;
                    }
                    .metric-card {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 18px;
                      background: #fbfcfd;
                    }
                    .metric-number {
                      margin: 6px 0;
                      font-size: 30px;
                      line-height: 1;
                      font-weight: 800;
                      color: var(--accent);
                    }
                    .metric-card.warn .metric-number {
                      color: var(--warn);
                    }
                    .metric-card.bad .metric-number {
                      color: var(--bad);
                    }
                    .bar-list {
                      display: grid;
                      gap: 14px;
                    }
                    .score-row {
                      display: grid;
                      gap: 6px;
                    }
                    .score-top {
                      display: flex;
                      justify-content: space-between;
                      gap: 12px;
                      align-items: baseline;
                      font-size: 14px;
                    }
                    .score-question {
                      line-height: 1.35;
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
                    .fill.warn {
                      background: var(--warn);
                    }
                    .fill.bad {
                      background: var(--bad);
                    }
                    .method-layout {
                      display: grid;
                      grid-template-columns: 280px minmax(0, 1fr);
                      gap: 14px;
                    }
                    .pair-list {
                      display: grid;
                      gap: 8px;
                    }
                    .pair-button {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      background: white;
                      color: var(--text);
                      text-align: left;
                      padding: 10px;
                    }
                    .pair-button:hover {
                      background: #f4f7fa;
                      border-color: var(--line);
                      color: var(--text);
                    }
                    .pair-button.active {
                      border-color: var(--accent);
                      background: var(--accent-soft);
                    }
                    .pair-name {
                      font-weight: 800;
                      overflow-wrap: anywhere;
                    }
                    .pair-meta {
                      margin-top: 6px;
                      color: var(--muted);
                      font-size: 12px;
                      line-height: 1.35;
                    }
                    .pair-detail {
                      display: grid;
                      gap: 12px;
                    }
                    .pair-title {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 12px;
                      background: #fbfcfd;
                      display: grid;
                      gap: 8px;
                    }
                    .method-map {
                      display: grid;
                      grid-template-columns: minmax(0, 1fr) 32px minmax(0, 1fr);
                      gap: 10px;
                      align-items: center;
                    }
                    .method-name {
                      min-width: 0;
                      overflow-wrap: anywhere;
                      font-weight: 800;
                    }
                    .method-name.left {
                      border-left: 4px solid var(--left-file);
                      padding-left: 8px;
                    }
                    .method-name.right {
                      border-left: 4px solid var(--right-file);
                      padding-left: 8px;
                    }
                    .arrow {
                      color: var(--muted);
                      text-align: center;
                      font-weight: 800;
                    }
                    .code-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 12px;
                    }
                    .code-card {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      overflow: hidden;
                      background: #fbfcfd;
                    }
                    .code-head {
                      padding: 9px 11px;
                      border-bottom: 1px solid var(--line);
                      font-weight: 800;
                      white-space: nowrap;
                      overflow: hidden;
                      text-overflow: ellipsis;
                    }
                    .code-head.left {
                      border-left: 5px solid var(--left-file);
                    }
                    .code-head.right {
                      border-left: 5px solid var(--right-file);
                    }
                    pre.code {
                      margin: 0;
                      max-height: 520px;
                      overflow: auto;
                      padding: 0;
                      background: white;
                      font-family: Consolas, "Courier New", monospace;
                      font-size: 12px;
                      line-height: 1.45;
                    }
                    .code-line {
                      display: grid;
                      grid-template-columns: 42px minmax(0, 1fr);
                      min-height: 18px;
                    }
                    .line-no {
                      color: #8a95a3;
                      background: #f3f5f8;
                      border-right: 1px solid #e1e6ed;
                      text-align: right;
                      padding-right: 8px;
                      user-select: none;
                    }
                    .line-code {
                      padding-left: 10px;
                      white-space: pre;
                    }
                    .code-line.hl .line-code {
                      background: #e9f7f4;
                    }
                    .evidence-board {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 12px;
                    }
                    .evidence-hero {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 16px;
                      margin-bottom: 18px;
                    }
                    .evidence-panel {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 18px;
                      background: #fbfcfd;
                    }
                    .evidence-panel.support {
                      border-left: 7px solid var(--accent);
                    }
                    .evidence-panel.concern {
                      border-left: 7px solid var(--warn);
                    }
                    .evidence-score {
                      font-size: 34px;
                      font-weight: 800;
                      line-height: 1;
                      margin: 8px 0;
                    }
                    .evidence-card {
                      border: 1px solid var(--line);
                      border-left: 6px solid var(--accent);
                      border-radius: 8px;
                      padding: 14px;
                      background: #fbfcfd;
                    }
                    .evidence-card.warn {
                      border-left-color: var(--warn);
                    }
                    .evidence-card.bad {
                      border-left-color: var(--bad);
                    }
                    .evidence-card.info {
                      border-left-color: #4b6f9f;
                    }
                    .evidence-count {
                      font-size: 30px;
                      font-weight: 800;
                      margin: 6px 0;
                    }
                    .evidence-list {
                      display: grid;
                      gap: 10px;
                      margin-top: 14px;
                    }
                    .signal-table {
                      display: grid;
                      gap: 8px;
                    }
                    .signal-row {
                      display: grid;
                      grid-template-columns: minmax(220px, 1.4fr) 90px 90px minmax(160px, 1fr);
                      gap: 12px;
                      align-items: center;
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 11px 12px;
                      background: white;
                    }
                    .signal-name {
                      font-weight: 800;
                    }
                    .signal-pill {
                      display: inline-flex;
                      justify-content: center;
                      border-radius: 999px;
                      padding: 5px 8px;
                      background: #eef2f6;
                      color: var(--muted);
                      font-size: 12px;
                      font-weight: 700;
                    }
                    .signal-pill.high {
                      background: var(--accent-soft);
                      color: #0b5f59;
                    }
                    .signal-pill.medium {
                      background: var(--warn-soft);
                      color: var(--warn);
                    }
                    .signal-pill.low {
                      background: #f1f3f6;
                      color: var(--muted);
                    }
                    .evidence-item {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 12px;
                      background: white;
                    }
                    .detail-grid {
                      display: grid;
                      grid-template-columns: repeat(3, minmax(0, 1fr));
                      gap: 12px;
                    }
                    .detail-card {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 12px;
                      background: #fbfcfd;
                    }
                    .json-box {
                      max-height: 680px;
                      overflow: auto;
                      margin: 0;
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 12px;
                      background: white;
                      font-size: 12px;
                      line-height: 1.45;
                    }
                    .hidden {
                      display: none;
                    }
                    @media (max-width: 980px) {
                      .editor-grid,
                      .report-shell,
                      .method-layout,
                      .code-grid,
                      .metric-grid,
                      .evidence-board,
                      .evidence-hero,
                      .detail-grid {
                        grid-template-columns: 1fr;
                      }
                      .signal-row {
                        grid-template-columns: 1fr;
                      }
                      textarea {
                        min-height: 360px;
                      }
                      .side-nav {
                        position: static;
                      }
                    }
                  </style>
                </head>
                <body>
                  <header>
                    <div class="topbar">
                      <h1>Code Similarity Analyzer</h1>
                      <div class="actions" id="inputActions">
                        <button class="secondary" id="sampleBtn" type="button">Load Sample</button>
                        <button id="analyzeBtn" type="button">Analyze</button>
                      </div>
                      <div class="actions hidden" id="reportActions">
                        <button class="secondary" id="backBtn" type="button">Back to Input</button>
                        <button id="rerunBtn" type="button">Run Again</button>
                      </div>
                    </div>
                  </header>
                  <main>
                    <section class="input-view" id="inputView">
                      <div class="editor-grid">
                        <div class="panel">
                          <div class="panel-head"><input id="fileA" value="B1.java" aria-label="File 1 name"></div>
                          <textarea id="sourceA" spellcheck="false" aria-label="Source 1"></textarea>
                        </div>
                        <div class="panel">
                          <div class="panel-head"><input id="fileB" value="B2.java" aria-label="File 2 name"></div>
                          <textarea id="sourceB" spellcheck="false" aria-label="Source 2"></textarea>
                        </div>
                      </div>
                    </section>
                    <section class="report-view" id="reportView">
                      <div class="report-head">
                        <div class="report-title">
                          <h2>Similarity Report</h2>
                          <div class="compare-line" id="reportCompare"></div>
                        </div>
                      </div>
                      <div class="report-shell">
                        <nav class="side-nav" id="sectionNav"></nav>
                        <div class="content" id="sectionContent"></div>
                      </div>
                    </section>
                  </main>
                  <script>
                    const sourceA = document.querySelector('#sourceA');
                    const sourceB = document.querySelector('#sourceB');
                    const fileA = document.querySelector('#fileA');
                    const fileB = document.querySelector('#fileB');
                    const inputView = document.querySelector('#inputView');
                    const reportView = document.querySelector('#reportView');
                    const inputActions = document.querySelector('#inputActions');
                    const reportActions = document.querySelector('#reportActions');
                    const sectionNav = document.querySelector('#sectionNav');
                    const sectionContent = document.querySelector('#sectionContent');
                    const reportCompare = document.querySelector('#reportCompare');
                    let reportData = null;
                    let activeSection = 'summary';
                    let selectedPairIndex = 0;
                    const sections = [
                      ['summary', 'General Summary'],
                      ['methods', 'Matched Methods'],
                      ['evidence', 'Evidence'],
                      ['details', 'Analysis Details'],
                      ['json', 'Developer JSON']
                    ];
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
                    document.querySelector('#rerunBtn').addEventListener('click', analyze);
                    document.querySelector('#backBtn').addEventListener('click', showInput);
                    function showInput() {
                      reportView.style.display = 'none';
                      inputView.style.display = 'grid';
                      reportActions.classList.add('hidden');
                      inputActions.classList.remove('hidden');
                    }
                    function showReport() {
                      inputView.style.display = 'none';
                      reportView.style.display = 'grid';
                      inputActions.classList.add('hidden');
                      reportActions.classList.remove('hidden');
                    }
                    async function analyze() {
                      const body = new URLSearchParams({
                        fileA: fileA.value || 'Left Code',
                        fileB: fileB.value || 'Right Code',
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
                        reportData = data;
                        selectedPairIndex = 0;
                        activeSection = 'summary';
                        showReport();
                        renderReport();
                      } catch (err) {
                        alert(err.message);
                      }
                    }
                    function renderReport() {
                      if (!reportData) return;
                      const files = fileInfo(reportData);
                      reportCompare.innerHTML = `${filePill(files.left, 'left')}<span class="vs">vs</span>${filePill(files.right, 'right')}`;
                      sectionNav.innerHTML = sections.map(([id, label]) =>
                        `<button class="nav-button ${id === activeSection ? 'active' : ''}" data-section="${id}" type="button">${label}</button>`
                      ).join('');
                      sectionNav.querySelectorAll('button').forEach(btn => {
                        btn.addEventListener('click', () => {
                          activeSection = btn.dataset.section;
                          renderReport();
                        });
                      });
                      if (activeSection === 'summary') sectionContent.innerHTML = renderSummary(reportData, files);
                      if (activeSection === 'methods') sectionContent.innerHTML = renderMethods(reportData, files);
                      if (activeSection === 'evidence') sectionContent.innerHTML = renderEvidence(reportData);
                      if (activeSection === 'details') sectionContent.innerHTML = renderDetails(reportData);
                      if (activeSection === 'json') sectionContent.innerHTML = renderJson(reportData);
                      bindSectionEvents();
                    }
                    function renderSummary(data, files) {
                      const summary = data.summary;
                      const stage3 = data.stage3;
                      const stage4 = data.stage4;
                      const clone = cloneText(summary.cloneType);
                      const scope = scopeText(summary.scopeType);
                      const coverage = (Number(stage3.coverageA) + Number(stage3.coverageB)) / 2;
                      return `<section class="section">
                        <div class="status-card ${statusClass(summary.cloneType, summary.scopeType, summary.confidenceLevel)}">
                          <div class="label">General Summary</div>
                          <div class="status-title">${decisionText(summary.cloneType, summary.scopeType)}</div>
                          <div class="muted">${clone.short} - ${scope.short}</div>
                          <div class="tag-row">
                            <span class="tag">Type: ${summary.cloneType} - ${clone.short}</span>
                            <span class="tag">Scope: ${summary.scopeType} - ${scope.short}</span>
                            <span class="tag">Confidence: ${summary.confidenceLevel}</span>
                          </div>
                        </div>
                        <div class="metric-grid">
                          ${metricCard('Confidence', summary.confidence, 'How reliable the final decision is.', 'positive')}
                          ${metricCard('Matched Functions', stage3.matchScoreAvg, 'High value means many functions line up.', 'risk')}
                          ${metricCard('How Much Code Matches', coverage, 'High value means much of the files match.', 'risk')}
                          ${metricCard('Partial-Only Match', stage3.partialCloneSignal, 'High value means the match is concentrated in part of the files.', 'risk')}
                        </div>
                        <div class="section">
                          <div class="section-head">
                            <h3>How Much Was Matched</h3>
                            <div class="muted">Measured in both directions</div>
                          </div>
                          <div class="bar-list">
                            ${directionalScore(files.left, files.right, stage3.coverageA)}
                            ${directionalScore(files.right, files.left, stage3.coverageB)}
                            ${scoreBar('Does this look like only part of one file matches?', stage3.partialCloneSignal)}
                          </div>
                        </div>
                        <div class="evidence-board">
                          ${evidenceCard('Supports Similarity', stage4.evidenceChain.supportingEvidence, '')}
                          ${evidenceCard('Suggests Modification', stage4.evidenceChain.opposingEvidence, 'warn')}
                          ${evidenceCard('Supports Scope', stage4.evidenceChain.scopeEvidence, 'info')}
                          ${evidenceCard('Reliability Warnings', stage4.evidenceChain.reliabilityWarnings, stage4.evidenceChain.reliabilityWarnings.length ? 'bad' : '')}
                        </div>
                      </section>`;
                    }
                    function renderMethods(data, files) {
                      const pairs = data.stage3.mergedPairs;
                      const selected = pairs[selectedPairIndex] || null;
                      const pairList = pairs.length ? pairs.map((pair, index) => `
                        <button class="pair-button ${index === selectedPairIndex ? 'active' : ''}" data-pair="${index}" type="button">
                          <div class="pair-name">${escapeHtml(methodNameOnly(pair.methodAId))} -> ${escapeHtml(methodNameOnly(pair.methodBId))}</div>
                          <div class="pair-meta">Match ${percent(pair.matchScore)} - Text ${percent(pair.feature.s3)} - Structure ${percent(pair.feature.s4)}</div>
                        </button>`).join('') : '<div class="muted">No matched methods were selected.</div>';
                      const detail = selected ? `
                        <div class="pair-detail">
                          <div class="pair-title">
                            <div class="label">Selected Match</div>
                            <div class="method-map">
                              <div class="method-name left">${escapeHtml(shortMethod(selected.methodAId))}</div>
                              <div class="arrow">-></div>
                              <div class="method-name right">${escapeHtml(shortMethod(selected.methodBId))}</div>
                            </div>
                            <div class="tag-row">
                              <span class="tag">Match ${percent(selected.matchScore)}</span>
                              <span class="tag">Text ${percent(selected.feature.s3)}</span>
                              <span class="tag">Structure ${percent(selected.feature.s4)}</span>
                            </div>
                            <div class="muted">The highlighted method blocks are an interface preview based on method names. Exact line mapping can be added next.</div>
                          </div>
                          <div class="code-grid">
                            ${codePanel(files.left, 'left', sourceA.value, selected.methodAId)}
                            ${codePanel(files.right, 'right', sourceB.value, selected.methodBId)}
                          </div>
                        </div>` : '<div class="muted">Select a method pair to inspect source code.</div>';
                      return `<section class="section">
                        <div class="section-head">
                          <h3>Matched Methods</h3>
                          <div class="muted">${pairs.length} selected</div>
                        </div>
                        <div class="method-layout">
                          <div class="pair-list">${pairList}</div>
                          ${detail}
                        </div>
                      </section>`;
                    }
                    function renderEvidence(data) {
                      const chain = data.stage4.evidenceChain;
                      const supportScore = evidenceGroupScore(chain.supportingEvidence.concat(chain.scopeEvidence));
                      const concernScore = evidenceGroupScore(chain.opposingEvidence.concat(chain.reliabilityWarnings));
                      return `<section class="section">
                        <div class="section-head">
                          <h3>Evidence</h3>
                          <div class="muted">The strongest reasons behind the decision</div>
                        </div>
                        <div class="evidence-hero">
                          <div class="evidence-panel support">
                            <div class="label">Evidence Supporting Similarity</div>
                            <div class="evidence-score">${percent(supportScore)}</div>
                            <div class="muted">${chain.supportingEvidence.length + chain.scopeEvidence.length} supporting signals found.</div>
                          </div>
                          <div class="evidence-panel concern">
                            <div class="label">Evidence That Needs Review</div>
                            <div class="evidence-score">${percent(concernScore)}</div>
                            <div class="muted">${chain.opposingEvidence.length + chain.reliabilityWarnings.length} modifying or reliability signals found.</div>
                          </div>
                        </div>
                        <div class="section-head" style="margin-top:22px">
                          <h3>Key Signals</h3>
                          <div class="muted">Compact view; raw JSON keeps the full detail.</div>
                        </div>
                        <div class="signal-table">
                          ${signalRows(chain)}
                        </div>
                      </section>`;
                    }
                    function renderDetails(data) {
                      const stage0 = data.stage0;
                      const stage1 = data.stage1;
                      const stage2 = data.stage2;
                      const stage3 = data.stage3;
                      const stage4 = data.stage4;
                      return `<section class="section">
                        <div class="section-head">
                          <h3>Analysis Details</h3>
                          <div class="muted">How the result was produced</div>
                        </div>
                        <h3>Input Overview</h3>
                        <div class="detail-grid">
                          ${detailCard('Input Pattern', inputPatternText(stage0.primaryMode), 'Overall shape of the comparison.')}
                          ${detailCard('Methods in Left File', String(stage0.methodCountA), 'Detected method declarations.')}
                          ${detailCard('Methods in Right File', String(stage0.methodCountB), 'Detected method declarations.')}
                          ${detailCard('Compared Method Pairs', String(stage1.pairMatrix.length), 'All method pairs compared before selection.')}
                          ${detailCard('Selected Method Matches', String(stage3.mergedPairs.length), 'Best matches retained for the report.')}
                          ${detailCard('Pipeline Reliability', percent(stage4.pipelineReliability), 'Whether important signals were available.')}
                        </div>
                        <h3 style="margin-top:18px">Metric Breakdown</h3>
                        <div class="bar-list">
                          ${scoreBar('Code Text Similarity', stage3.centroidS3)}
                          ${scoreBar('Code Structure Similarity', stage3.centroidS4)}
                          ${scoreBar('Same Code Shape', stage3.structuralExactnessAvg)}
                          ${scoreBar('Signs of Real Edits', stage3.tokenExactGapAvg)}
                          ${scoreBar('Evidence Strength', stage4.evidenceStrength)}
                          ${scoreBar('Evidence Consistency', stage4.evidenceConsistency)}
                        </div>
                        <h3 style="margin-top:18px">Signal Availability</h3>
                        <div class="detail-grid">
                          ${detailCard('File Context Signal', percent(stage3.s1), 'Class-level and non-method context.')}
                          ${detailCard('API Signal', stage3.s5Status === 'APPLICABLE' ? percent(stage3.s5) : stage3.s5Status, 'External API vocabulary signal.')}
                          ${detailCard('Exact File Match', String(stage1.fileExactNormalizedMatch), 'Whether normalized files are exactly equal.')}
                        </div>
                      </section>`;
                    }
                    function renderJson(data) {
                      return `<section class="section">
                        <div class="section-head">
                          <h3>Developer JSON</h3>
                          <div class="muted">Raw output for debugging or integration</div>
                        </div>
                        <pre class="json-box">${escapeHtml(JSON.stringify(data, null, 2))}</pre>
                      </section>`;
                    }
                    function bindSectionEvents() {
                      sectionContent.querySelectorAll('[data-pair]').forEach(btn => {
                        btn.addEventListener('click', () => {
                          selectedPairIndex = Number(btn.dataset.pair);
                          renderReport();
                        });
                      });
                    }
                    function fileInfo(data) {
                      return {
                        left: displayFileName(data.fileA, 'Left Code'),
                        right: displayFileName(data.fileB, 'Right Code')
                      };
                    }
                    function displayFileName(fileName, fallback) {
                      const raw = fileName || fallback;
                      const base = raw.split(/[\\\\/]/).pop() || fallback;
                      const short = base.length > 20 ? `${base.slice(0, 17)}...` : base;
                      return { raw, short };
                    }
                    function filePill(file, side) {
                      return `<span class="file-pill ${side}" title="${escapeHtml(file.raw)}">${escapeHtml(file.short)}</span>`;
                    }
                    function decisionText(cloneType, scopeType) {
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
                    function directionalScore(fromFile, toFile, value) {
                      return scoreBar(`How much of ${filePill(fromFile, 'left')} has matching code in ${filePill(toFile, 'right')}?`, value);
                    }
                    function metricCard(label, value, help, polarity) {
                      const n = Number(value) || 0;
                      const cls = metricClass(n, polarity);
                      return `<div class="metric-card ${cls}">
                        <div class="label">${escapeHtml(label)}</div>
                        <div class="metric-number">${percent(n)}</div>
                        <div class="muted">${escapeHtml(help)}</div>
                      </div>`;
                    }
                    function metricClass(value, polarity) {
                      if (polarity === 'risk') {
                        if (value >= 0.75) return 'bad';
                        if (value >= 0.4) return 'warn';
                        return '';
                      }
                      if (value < 0.35) return 'bad';
                      if (value < 0.65) return 'warn';
                      return '';
                    }
                    function detailCard(label, value, help) {
                      return `<div class="detail-card">
                        <div class="label">${escapeHtml(label)}</div>
                        <div style="font-size:20px;font-weight:800;margin:5px 0">${escapeHtml(value)}</div>
                        <div class="muted">${escapeHtml(help)}</div>
                      </div>`;
                    }
                    function scoreBar(label, value) {
                      const n = Number(value) || 0;
                      const cls = n < 0.35 ? 'bad' : n < 0.65 ? 'warn' : '';
                      return `<div class="score-row">
                        <div class="score-top"><span class="score-question">${label}</span><strong>${percent(n)}</strong></div>
                        <div class="track"><div class="fill ${cls}" style="width:${Math.max(0, Math.min(100, n * 100))}%"></div></div>
                      </div>`;
                    }
                    function methodNameOnly(methodId) {
                      const hash = methodId.indexOf('#');
                      if (hash < 0) return methodId;
                      return methodId.slice(hash + 1).replace(/#\\d+$/, '');
                    }
                    function shortMethod(methodId) {
                      const hash = methodId.indexOf('#');
                      if (hash < 0) return methodId;
                      const type = methodId.slice(0, hash);
                      return `${type}.${methodNameOnly(methodId)}`;
                    }
                    function codePanel(file, side, source, methodId) {
                      return `<div class="code-card">
                        <div class="code-head ${side}" title="${escapeHtml(file.raw)}">${escapeHtml(file.short)}</div>
                        <pre class="code">${renderCode(source, methodId)}</pre>
                      </div>`;
                    }
                    function renderCode(source, methodId) {
                      const lines = source.split('\\n');
                      const range = findMethodRange(lines, methodId);
                      return lines.map((line, index) => {
                        const lineNo = index + 1;
                        const hl = range && lineNo >= range.start && lineNo <= range.end ? ' hl' : '';
                        return `<span class="code-line${hl}"><span class="line-no">${lineNo}</span><span class="line-code">${escapeHtml(line || ' ')}</span></span>`;
                      }).join('');
                    }
                    function findMethodRange(lines, methodId) {
                      const name = methodNameOnly(methodId).replace(/\\(.*/, '');
                      if (!name) return null;
                      const pattern = new RegExp('\\\\b' + escapeRegExp(name) + '\\\\s*\\\\(');
                      let start = -1;
                      for (let i = 0; i < lines.length; i++) {
                        if (pattern.test(lines[i])) {
                          start = i;
                          break;
                        }
                      }
                      if (start < 0) return null;
                      let depth = 0;
                      let seenOpen = false;
                      for (let i = start; i < lines.length; i++) {
                        for (const ch of lines[i]) {
                          if (ch === '{') {
                            depth++;
                            seenOpen = true;
                          } else if (ch === '}') {
                            depth--;
                          }
                        }
                        if (seenOpen && depth <= 0) {
                          return { start: start + 1, end: i + 1 };
                        }
                      }
                      return { start: start + 1, end: start + 1 };
                    }
                    function evidenceCard(title, items, cls) {
                      const strength = strongest(items);
                      const note = items.length === 0 ? 'No signals in this group' : `${strength} signal${items.length === 1 ? '' : 's'}`;
                      return `<div class="evidence-card ${cls}">
                        <div class="label">${escapeHtml(title)}</div>
                        <div class="evidence-count">${items.length}</div>
                        <div class="muted">${escapeHtml(note)}</div>
                      </div>`;
                    }
                    function signalRows(chain) {
                      const groups = [
                        ['Support', chain.supportingEvidence],
                        ['Review', chain.opposingEvidence],
                        ['Scope', chain.scopeEvidence],
                        ['Reliability', chain.reliabilityWarnings]
                      ];
                      const rows = groups.flatMap(([group, items]) => items.map(item => [group, item]));
                      if (!rows.length) {
                        return '<div class="muted">No evidence signals were produced.</div>';
                      }
                      return rows.map(([group, item]) => signalRow(group, item)).join('');
                    }
                    function signalRow(group, item) {
                      return `<div class="signal-row">
                        <div>
                          <div class="signal-name">${escapeHtml(signalDisplay(item.signal))}</div>
                          <div class="muted">${escapeHtml(shortInterpretation(item))}</div>
                        </div>
                        <div>${escapeHtml(item.value)}</div>
                        <div><span class="signal-pill ${item.strength.toLowerCase()}">${escapeHtml(item.strength)}</span></div>
                        <div class="muted">${escapeHtml(group)} - ${escapeHtml(supportsDisplay(item.supports))}</div>
                      </div>`;
                    }
                    function shortInterpretation(item) {
                      const text = interpretationDisplay(item);
                      return text.length > 92 ? `${text.slice(0, 89)}...` : text;
                    }
                    function evidenceGroupScore(items) {
                      if (!items.length) return 0.0;
                      const total = items.reduce((sum, item) => {
                        if (item.strength === 'HIGH') return sum + 1.0;
                        if (item.strength === 'MEDIUM') return sum + 0.65;
                        if (item.strength === 'LOW') return sum + 0.3;
                        return sum + 0.0;
                      }, 0.0);
                      return Math.min(1.0, total / Math.max(2, items.length));
                    }
                    function strongest(items) {
                      if (items.some(item => item.strength === 'HIGH')) return 'High';
                      if (items.some(item => item.strength === 'MEDIUM')) return 'Medium';
                      if (items.some(item => item.strength === 'LOW')) return 'Low';
                      return 'No';
                    }
                    function signalDisplay(signal) {
                      const map = {
                        structural_exactness_avg: 'Same Code Shape',
                        method_similarity_strength: 'Matched Functions Look Similar',
                        modification_strength: 'Signs of Real Edits',
                        confirmed_ratio: 'Matches Work Both Ways',
                        'coverage_A/B': 'Both Files Are Mostly Covered',
                        S5_NOT_APPLICABLE: 'API Signal Not Available',
                        S1_if_reliable: 'File Context Similarity',
                        partial_clone_signal: 'Signs of Partial Copying',
                        match_score_avg: 'Matched Functions',
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
                        match_score_avg: 'The selected function matches are strong overall.',
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
                    function percent(value) {
                      return (Number(value) * 100).toFixed(2) + '%';
                    }
                    function escapeHtml(value) {
                      return String(value).replace(/[&<>"']/g, ch => ({
                        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;'
                      }[ch]));
                    }
                    function escapeRegExp(value) {
                      return String(value).replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&');
                    }
                  </script>
                </body>
                </html>
                """;
    }
}
