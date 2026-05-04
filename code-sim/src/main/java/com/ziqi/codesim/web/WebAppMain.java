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
                      gap: 20px;
                    }
                    .input-intro {
                      display: flex;
                      justify-content: space-between;
                      gap: 18px;
                      align-items: flex-end;
                    }
                    .input-title {
                      display: grid;
                      gap: 6px;
                    }
                    .input-title h2 {
                      font-size: 26px;
                    }
                    .input-footer {
                      display: flex;
                      justify-content: flex-end;
                      align-items: center;
                      gap: 16px;
                    }
                    .editor-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 22px;
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
                      font-weight: 800;
                    }
                    textarea {
                      min-height: 640px;
                      border: 0;
                      resize: vertical;
                      padding: 18px;
                      line-height: 1.45;
                      font-family: Consolas, "Courier New", monospace;
                      font-size: 14px;
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
                    .summary-lead {
                      font-size: 22px;
                      line-height: 1.35;
                      font-weight: 800;
                    }
                    .summary-lead strong {
                      color: var(--accent);
                    }
                    .summary-lead.warn strong {
                      color: var(--warn);
                    }
                    .summary-lead.bad strong {
                      color: var(--bad);
                    }
                    .focus-grid {
                      display: grid;
                      grid-template-columns: 1.25fr 1.25fr .9fr;
                      gap: 14px;
                    }
                    .focus-card {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 16px;
                      background: #fbfcfd;
                      display: grid;
                      gap: 7px;
                    }
                    .focus-card.warn .metric-number {
                      color: var(--warn);
                    }
                    .focus-card.bad .metric-number {
                      color: var(--bad);
                    }
                    .sub-panel {
                      border-top: 1px solid var(--line);
                      padding-top: 18px;
                    }
                    .focus-value {
                      font-size: 24px;
                      line-height: 1.12;
                      font-weight: 800;
                    }
                    .summary-columns {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 16px;
                    }
                    .summary-columns .detail-grid {
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                    }
                    .ranking-list {
                      display: grid;
                      gap: 11px;
                    }
                    .ranking-row {
                      display: grid;
                      gap: 6px;
                    }
                    .ranking-top {
                      display: flex;
                      justify-content: space-between;
                      gap: 12px;
                      align-items: baseline;
                      font-size: 14px;
                    }
                    .ranking-name {
                      font-weight: 800;
                    }
                    .ranking-row.dim {
                      opacity: .55;
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
                      gap: 18px;
                    }
                    .pair-list {
                      display: flex;
                      gap: 10px;
                      overflow-x: auto;
                      padding: 2px 2px 8px;
                    }
                    .pair-button {
                      min-width: 260px;
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      background: white;
                      color: var(--text);
                      text-align: left;
                      padding: 12px;
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
                      gap: 16px;
                    }
                    .pair-title {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 16px;
                      background: #fbfcfd;
                      display: grid;
                      gap: 12px;
                    }
                    .match-summary-grid {
                      display: grid;
                      grid-template-columns: minmax(0, 1fr) minmax(240px, 320px);
                      gap: 16px;
                      align-items: center;
                    }
                    .method-map {
                      display: grid;
                      grid-template-columns: minmax(0, 1fr) 42px minmax(0, 1fr);
                      gap: 14px;
                      align-items: center;
                    }
                    .method-name {
                      min-width: 0;
                      overflow-wrap: anywhere;
                      font-weight: 800;
                    }
                    .method-name.left {
                      border-left: 4px solid var(--left-file);
                      padding-left: 10px;
                    }
                    .method-name.right {
                      border-left: 4px solid var(--right-file);
                      padding-left: 10px;
                    }
                    .arrow {
                      color: var(--muted);
                      text-align: center;
                      font-weight: 800;
                    }
                    .code-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 16px;
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
                      max-height: 620px;
                      min-height: 320px;
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
                    .ring-panel {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 18px;
                      background: #fbfcfd;
                      display: grid;
                      grid-template-columns: 108px minmax(0, 1fr);
                      gap: 18px;
                      align-items: center;
                    }
                    .ring {
                      width: 96px;
                      height: 96px;
                      border-radius: 50%;
                      display: grid;
                      place-items: center;
                      background:
                        radial-gradient(circle at center, #fbfcfd 0 58%, transparent 59%),
                        conic-gradient(var(--ring-color) var(--ring-value), #e7ebf0 0);
                    }
                    .ring strong {
                      font-size: 17px;
                    }
                    .ring-panel.support {
                      --ring-color: var(--accent);
                    }
                    .ring-panel.concern {
                      --ring-color: var(--warn);
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
                      .focus-grid,
                      .summary-columns,
                      .metric-grid,
                      .evidence-board,
                      .evidence-hero,
                      .detail-grid {
                        grid-template-columns: 1fr;
                      }
                      .signal-row {
                        grid-template-columns: 1fr;
                      }
                      .ring-panel {
                        grid-template-columns: 1fr;
                      }
                      textarea {
                        min-height: 360px;
                      }
                      .input-intro,
                      .input-footer {
                        align-items: stretch;
                        flex-direction: column;
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
                      <div class="actions hidden" id="reportActions">
                        <button class="secondary" id="backBtn" type="button">Back to Input</button>
                        <button id="rerunBtn" type="button">Run Again</button>
                      </div>
                    </div>
                  </header>
                  <main>
                    <section class="input-view" id="inputView">
                      <div class="input-intro">
                        <div class="input-title">
                          <h2>Compare Two Java Files</h2>
                          <div class="muted">Paste code into both editors, then run the similarity analysis.</div>
                        </div>
                      </div>
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
                      <div class="input-footer">
                        <div class="muted">Use the sample to preview a multi-method comparison.</div>
                        <div class="actions" id="inputActions">
                          <button class="secondary" id="sampleBtn" type="button">Load Sample</button>
                          <button id="analyzeBtn" type="button">Analyze</button>
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
                      ['evidence', 'Why This Result?'],
                      ['json', 'Developer JSON']
                    ];
                    const sampleA = `import java.util.ArrayList;
                import java.util.List;

                class OrderRiskA {
                    int scoreOrder(List<Integer> quantities, String email, String country) {
                        int score = 0;
                        score += quantityRisk(quantities);
                        if (isSuspiciousEmail(email)) {
                            score += 25;
                        }
                        if ("CN".equals(normalizeCountry(country))) {
                            score += 5;
                        }
                        return Math.min(score, 100);
                    }

                    int quantityRisk(List<Integer> quantities) {
                        int risk = 0;
                        for (int amount : quantities) {
                            if (amount > 20) {
                                risk += 10;
                            } else if (amount > 8) {
                                risk += 4;
                            }
                        }
                        return risk;
                    }

                    boolean isSuspiciousEmail(String email) {
                        if (email == null) {
                            return true;
                        }
                        String lower = email.toLowerCase();
                        return lower.endsWith("@tempmail.com") || lower.contains("test");
                    }

                    String normalizeCountry(String country) {
                        if (country == null || country.isBlank()) {
                            return "UNKNOWN";
                        }
                        return country.trim().toUpperCase();
                    }
                }`;
                    const sampleB = `import java.util.ArrayList;
                import java.util.List;

                class OrderRiskB {
                    int calculateRisk(List<Integer> items, String contact, String region) {
                        int total = itemRiskScore(items);
                        if (looksLikeTemporaryEmail(contact)) {
                            total = total + 25;
                        }
                        String normalizedRegion = cleanRegion(region);
                        if ("CN".equals(normalizedRegion)) {
                            total = total + 5;
                        }
                        return Math.min(100, total);
                    }

                    int itemRiskScore(List<Integer> items) {
                        int points = 0;
                        for (int count : items) {
                            if (count > 20) {
                                points += 10;
                            } else if (count > 8) {
                                points += 4;
                            }
                        }
                        return points;
                    }

                    boolean looksLikeTemporaryEmail(String contact) {
                        if (contact == null) {
                            return true;
                        }
                        String value = contact.toLowerCase();
                        return value.contains("test") || value.endsWith("@tempmail.com");
                    }

                    String cleanRegion(String region) {
                        if (region == null || region.isBlank()) {
                            return "UNKNOWN";
                        }
                        return region.trim().toUpperCase();
                    }

                    List<Integer> keepLargeOrders(List<Integer> items) {
                        List<Integer> result = new ArrayList<>();
                        for (int count : items) {
                            if (count > 8) {
                                result.add(count);
                            }
                        }
                        return result;
                    }
                }`;
                    document.querySelector('#sampleBtn').addEventListener('click', () => {
                      sourceA.value = sampleA;
                      sourceB.value = sampleB;
                      fileA.value = 'OrderRiskA.java';
                      fileB.value = 'OrderRiskB.java';
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
                      if (activeSection === 'evidence') sectionContent.innerHTML = renderEvidence(reportData, files);
                      if (activeSection === 'json') sectionContent.innerHTML = renderJson(reportData);
                      bindSectionEvents();
                    }
                    function renderSummary(data, files) {
                      const summary = data.summary;
                      const stage3 = data.stage3;
                      const stage4 = data.stage4;
                      const clone = cloneText(summary.cloneType);
                      const scope = scopeText(summary.scopeType);
                      const leadClass = statusClass(summary.cloneType, summary.scopeType, summary.confidenceLevel);
                      const confidenceClass = metricClass(summary.confidence, 'positive');
                      return `<section class="section">
                        <div class="status-card ${statusClass(summary.cloneType, summary.scopeType, summary.confidenceLevel)}">
                          <div class="label">General Summary</div>
                          <div class="summary-lead ${leadClass}">${summarySentence(summary, clone, scope)}</div>
                          <div class="tag-row">
                            <span class="tag">${decisionText(summary.cloneType, summary.scopeType)}</span>
                            <span class="tag">Confidence: ${summary.confidenceLevel}</span>
                            <span class="tag">Checks completed: ${percent(stage4.pipelineReliability)}</span>
                          </div>
                        </div>
                        <div class="focus-grid">
                          <div class="focus-card">
                            <div class="label">What Kind Of Match?</div>
                            <div class="focus-value">${clone.short}</div>
                            <div class="muted">${clone.description}</div>
                          </div>
                          <div class="focus-card">
                            <div class="label">How Broad Is It?</div>
                            <div class="focus-value">${scope.short}</div>
                            <div class="muted">${scope.description}</div>
                          </div>
                          <div class="focus-card ${confidenceClass}">
                            <div class="label">Confidence</div>
                            <div class="metric-number">${percent(summary.confidence)}</div>
                            <div class="muted">How sure the analyzer is about this result.</div>
                          </div>
                        </div>
                        <div class="summary-columns">
                          <div class="sub-panel">
                            <div class="section-head">
                              <h3>Possible Match Types</h3>
                              <div class="muted">Most likely first</div>
                            </div>
                            <div class="ranking-list">${rankingList(typeRankings(stage4.typeScores), 'risk')}</div>
                          </div>
                          <div class="sub-panel">
                            <div class="section-head">
                              <h3>How Much Is Shared?</h3>
                              <div class="muted">Most likely first</div>
                            </div>
                            <div class="ranking-list">${rankingList(scopeRankings(stage4.scopeScores), 'risk')}</div>
                          </div>
                        </div>
                        <div class="sub-panel">
                          <div class="section-head">
                            <h3>How Much Code Lines Up?</h3>
                            <div class="muted">Measured in both directions</div>
                          </div>
                          <div class="bar-list">
                            ${directionalScore(files.left, files.right, stage3.coverageA)}
                            ${directionalScore(files.right, files.left, stage3.coverageB)}
                            ${scoreBar('Does the overlap seem concentrated in only one area?', stage3.partialCloneSignal)}
                          </div>
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
                            <div class="match-summary-grid">
                              <div>
                                <div class="label">Selected Match</div>
                                <div class="method-map" style="margin-top:8px">
                                  <div class="method-name left">${escapeHtml(shortMethod(selected.methodAId))}</div>
                                  <div class="arrow">-></div>
                                  <div class="method-name right">${escapeHtml(shortMethod(selected.methodBId))}</div>
                                </div>
                              </div>
                              <div class="tag-row">
                                <span class="tag">Match ${percent(selected.matchScore)}</span>
                                <span class="tag">Text ${percent(selected.feature.s3)}</span>
                                <span class="tag">Structure ${percent(selected.feature.s4)}</span>
                              </div>
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
                          <div>
                            <div class="label" style="margin-bottom:8px">Pick A Matched Pair</div>
                            <div class="pair-list">${pairList}</div>
                          </div>
                          ${detail}
                        </div>
                      </section>`;
                    }
                    function renderEvidence(data, files) {
                      const chain = data.stage4.evidenceChain;
                      const stage0 = data.stage0;
                      const stage1 = data.stage1;
                      const stage3 = data.stage3;
                      const stage4 = data.stage4;
                      const signalCount = chain.supportingEvidence.length + chain.opposingEvidence.length
                        + chain.scopeEvidence.length + chain.reliabilityWarnings.length;
                      const supportScore = evidenceGroupScore(chain.supportingEvidence.concat(chain.scopeEvidence));
                      const concernScore = evidenceGroupScore(chain.opposingEvidence.concat(chain.reliabilityWarnings));
                      return `<section class="section">
                        <div class="section-head">
                          <h3>Why This Result?</h3>
                          <div class="muted">The main checks behind the report</div>
                        </div>
                        <div class="evidence-hero">
                          ${ringPanel('Reasons These Files Look Related', supportScore, chain.supportingEvidence.length + chain.scopeEvidence.length + ' strong reasons found.', 'support')}
                          ${ringPanel('Reasons To Be Careful', concernScore, chain.opposingEvidence.length + chain.reliabilityWarnings.length + ' caution signs found.', 'concern')}
                        </div>
                        ${signalCount === 0 ? `<div class="evidence-panel concern" style="margin-bottom:18px">
                          <div class="label">No Strong Reasons Were Highlighted</div>
                          <div class="muted">The score may come from smaller checks that did not pass the display threshold. Developer JSON keeps the full numeric detail.</div>
                        </div>` : ''}
                        <div class="summary-columns">
                          <div class="sub-panel">
                            <div class="section-head">
                              <h3>What Was Compared?</h3>
                              <div class="muted">${inputPatternText(stage0.primaryMode)}</div>
                            </div>
                            <div class="detail-grid">
                              ${detailCard('Functions in ' + files.left.short, String(stage0.methodCountA), 'Functions found before comparison.')}
                              ${detailCard('Functions in ' + files.right.short, String(stage0.methodCountB), 'Functions found before comparison.')}
                              ${detailCard('Function Pairs Checked', String(stage1.pairMatrix.length), 'All possible function-to-function comparisons.')}
                              ${detailCard('Chosen Matches', String(stage3.mergedPairs.length), 'Pairs kept for the final report.')}
                            </div>
                          </div>
                          <div class="sub-panel">
                            <div class="section-head">
                              <h3>Main Measurements</h3>
                              <div class="muted">The numbers that shaped the result</div>
                            </div>
                            <div class="bar-list">
                              ${scoreBar('Names and tokens look alike', stage3.centroidS3)}
                              ${scoreBar('Code structure looks alike', stage3.centroidS4)}
                              ${scoreBar('Exact structure overlap', stage3.structuralExactnessAvg)}
                              ${scoreBar('Signs of real editing', stage3.tokenExactGapAvg)}
                            </div>
                          </div>
                        </div>
                        <div class="section-head" style="margin-top:22px">
                          <h3>Highlighted Reasons</h3>
                          <div class="muted">Short names here, full raw data in Developer JSON.</div>
                        </div>
                        <div class="signal-table">
                          ${signalRows(chain)}
                        </div>
                        <div class="sub-panel" style="margin-top:22px">
                          <div class="section-head">
                            <h3>Checks Used</h3>
                            <div class="muted">Useful when you want to audit the result</div>
                          </div>
                          <div class="detail-grid">
                            ${detailCard('Whole-file check', percent(stage3.s1), 'Similarity outside individual functions.')}
                            ${detailCard('Library/API check', stage3.s5Status === 'APPLICABLE' ? percent(stage3.s5) : friendlyStatus(stage3.s5Status), 'Whether shared library calls helped the decision.')}
                            ${detailCard('Exactly same after cleanup', yesNo(stage1.fileExactNormalizedMatch), 'Whether spacing and simple cleanup made the files identical.')}
                            ${detailCard('Reason strength', percent(stage4.evidenceStrength), 'How strong the displayed reasons are.')}
                            ${detailCard('Reason agreement', percent(stage4.evidenceConsistency), 'Whether the reasons point in the same direction.')}
                            ${detailCard('Checks completed', percent(stage4.pipelineReliability), 'Whether the analyzer had enough information to run its checks.')}
                          </div>
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
                    function summarySentence(summary, clone, scope) {
                      const confidence = `<strong>${percent(summary.confidence)}</strong>`;
                      if (summary.cloneType === 'NON_CLONE') {
                        return `We are ${confidence} confident that the files do not show a clear clone pattern.`;
                      }
                      return `We are ${confidence} confident that these files look like ${escapeHtml(clone.short.toLowerCase())}, and ${escapeHtml(scope.sentence)}.`;
                    }
                    function typeRankings(scores) {
                      const values = scores || {};
                      return [
                        ['T1', 'Exact clone', values.t1],
                        ['T2', 'Same code with renaming or formatting changes', values.t2],
                        ['T3', 'Copied code with real edits', values.t3],
                        ['T4_WEAK', 'Similar purpose, different code shape', values.t4Weak],
                        ['NON_CLONE', 'No clear clone', values.nonClone]
                      ].sort((a, b) => Number(b[2] || 0) - Number(a[2] || 0));
                    }
                    function scopeRankings(scores) {
                      const values = scores || {};
                      return [
                        ['FULL', 'Most of both files match', values.full],
                        ['PARTIAL', 'Only part of a file matches', values.partial],
                        ['MIXED', 'A mix of broad and partial matches', values.mixed]
                      ].sort((a, b) => Number(b[2] || 0) - Number(a[2] || 0));
                    }
                    function rankingList(items) {
                      return items.map((item, index) => rankingRow(item, index)).join('');
                    }
                    function rankingRow(item, index) {
                      const value = Number(item[2]) || 0;
                      const cls = value >= 0.75 ? 'bad' : value >= 0.4 ? 'warn' : '';
                      const dim = index > 2 ? ' dim' : '';
                      return `<div class="ranking-row${dim}">
                        <div class="ranking-top">
                          <span class="ranking-name">${escapeHtml(item[1])}</span>
                          <strong>${percent(value)}</strong>
                        </div>
                        <div class="track"><div class="fill ${cls}" style="width:${Math.max(0, Math.min(100, value * 100))}%"></div></div>
                      </div>`;
                    }
                    function ringPanel(title, value, note, cls) {
                      const n = Math.max(0, Math.min(1, Number(value) || 0));
                      return `<div class="ring-panel ${cls}" style="--ring-value:${(n * 100).toFixed(2)}%">
                        <div class="ring"><strong>${percent(n)}</strong></div>
                        <div>
                          <div class="label">${escapeHtml(title)}</div>
                          <div class="muted" style="margin-top:8px">${escapeHtml(note)}</div>
                        </div>
                      </div>`;
                    }
                    function decisionText(cloneType, scopeType) {
                      if (cloneType === 'NON_CLONE') return 'NO CLEAR SIMILARITY';
                      if (cloneType === 'T1') return 'NEARLY IDENTICAL';
                      if (cloneType === 'T2') return 'SAME CODE, RENAMED';
                      if (cloneType === 'T3') return 'SIMILAR WITH EDITS';
                      if (cloneType === 'T4_WEAK') return 'WEAK SIMILARITY';
                      if (scopeType === 'PARTIAL') return 'PARTLY SIMILAR';
                      if (scopeType === 'MIXED') return 'MIXED COVERAGE';
                      return 'BROADLY SIMILAR';
                    }
                    function statusClass(cloneType, scopeType, confidenceLevel) {
                      if (cloneType === 'NON_CLONE') return 'bad';
                      if (confidenceLevel !== 'HIGH' || scopeType === 'PARTIAL' || scopeType === 'MIXED') return 'warn';
                      return '';
                    }
                    function cloneText(type) {
                      const map = {
                        T1: ['nearly identical code', 'The two files are almost the same after cleanup.'],
                        T2: ['same code with renamed parts', 'The code shape is mostly the same; changes are likely names, formatting, or small literals.'],
                        T3: ['copied code with edits', 'The files still line up, but some statements or local logic changed.'],
                        T4_WEAK: ['similar purpose, different shape', 'The code is not structurally close, but weaker signals still suggest a relationship.'],
                        NON_CLONE: ['no clear clone', 'The current evidence is not enough to mark the files as similar.']
                      };
                      const item = map[type] || [type, 'The system produced a similarity decision.'];
                      return { short: item[0], description: item[1] };
                    }
                    function scopeText(type) {
                      const map = {
                        FULL: ['most of both files match', 'Most important functions on both sides have matching functions.', 'the overlap covers most of both files'],
                        PARTIAL: ['only part of a file matches', 'Only some functions or fragments have matching code.', 'the overlap is concentrated in part of the files'],
                        MIXED: ['a mix of broad and partial matches', 'Some evidence looks broad, but some evidence is concentrated in smaller areas.', 'the overlap is mixed across the files'],
                        NONE: ['the matching area is unclear', 'The matches do not form a stable area of similarity.', 'the matching area is unclear']
                      };
                      const item = map[type] || [type, 'The covered area comes from function matching.', 'the matching area is unclear'];
                      return { short: item[0], description: item[1], sentence: item[2] };
                    }
                    function inputPatternText(mode) {
                      const map = {
                        BCB_SINGLE_METHOD: 'One function was compared',
                        SINGLE_METHOD_REAL_FILE: 'One file mainly contains one function',
                        ONE_TO_MANY_METHOD: 'One function was compared against several functions',
                        MULTI_METHOD_BALANCED: 'Both files contain several functions',
                        CLASS_CONTEXT_HEAVY: 'The surrounding class code mattered',
                        PARSE_UNSTABLE: 'Some code could not be parsed cleanly'
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
                        ['Supports similarity', chain.supportingEvidence],
                        ['Needs review', chain.opposingEvidence],
                        ['Shows how broad it is', chain.scopeEvidence],
                        ['Check warning', chain.reliabilityWarnings]
                      ];
                      const rows = groups.flatMap(([group, items]) => items.map(item => [group, item]));
                      if (!rows.length) {
                        return '<div class="muted">No strong reasons were highlighted for this result.</div>';
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
                        structural_exactness_avg: 'The code shape is very similar',
                        method_similarity_strength: 'Matched functions look alike',
                        modification_strength: 'There are signs of editing',
                        confirmed_ratio: 'Matches work in both directions',
                        'coverage_A/B': 'Both files have matching areas',
                        S5_NOT_APPLICABLE: 'Library-call check was not useful',
                        S1_if_reliable: 'Surrounding code also looks similar',
                        partial_clone_signal: 'The overlap may be only partial',
                        match_score_avg: 'Matched functions are strong',
                        magnitude_avg: 'Overall match strength',
                        scope_confidence: 'How clear the matched area is',
                        evidence_strength: 'Reason strength',
                        pipeline_reliability: 'Checks completed'
                      };
                      return map[signal] || signal.replaceAll('_', ' ');
                    }
                    function interpretationDisplay(item) {
                      const map = {
                        structural_exactness_avg: 'Many function structures match, which suggests copied code with light changes.',
                        method_similarity_strength: 'The selected functions are similar in both text and structure.',
                        modification_strength: 'The files show edits beyond simple renaming or formatting.',
                        confirmed_ratio: 'Many matches are confirmed from both comparison directions.',
                        'coverage_A/B': 'Both files have a meaningful amount of code matched to the other file.',
                        S5_NOT_APPLICABLE: 'There were not enough shared library or API calls for this check to help.',
                        S1_if_reliable: 'Code outside individual functions also looks similar.',
                        partial_clone_signal: 'Only part of one file may correspond to the other.',
                        match_score_avg: 'The selected function matches are strong overall.',
                        magnitude_avg: 'The combined function-level checks are strong.'
                      };
                      return map[item.signal] || item.interpretation;
                    }
                    function supportsDisplay(value) {
                      const map = {
                        T1: 'Nearly identical code',
                        T2: 'Same code with renamed parts',
                        T3: 'Copied code with edits',
                        T4_WEAK: 'Similar purpose, different shape',
                        NON_CLONE: 'No clear clone',
                        FULL: 'Most of both files match',
                        PARTIAL: 'Only part of a file matches',
                        MIXED: 'Mixed coverage',
                        LOWER_CONFIDENCE: 'Lower confidence'
                      };
                      return map[value] || value;
                    }
                    function friendlyStatus(value) {
                      const map = {
                        APPLICABLE: 'Used',
                        NOT_APPLICABLE: 'Not useful here',
                        DISABLED: 'Not used',
                        SKIPPED: 'Skipped',
                        COMPUTED: 'Used'
                      };
                      return map[value] || String(value).replaceAll('_', ' ').toLowerCase();
                    }
                    function yesNo(value) {
                      return value === true || value === 'true' ? 'Yes' : 'No';
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
