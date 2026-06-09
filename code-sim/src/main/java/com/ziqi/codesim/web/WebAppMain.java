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
        server.createContext("/cfg-evidence", WebAppMain::handleCfgEvidence);
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

    private static void handleCfgEvidence(HttpExchange exchange) throws IOException {
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
        return String.join("", """
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
                      max-width: 1560px;
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
                    .mode-panel {
                      background: var(--panel);
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 14px;
                      display: grid;
                      gap: 12px;
                    }
                    .mode-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 12px;
                    }
                    .mode-option {
                      text-align: left;
                      border: 1px solid var(--line);
                      background: white;
                      color: var(--text);
                      display: grid;
                      gap: 6px;
                      min-height: 92px;
                    }
                    .mode-option.active {
                      border-color: var(--accent);
                      background: var(--accent-soft);
                      box-shadow: inset 0 0 0 1px var(--accent);
                    }
                    .mode-name {
                      font-weight: 900;
                      font-size: 15px;
                    }
                    .mode-desc {
                      color: var(--muted);
                      font-size: 13px;
                      line-height: 1.35;
                    }
                    .mode-view {
                      display: none;
                      gap: 20px;
                    }
                    .mode-choice-head {
                      display: flex;
                      justify-content: space-between;
                      align-items: flex-end;
                      gap: 18px;
                    }
                    .mode-choice-head h2 {
                      font-size: 26px;
                      margin: 0;
                    }
                    .choice-grid {
                      display: grid;
                      grid-template-columns: repeat(3, minmax(0, 1fr));
                      gap: 22px;
                    }
                    .choice-card {
                      background: var(--panel);
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 20px;
                      display: grid;
                      gap: 16px;
                      min-height: 420px;
                    }
                    .choice-card.ast {
                      border-top: 5px solid var(--left-file);
                    }
                    .choice-card.cfg {
                      border-top: 5px solid var(--accent);
                    }
                    .choice-card.next {
                      border-top: 5px solid var(--warn);
                    }
                    .choice-title {
                      display: grid;
                      gap: 6px;
                    }
                    .choice-title h3 {
                      font-size: 22px;
                    }
                    .choice-title p {
                      color: var(--muted);
                      margin: 0;
                    }
                    .choice-list {
                      display: grid;
                      gap: 10px;
                    }
                    .choice-item {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 11px;
                      background: #fbfcfd;
                    }
                    .choice-item strong {
                      display: block;
                      margin-bottom: 4px;
                    }
                    .choice-item span {
                      color: var(--muted);
                      font-size: 13px;
                    }
                    .choice-action {
                      align-self: end;
                      display: flex;
                      justify-content: flex-end;
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
                    .code-editor {
                      position: relative;
                      min-height: 640px;
                      background: #fbfcfe;
                    }
                    .code-editor pre,
                    .code-editor textarea {
                      position: absolute;
                      inset: 0;
                      margin: 0;
                      min-height: 640px;
                      padding: 18px;
                      border: 0;
                      overflow: auto;
                      white-space: pre;
                      line-height: 1.45;
                      font-family: Consolas, "Courier New", monospace;
                      font-size: 14px;
                      tab-size: 2;
                    }
                    .code-editor pre {
                      pointer-events: none;
                      color: #1f2937;
                      background: #fbfcfe;
                    }
                    .code-editor textarea {
                      color: transparent;
                      caret-color: #111827;
                      background: transparent;
                      resize: none;
                      z-index: 1;
                    }
                    .code-editor textarea::selection {
                      background: rgba(37, 99, 235, .20);
                      color: transparent;
                    }
                    .tok-keyword { color: #2563eb; font-weight: 800; }
                    .tok-type { color: #0f766e; font-weight: 700; }
                    .tok-string { color: #a16207; }
                    .tok-number { color: #7c3aed; }
                    .tok-comment { color: #64748b; font-style: italic; }
                    .report-view {
                      display: none;
                      gap: 18px;
                    }
                    .report-shell {
                      display: grid;
                      gap: 16px;
                    }
                    .report-head {
                      display: flex;
                      justify-content: flex-start;
                      align-items: center;
                      gap: 16px;
                      margin-bottom: 12px;
                    }
                    .report-title {
                      display: flex;
                      align-items: center;
                      gap: 16px;
                      flex-wrap: wrap;
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
                      background: transparent;
                      border: 0;
                      padding: 0;
                      display: flex;
                      gap: 22px;
                      align-items: center;
                      flex-wrap: wrap;
                      border-bottom: 1px solid var(--line);
                      padding-bottom: 10px;
                    }
                    .nav-button {
                      width: auto;
                      border: 0;
                      border-radius: 0;
                      background: transparent;
                      color: var(--muted);
                      text-align: center;
                      padding: 6px 0;
                      font-weight: 900;
                      position: relative;
                    }
                    .nav-button:hover {
                      background: transparent;
                      color: var(--accent);
                    }
                    .nav-button.active {
                      background: transparent;
                      color: var(--accent);
                    }
                    .nav-button.active::after {
                      content: "";
                      position: absolute;
                      left: 0;
                      right: 0;
                      bottom: -11px;
                      height: 3px;
                      border-radius: 999px;
                      background: var(--accent);
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
                    .next-affected-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(280px, 1fr));
                      gap: 18px;
                      align-items: stretch;
                    }
                    .next-donut-card {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 20px;
                      background: #fbfcfd;
                      display: grid;
                      grid-template-columns: 150px minmax(0, 1fr);
                      gap: 20px;
                      align-items: center;
                    }
                    .next-donut-card.severity-high {
                      border-color: #f1a2a2;
                      background: #fff6f6;
                    }
                    .next-donut-card.severity-mid {
                      border-color: #e3b76c;
                      background: #fff8ea;
                    }
                    .next-donut {
                      --progress: var(--value);
                      --severity: var(--accent);
                      width: 148px;
                      aspect-ratio: 1;
                      border-radius: 50%;
                      display: grid;
                      place-items: center;
                      background:
                        radial-gradient(circle closest-side, #fbfcfd 68%, transparent 69%),
                        conic-gradient(var(--severity) calc(var(--progress) * 1%), #e6ebf0 0);
                      animation: next-donut-fill 900ms cubic-bezier(.2, .8, .2, 1) forwards;
                    }
                    .next-donut.severity-high { --severity: var(--bad); }
                    .next-donut.severity-mid { --severity: var(--warn); }
                    .next-donut.severity-low { --severity: var(--accent); }
                    .next-donut strong {
                      font-size: 28px;
                      color: var(--severity);
                    }
                    .severity-status {
                      font-size: 13px;
                      font-weight: 900;
                      text-transform: uppercase;
                    }
                    .severity-status.severity-high { color: var(--bad); }
                    .severity-status.severity-mid { color: var(--warn); }
                    .severity-status.severity-low { color: var(--accent); }
                    @property --progress {
                      syntax: '<number>';
                      inherits: false;
                      initial-value: 0;
                    }
                    @keyframes next-donut-fill {
                      from { --progress: 0; }
                      to { --progress: var(--value); }
                    }
                    .next-file-name {
                      font-size: 22px;
                      font-weight: 900;
                      overflow-wrap: anywhere;
                    }
                    .next-type-summary {
                      margin-top: 18px;
                    }
                    .next-type-summary .section-head {
                      margin-bottom: 10px;
                    }
                    .next-breakdown {
                      display: grid;
                      gap: 10px;
                    }
                    .next-breakdown-row {
                      display: grid;
                      grid-template-columns: 82px minmax(0, 1fr) 132px;
                      gap: 12px;
                      align-items: center;
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 12px;
                      background: #fbfcfd;
                      color: var(--text);
                      text-align: left;
                      width: 100%;
                      cursor: pointer;
                    }
                    .next-breakdown-row:hover {
                      border-color: var(--accent);
                      background: var(--accent-soft);
                    }
                    .next-type {
                      display: inline-flex;
                      justify-content: center;
                      border-radius: 999px;
                      padding: 7px 10px;
                      color: white;
                      font-weight: 900;
                      font-size: 13px;
                    }
                    .next-type.t1 { background: #2f7d55; }
                    .next-type.t2 { background: #3e66c4; }
                    .next-type.t3 { background: var(--warn); color: #3a2b12; }
                    .next-type.t4 { background: var(--bad); }
                    .next-type-text {
                      font-weight: 900;
                    }
                    .next-region-layout {
                      display: grid;
                      grid-template-columns: 1fr;
                      gap: 16px;
                    }
                    .next-region-list {
                      display: grid;
                      grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
                      gap: 10px;
                    }
                    .next-region-card {
                      text-align: left;
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 13px;
                      background: white;
                      display: grid;
                      gap: 6px;
                    }
                    .next-region-card.active {
                      border-color: var(--accent);
                      background: var(--accent-soft);
                    }
                    .next-region-card .muted {
                      font-size: 13px;
                      line-height: 1.35;
                    }
                    .next-region-detail {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 16px;
                      background: #fbfcfd;
                    }
                    .next-code-filter {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      gap: 12px;
                      margin-bottom: 14px;
                      flex-wrap: wrap;
                    }
                    .next-type-buttons {
                      display: flex;
                      gap: 8px;
                      flex-wrap: wrap;
                    }
                    .next-type-button {
                      border: 1px solid var(--line);
                      background: white;
                      color: var(--muted);
                      padding: 8px 11px;
                      border-radius: 999px;
                      font-weight: 900;
                    }
                    .next-type-button.active {
                      border-color: var(--accent);
                      background: var(--accent-soft);
                      color: var(--accent);
                    }
                    .next-type-button.t1 {
                      border-color: #2f7d55;
                      color: #2f7d55;
                    }
                    .next-type-button.t1.active {
                      background: #e8f6ef;
                    }
                    .next-type-button.t2 {
                      border-color: #3e66c4;
                      color: #244da8;
                    }
                    .next-type-button.t2.active {
                      background: #eef3ff;
                    }
                    .next-type-button.t3 {
                      border-color: var(--warn);
                      color: #8a5a14;
                    }
                    .next-type-button.t3.active {
                      background: #fff2d9;
                    }
                    .next-code-line {
                      cursor: pointer;
                    }
                    .next-code-line.region .line-code {
                      background: transparent;
                    }
                    .next-code-line.visible-region .line-code {
                      background: #fff2d9;
                      box-shadow: inset 4px 0 0 var(--warn);
                    }
                    .next-code-line.visible-region.t1 .line-code {
                      background: #e8f6ef;
                      box-shadow: inset 4px 0 0 #2f7d55;
                    }
                    .next-code-line.visible-region.t2 .line-code {
                      background: #eef3ff;
                      box-shadow: inset 4px 0 0 #3e66c4;
                    }
                    .next-code-line.visible-region.t3 .line-code {
                      background: #fff2d9;
                      box-shadow: inset 4px 0 0 var(--warn);
                    }
                    .next-code-line.peer-focus .line-code {
                      box-shadow: inset 4px 0 0 var(--accent), inset 0 1px 0 var(--accent), inset 0 -1px 0 var(--accent);
                      background: #e7f6f4;
                    }
                    .next-code-line.region-start .line-code {
                      position: relative;
                      padding-top: 20px;
                    }
                    .next-code-line.region-start .line-code::before {
                      content: attr(data-region-label);
                      position: absolute;
                      top: 2px;
                      left: 12px;
                      border-radius: 999px;
                      padding: 2px 7px;
                      background: var(--accent);
                      color: white;
                      font-family: Arial, Helvetica, sans-serif;
                      font-size: 10px;
                      font-weight: 900;
                      letter-spacing: 0;
                    }
                    .next-code-line.region-start.t1 .line-code::before {
                      background: #2f7d55;
                    }
                    .next-code-line.region-start.t2 .line-code::before {
                      background: #3e66c4;
                    }
                    .next-code-line.region-start.t3 .line-code::before {
                      background: var(--warn);
                      color: #3a2b12;
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
                      display: grid;
                      gap: 8px;
                    }
                    .summary-decision {
                      font-size: 36px;
                      line-height: 1.05;
                      font-weight: 800;
                    }
                    .summary-sentence {
                      color: var(--muted);
                      font-size: 19px;
                      line-height: 1.35;
                      font-weight: 700;
                    }
                    .summary-sentence strong {
                      color: var(--accent);
                    }
                    .summary-sentence.warn strong {
                      color: var(--warn);
                    }
                    .summary-sentence.bad strong {
                      color: var(--bad);
                    }
                    .focus-grid {
                      display: grid;
                      grid-template-columns: repeat(3, minmax(0, 1fr));
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
                    """, """
                    .evidence-board {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 12px;
                    }
                    .evidence-hero {
                      display: block;
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
                    .breakdown-panel {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 20px;
                      background: #fbfcfd;
                      display: grid;
                      grid-template-columns: 156px minmax(0, 1fr);
                      gap: 24px;
                      align-items: center;
                    }
                    .reason-donut {
                      width: 136px;
                      height: 136px;
                      border-radius: 50%;
                      display: grid;
                      place-items: center;
                      background: var(--donut);
                    }
                    .reason-center {
                      width: 88px;
                      height: 88px;
                      border-radius: 50%;
                      background: #fbfcfd;
                      display: grid;
                      place-items: center;
                      text-align: center;
                      line-height: 1.1;
                    }
                    .reason-center strong {
                      display: block;
                      font-size: 30px;
                      line-height: 1;
                    }
                    .reason-center span {
                      color: var(--muted);
                      font-size: 12px;
                      font-weight: 700;
                    }
                    .legend-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 10px;
                      margin-top: 14px;
                    }
                    .legend-row {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 10px;
                      background: white;
                      font-size: 13px;
                    }
                    .legend-head {
                      display: grid;
                      grid-template-columns: 12px minmax(0, 1fr) auto;
                      gap: 9px;
                      align-items: center;
                    }
                    .legend-dot {
                      width: 10px;
                      height: 10px;
                      border-radius: 50%;
                      background: var(--dot-color);
                    }
                    .reason-mini-list {
                      display: grid;
                      gap: 6px;
                      margin-top: 9px;
                      padding-top: 8px;
                      border-top: 1px solid #edf0f4;
                    }
                    .reason-mini {
                      display: grid;
                      grid-template-columns: minmax(0, 1fr) auto;
                      gap: 8px;
                      align-items: start;
                      color: var(--muted);
                      line-height: 1.3;
                    }
                    .reason-mini-main {
                      min-width: 0;
                    }
                    .reason-mini-name {
                      display: block;
                      color: var(--text);
                      font-weight: 700;
                      overflow: hidden;
                      text-overflow: ellipsis;
                      white-space: nowrap;
                    }
                    .reason-mini-score {
                      display: block;
                      margin-top: 2px;
                      font-size: 12px;
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
                    .signal-pill.note {
                      background: #eef2f6;
                      color: #4b5563;
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
                    .cfg-page {
                      display: grid;
                      gap: 18px;
                    }
                    .cfg-hero {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 18px;
                      display: grid;
                      grid-template-columns: minmax(0, 1fr) 145px 190px 145px;
                      gap: 14px;
                      background: white;
                    }
                    .cfg-statement {
                      display: grid;
                      gap: 8px;
                    }
                    .cfg-statement h3 {
                      font-size: 18px;
                      margin: 0;
                    }
                    .cfg-statement p {
                      color: var(--muted);
                      font-size: 14px;
                      margin: 0;
                    }
                    .cfg-metric {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 12px;
                      background: #fbfcfe;
                    }
                    .cfg-metric strong {
                      display: block;
                      font-size: 18px;
                      margin-top: 5px;
                      overflow-wrap: anywhere;
                    }
                    .cfg-method-row {
                      display: grid;
                      grid-template-columns: minmax(0, 1fr) 78px;
                      gap: 12px;
                      align-items: center;
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 12px;
                      margin-bottom: 10px;
                      background: white;
                      color: var(--text);
                      text-align: left;
                    }
                    .cfg-method-strip {
                      display: grid;
                      grid-template-columns: repeat(3, minmax(0, 1fr));
                      gap: 10px;
                    }
                    .cfg-method-row.active {
                      border-color: var(--accent);
                      background: var(--accent-soft);
                    }
                    .cfg-method-title {
                      display: flex;
                      align-items: center;
                      gap: 8px;
                      margin-bottom: 6px;
                      font-weight: 900;
                    }
                    .cfg-method {
                      font-family: Consolas, "Courier New", monospace;
                      font-size: 11px;
                      overflow-wrap: anywhere;
                      color: var(--muted);
                    }
                    .cfg-method-score {
                      font-size: 18px;
                      font-weight: 900;
                      color: var(--accent);
                      text-align: right;
                    }
                    .cfg-category-grid {
                      display: grid;
                      grid-template-columns: repeat(3, minmax(0, 1fr));
                      gap: 16px;
                    }
                    .cfg-category-card {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      background: white;
                      padding: 16px;
                      min-height: 430px;
                      display: grid;
                      grid-template-rows: auto auto 1fr;
                      gap: 14px;
                    }
                    .cfg-category-card.primary {
                      border-top: 5px solid var(--accent);
                    }
                    .cfg-category-card.low {
                      border-top: 5px solid #9aa7b7;
                    }
                    .cfg-category-card.partial {
                      border-top: 5px solid #d49535;
                    }
                    .cfg-category-head {
                      display: grid;
                      gap: 6px;
                    }
                    .cfg-category-head h3 {
                      font-size: 18px;
                    }
                    .cfg-category-head p {
                      color: var(--muted);
                      margin: 0;
                      line-height: 1.45;
                    }
                    .cfg-pair-list {
                      display: grid;
                      gap: 10px;
                      align-content: start;
                    }
                    .cfg-pair-card {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      background: #fbfcfe;
                      color: var(--text);
                      padding: 12px;
                      text-align: left;
                      display: grid;
                      gap: 8px;
                    }
                    .cfg-pair-card:hover {
                      border-color: var(--accent);
                    }
                    .cfg-pair-title {
                      display: flex;
                      justify-content: space-between;
                      gap: 10px;
                      align-items: center;
                      font-weight: 900;
                    }
                    .cfg-pair-code {
                      color: var(--muted);
                      font-family: Consolas, "Courier New", monospace;
                      font-size: 12px;
                      overflow-wrap: anywhere;
                      line-height: 1.45;
                    }
                    .cfg-empty {
                      border: 1px dashed var(--line);
                      border-radius: 8px;
                      padding: 14px;
                      color: var(--muted);
                      background: #fbfcfe;
                    }
                    .cfg-detail-head {
                      display: grid;
                      grid-template-columns: minmax(0, 1fr) auto;
                      gap: 12px;
                      align-items: center;
                      margin-bottom: 10px;
                    }
                    .cfg-detail-title {
                      display: flex;
                      gap: 10px;
                      align-items: center;
                      flex-wrap: wrap;
                      min-width: 0;
                    }
                    .cfg-detail-title > .secondary {
                      width: fit-content;
                      padding: 6px 10px;
                    }
                    .cfg-detail-main {
                      min-width: 260px;
                      flex: 1;
                    }
                    .cfg-detail-main h3 {
                      font-size: 18px;
                      line-height: 1.15;
                    }
                    .cfg-detail-main .muted {
                      font-size: 12px;
                      white-space: nowrap;
                      overflow: hidden;
                      text-overflow: ellipsis;
                    }
                    .cfg-category-tag {
                      border-radius: 999px;
                      padding: 6px 10px;
                      font-size: 12px;
                      font-weight: 900;
                      width: fit-content;
                    }
                    .cfg-category-tag.primary {
                      border: 1px solid var(--accent);
                      background: var(--accent-soft);
                      color: var(--accent);
                    }
                    .cfg-category-tag.low {
                      border: 1px solid #9aa7b7;
                      background: #f4f6f9;
                      color: #526071;
                    }
                    .cfg-category-tag.partial {
                      border: 1px solid #e3b76c;
                      background: var(--warn-soft);
                      color: #8a5a14;
                    }
                    .cfg-channel-grid {
                      display: grid;
                      grid-template-columns: repeat(3, minmax(0, 1fr));
                      gap: 12px;
                    }
                    .cfg-channel {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 13px;
                      background: white;
                      display: grid;
                      gap: 8px;
                    }
                    .cfg-channel strong {
                      font-size: 16px;
                    }
                    .cfg-track {
                      height: 8px;
                      background: #e7ebf0;
                      border-radius: 999px;
                      overflow: hidden;
                    }
                    .cfg-track span {
                      display: block;
                      height: 100%;
                      background: var(--accent);
                      border-radius: inherit;
                    }
                    .cfg-visual {
                      display: grid;
                      grid-template-columns: minmax(0, 1fr);
                      gap: 14px;
                      align-items: stretch;
                    }
                    .cfg-visual.with-inspector {
                      grid-template-columns: minmax(0, 1fr) 270px;
                    }
                    .cfg-method-panel {
                      grid-column: 1 / -1;
                    }
                    .cfg-explorer-main {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      background: #fbfcfe;
                      padding: 12px;
                      min-width: 0;
                    }
                    .cfg-explorer-main.graph-only {
                      background: white;
                    }
                    .cfg-story {
                      border: 1px solid #91c6c0;
                      border-radius: 8px;
                      background: var(--accent-soft);
                      padding: 8px 10px;
                      margin-bottom: 8px;
                      display: flex;
                      justify-content: space-between;
                      gap: 10px;
                      align-items: center;
                    }
                    .cfg-story strong {
                      display: inline;
                      margin-right: 8px;
                      font-size: 13px;
                    }
                    .cfg-story span {
                      color: var(--muted);
                      font-size: 12px;
                    }
                    .cfg-explorer-toolbar {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      gap: 12px;
                      margin-bottom: 12px;
                      flex-wrap: wrap;
                    }
                    .cfg-toggle-group {
                      display: flex;
                      gap: 8px;
                      flex-wrap: wrap;
                    }
                    .cfg-toggle {
                      border-color: var(--line);
                      background: white;
                      color: var(--muted);
                      padding: 7px 9px;
                      font-size: 12px;
                    }
                    .cfg-toggle.active {
                      background: var(--accent-soft);
                      color: var(--accent);
                      border-color: var(--accent);
                    }
                    .cfg-page-note {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      background: #fbfcfd;
                      padding: 12px 14px;
                      margin-bottom: 14px;
                    }
                    .cfg-page-note strong {
                      margin-right: 8px;
                    }
                    .cfg-page-actions {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      gap: 10px;
                      flex-wrap: wrap;
                      margin-bottom: 12px;
                    }
                    .cfg-tree-grid {
                      display: grid;
                      grid-template-columns: minmax(220px, 1fr) 120px minmax(220px, 1fr);
                      gap: 14px;
                      align-items: start;
                    }
                    .cfg-graph-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(330px, 1fr));
                      gap: 14px;
                      align-items: stretch;
                    }
                    .cfg-graph-card {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 10px;
                      background: white;
                      display: grid;
                      grid-template-rows: auto 1fr;
                      gap: 10px;
                      min-width: 0;
                    }
                    .cfg-graph-canvas {
                      position: relative;
                      min-height: 740px;
                      border: 1px solid #e4e9ef;
                      border-radius: 8px;
                      background: #fbfcfe;
                      overflow: hidden;
                    }
                    .cfg-edge-layer {
                      position: absolute;
                      inset: 0;
                      width: 100%;
                      height: 100%;
                      pointer-events: none;
                      z-index: 1;
                    }
                    .cfg-edge {
                      stroke: #64748b;
                      stroke-width: .96;
                      stroke-linecap: round;
                      fill: none;
                      opacity: 0.88;
                    }
                    .cfg-edge.loop {
                      stroke: #c47a1c;
                      stroke-width: .62;
                      stroke-dasharray: 4 7;
                      opacity: .58;
                    }
                    .cfg-graph-canvas .cfg-node {
                      position: absolute;
                      width: 126px;
                      min-height: 58px;
                      transform: translate(-50%, -50%);
                      z-index: 2;
                    }
                    .cfg-graph-canvas .cfg-node::after {
                      display: none;
                    }
                    .cfg-match-panel {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 10px;
                      background: white;
                      display: grid;
                      gap: 8px;
                      align-content: start;
                      min-width: 0;
                      margin-bottom: 12px;
                    }
                    .cfg-match-panel h4 {
                      font-size: 12px;
                      margin: 0;
                    }
                    .cfg-match-panel .cfg-links {
                      padding-top: 0;
                      gap: 8px;
                      display: grid;
                      grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
                    }
                    .cfg-match-panel .cfg-links span {
                      border-top: 0;
                      border-left: 2px solid var(--accent);
                      border-radius: 6px;
                      background: var(--accent-soft);
                      padding: 5px 7px;
                      text-align: left;
                      color: var(--text);
                      font-size: 10px;
                    }
                    .cfg-match-panel .cfg-links span strong {
                      display: block;
                      color: var(--accent);
                      font-size: 10px;
                      margin-bottom: 1px;
                    }
                    .cfg-match-panel .cfg-links span small {
                      color: var(--muted);
                      font-weight: 700;
                      font-size: 10px;
                    }
                    .cfg-column {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 14px;
                      background: white;
                      display: grid;
                      gap: 10px;
                    }
                    .cfg-node {
                      border: 1px solid #aacdc9;
                      background: var(--accent-soft);
                      border-radius: 8px;
                      padding: 8px;
                      min-height: 48px;
                      position: relative;
                      cursor: pointer;
                      color: var(--text);
                      text-align: left;
                    }
                    .cfg-node:not(:last-child)::after {
                      content: "";
                      position: absolute;
                      left: 50%;
                      bottom: -12px;
                      width: 2px;
                      height: 12px;
                      background: #9aa7b7;
                    }
                    .cfg-node.branch {
                      border-color: #e3b76c;
                      background: var(--warn-soft);
                    }
                    .cfg-node.helper {
                      border-color: #91c6c0;
                      background: var(--accent-soft);
                    }
                    .cfg-node.return {
                      border-color: #b7c2d0;
                      background: #f4f6f9;
                    }
                    .cfg-node.active {
                      box-shadow: 0 0 0 2px var(--accent);
                    }
                    .cfg-node.dim {
                      color: #8a95a3;
                      border-color: #d7dee7;
                      background: #f8fafc;
                    }
                    .cfg-node.dim strong,
                    .cfg-node.dim span {
                      color: #9aa7b7;
                    }
                    .cfg-node strong {
                      display: block;
                      font-size: 12px;
                      color: var(--text);
                    }
                    .cfg-node span {
                      color: var(--muted);
                      font-size: 11px;
                      line-height: 1.25;
                    }
                    .cfg-links {
                      display: grid;
                      gap: 28px;
                      color: var(--accent);
                      font-weight: 900;
                      text-align: center;
                      padding-top: 52px;
                    }
                    .cfg-links span {
                      border-top: 2px dashed var(--accent);
                      padding-top: 6px;
                      font-size: 12px;
                      line-height: 1.25;
                    }
                    .cfg-links.hide-distance span b {
                      display: none;
                    }
                    .cfg-tree-title {
                      display: flex;
                      align-items: center;
                      justify-content: space-between;
                      gap: 10px;
                      margin-bottom: 2px;
                    }
                    .cfg-tree-title strong {
                      font-size: 14px;
                    }
                    .cfg-inspector {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      background: white;
                      padding: 12px;
                      display: grid;
                      gap: 14px;
                      align-self: stretch;
                      min-width: 0;
                    }
                    .cfg-inspector > div:first-child,
                    .cfg-inspector .cfg-raw {
                      grid-column: auto;
                    }
                    .cfg-inspector h3 {
                      font-size: 16px;
                    }
                    .cfg-kv {
                      display: grid;
                      gap: 4px;
                      font-size: 13px;
                    }
                    .cfg-kv span:first-child {
                      color: var(--muted);
                      font-weight: 800;
                    }
                    .cfg-raw {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 10px;
                      background: #111827;
                      color: #e5e7eb;
                      font-family: Consolas, "Courier New", monospace;
                      font-size: 12px;
                      line-height: 1.4;
                      white-space: pre-wrap;
                    }
                    .cfg-note-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 14px;
                    }
                    .cfg-note {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 14px;
                      background: white;
                    }
                    """, """
                    .cfg-note.warn {
                      border-color: #e3b76c;
                      background: var(--warn-soft);
                    }
                    .cfg-note.good {
                      border-color: #91c6c0;
                      background: var(--accent-soft);
                    }
                    .hidden {
                      display: none;
                    }
                    @media (max-width: 980px) {
                      .editor-grid,
                      .mode-grid,
                      .report-shell,
                      .method-layout,
                      .code-grid,
                      .focus-grid,
                      .summary-columns,
                      .metric-grid,
                      .evidence-board,
                      .evidence-hero,
                      .detail-grid,
                      .cfg-hero,
                      .cfg-category-grid,
                      .cfg-channel-grid,
                      .cfg-visual,
                      .cfg-graph-grid,
                      .cfg-tree-grid,
                      .cfg-note-grid {
                        grid-template-columns: 1fr;
                      }
                      .next-affected-grid,
                      .next-region-layout,
                      .next-breakdown-row {
                        grid-template-columns: 1fr;
                      }
                      .cfg-links {
                        padding-top: 0;
                        gap: 10px;
                      }
                      .signal-row {
                        grid-template-columns: 1fr;
                      }
                      .breakdown-panel {
                        grid-template-columns: 1fr;
                      }
                      .legend-grid {
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
                      .code-editor,
                      .code-editor pre,
                      .code-editor textarea {
                        min-height: 360px;
                      }
                    }
                  </style>
                </head>
                <body>
                  <header>
                    <div class="topbar">
                      <h1>Code Similarity Analyzer</h1>
                      <div class="actions hidden" id="reportActions">
                        <button class="secondary" id="modeBtn" type="button">Back to Modes</button>
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
                          <div class="code-editor">
                            <pre id="highlightA" aria-hidden="true"></pre>
                            <textarea id="sourceA" spellcheck="false" aria-label="Source 1"></textarea>
                          </div>
                        </div>
                        <div class="panel">
                          <div class="panel-head"><input id="fileB" value="B2.java" aria-label="File 2 name"></div>
                          <div class="code-editor">
                            <pre id="highlightB" aria-hidden="true"></pre>
                            <textarea id="sourceB" spellcheck="false" aria-label="Source 2"></textarea>
                          </div>
                        </div>
                      </div>
                      <div class="input-footer">
                        <div class="muted">Use the sample to preview a multi-method comparison.</div>
                        <div class="actions" id="inputActions">
                          <button class="secondary" id="sampleBtn" type="button">Load Sample</button>
                          <button id="chooseModeBtn" type="button">Continue</button>
                        </div>
                      </div>
                    </section>
                    <section class="mode-view" id="modeView">
                      <div class="mode-choice-head">
                        <div class="input-title">
                          <h2>Choose An Analysis Style</h2>
                          <div class="muted">The files are ready. Pick the view that best matches what you want to understand.</div>
                        </div>
                        <button class="secondary" id="editCodeBtn" type="button">Edit Code</button>
                      </div>
                      <div class="choice-grid">
                        <article class="choice-card ast">
                          <div class="choice-title">
                            <h3>AST / Source Structure</h3>
                            <p>Best for a quick clone-type report based on source layout, tokens, and method-level matching.</p>
                          </div>
                          <div class="choice-list">
                            <div class="choice-item"><strong>Good for</strong><span>Formatting changes, renaming, inserted/deleted statements, and broad clone-type classification.</span></div>
                            <div class="choice-item"><strong>Output style</strong><span>General summary, matched methods, evidence reasons, and developer JSON.</span></div>
                            <div class="choice-item"><strong>Watch for</strong><span>Heavier structural edits or helper extraction can need deeper evidence.</span></div>
                          </div>
                          <div class="choice-action"><button id="runAstBtn" type="button">Run AST Analysis</button></div>
                        </article>
                        <article class="choice-card cfg">
                          <div class="choice-title">
                            <h3>CFG / Execution Structure</h3>
                            <p>Best for inspecting how methods execute: control-flow blocks, method ranking, and CFG evidence.</p>
                          </div>
                          <div class="choice-list">
                            <div class="choice-item"><strong>Good for</strong><span>Renamed methods, structural similarity after edits, and explaining which control-flow regions line up.</span></div>
                            <div class="choice-item"><strong>Output style</strong><span>Method ranking, CFG tree view, block alignment, and evidence channels.</span></div>
                            <div class="choice-item"><strong>Watch for</strong><span>Deep call chains or same-looking APIs may need semantic or inter-procedural checks later.</span></div>
                          </div>
                          <div class="choice-action"><button id="runCfgBtn" type="button">Run CFG Analysis</button></div>
                        </article>
                        <article class="choice-card next">
                          <div class="choice-title">
                            <h3>Evidence-first Workbench</h3>
                            <p>Best for showing affected content, region evidence, clickable code ranges, and CFG details without forcing one file-level label.</p>
                          </div>
                          <div class="choice-list">
                            <div class="choice-item"><strong>Good for</strong><span>Letting users inspect how much code is suspicious, where it appears, and what kind of evidence supports each region.</span></div>
                            <div class="choice-item"><strong>Output style</strong><span>Affected content, evidence breakdown, suspicious regions, inline source, CFG graph, and JSON evidence.</span></div>
                            <div class="choice-item"><strong>Watch for</strong><span>The report gives evidence; the user decides whether the file pair should be treated as a clone.</span></div>
                          </div>
                          <div class="choice-action"><button id="runNextBtn" type="button">Open Evidence Workbench</button></div>
                        </article>
                      </div>
                    </section>
                    <section class="report-view" id="reportView">
                      <div class="report-head">
                        <div class="report-title">
                          <h2 id="reportHeading">Similarity Report</h2>
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
                    const highlightA = document.querySelector('#highlightA');
                    const highlightB = document.querySelector('#highlightB');
                    const fileA = document.querySelector('#fileA');
                    const fileB = document.querySelector('#fileB');
                    const inputView = document.querySelector('#inputView');
                    const modeView = document.querySelector('#modeView');
                    const reportView = document.querySelector('#reportView');
                    const inputActions = document.querySelector('#inputActions');
                    const reportActions = document.querySelector('#reportActions');
                    const sectionNav = document.querySelector('#sectionNav');
                    const sectionContent = document.querySelector('#sectionContent');
                    const reportCompare = document.querySelector('#reportCompare');
                    const reportHeading = document.querySelector('#reportHeading');
                    let reportData = null;
                    let reportMode = 'ast';
                    let analysisMode = 'ast';
                    let activeSection = 'summary';
                    let selectedPairIndex = 0;
                    let cfgDetailOpen = false;
                    let selectedCfgNode = 'L2';
                    let selectedNextRegion = 'main-flow';
                    let selectedNextType = 'ALL';
                    let shouldScrollNextRegion = false;
                    let nextScrollTargetRegion = '';
                    let showCfgDistance = false;
                    let showCfgMatches = false;
                    let showCfgRaw = false;
                    let showCfgLoops = false;
                    let focusCfgMatch = false;
                    let cfgSubView = 'graph';
                    let fileAEdited = false;
                    let fileBEdited = false;
                    const astSections = [
                      ['summary', 'General Summary'],
                      ['methods', 'Matched Methods'],
                      ['evidence', 'Why This Result?'],
                      ['json', 'Developer JSON']
                    ];
                    const cfgSections = [
                      ['summary', 'Evidence Summary'],
                      ['explorer', 'CFG Explorer'],
                      ['json', 'Evidence JSON']
                    ];
                    const nextSections = [
                      ['summary', 'Result Summary'],
                      ['source', 'Code Regions'],
                      ['cfg', 'CFG Explorer'],
                      ['json', 'Evidence JSON']
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
                    const cfgSampleA = `class InlineGuard {
                    int countValid(int[] values) {
                        if (values == null || values.length == 0) {
                            return 0;
                        }
                        int count = 0;
                        for (int value : values) {
                            if (value >= 10 && value <= 99) {
                                count++;
                            }
                        }
                        return count;
                    }
                }`;
                    const cfgSampleB = `class HelperGuard {
                    int countValid(int[] values) {
                        if (values == null || values.length == 0) {
                            return 0;
                        }
                        int count = 0;
                        for (int value : values) {
                            if (isValid(value)) {
                                count++;
                            }
                        }
                        return count;
                    }

                    private boolean isValid(int value) {
                        return value >= 10 && value <= 99;
                    }
                }`;
                    const nextSampleA = `class InlineGuardLong {
                    int evaluateOrderRisk(int[] quantities, String[] emails, String country, String coupon, int limit, int stock, String channel, String device) {
                        int score = 0;
                        if (quantities == null || emails == null) {
                            return 100;
                        }
                        if (stock <= 0) {
                            score += 20;
                        } else if (stock < quantities.length) {
                            score += 11;
                        }
                        score += quantityRisk(quantities, limit);
                        score += inventoryRisk(quantities, stock);
                        score += emailRisk(emails);
                        score += regionRisk(country);
                        score += couponRisk(coupon);
                        score += channelRisk(channel, device);
                        score += stabilityRisk(quantities, emails);
                        score += fallbackRisk(score, channel);
                        if (score < 0) {
                            score = 0;
                        }
                        if (score > 100) {
                            score = 100;
                        }
                        return capScore(score);
                    }

                    private int quantityRisk(int[] quantities, int limit) {
                        int risk = 0;
                        int seen = 0;
                        for (int quantity : quantities) {
                            seen++;
                            if (quantity <= 0) {
                                risk += 9;
                            } else if (quantity > limit) {
                                risk += 18;
                            } else if (quantity > limit / 2) {
                                risk += 7;
                            } else {
                                risk += 1;
                            }
                            if (seen > 40) {
                                risk += 4;
                            }
                        }
                        return risk;
                    }

                    private int inventoryRisk(int[] quantities, int stock) {
                        int risk = 0;
                        int requested = 0;
                        for (int quantity : quantities) {
                            requested += Math.max(0, quantity);
                            if (requested > stock) {
                                risk += 13;
                                break;
                            }
                            if (stock - requested < 3) {
                                risk += 4;
                            }
                        }
                        if (requested == 0) {
                            risk += 6;
                        }
                        return risk;
                    }

                    private int emailRisk(String[] emails) {
                        int risk = 0;
                        for (String email : emails) {
                            if (email == null || email.isBlank()) {
                                risk += 8;
                            } else if (email.endsWith("@temp.example")) {
                                risk += 16;
                            } else if (email.contains("+promo")) {
                                risk += 5;
                            } else {
                                risk += 1;
                            }
                        }
                        return risk;
                    }

                    private int regionRisk(String country) {
                        if (country == null) {
                            return 11;
                        }
                        String clean = country.trim().toUpperCase();
                        if (clean.equals("CN") || clean.equals("BR")) {
                            return 18;
                        }
                        if (clean.equals("IN")) {
                            return 9;
                        }
                        if (clean.length() == 0) {
                            return 7;
                        }
                        return 2;
                    }

                    private int couponRisk(String coupon) {
                        if (coupon == null) {
                            return 3;
                        }
                        String code = coupon.trim().toLowerCase();
                        int risk = 0;
                        if (code.startsWith("free")) {
                            risk += 12;
                        }
                        if (code.contains("vip")) {
                            risk -= 3;
                        }
                        if (code.length() > 18) {
                            risk += 6;
                        }
                        return risk;
                    }

                    private int channelRisk(String channel, String device) {
                        int risk = 0;
                        String source = channel == null ? "unknown" : channel.trim().toLowerCase();
                        String client = device == null ? "unknown" : device.trim().toLowerCase();
                        if (source.equals("marketplace")) {
                            risk += 7;
                            if (client.contains("bot")) {
                                risk += 19;
                            }
                        } else if (source.equals("mobile")) {
                            risk += client.contains("root") ? 12 : 2;
                        } else if (source.equals("store")) {
                            risk -= 2;
                        } else {
                            risk += 5;
                        }
                        return risk;
                    }

                    private int stabilityRisk(int[] quantities, String[] emails) {
                        int risk = 0;
                        int repeated = 0;
                        int previous = Integer.MIN_VALUE;
                        for (int quantity : quantities) {
                            if (quantity == previous) {
                                repeated++;
                            }
                            previous = quantity;
                        }
                        if (repeated > 5) {
                            risk += 10;
                        }
                        int empty = 0;
                        for (String email : emails) {
                            if (email == null || email.isBlank()) {
                                empty++;
                            }
                        }
                        if (empty > emails.length / 2) {
                            risk += 14;
                        }
                        return risk;
                    }

                    private int fallbackRisk(int score, String channel) {
                        if (score > 80) {
                            return 6;
                        }
                        if (channel == null || channel.isBlank()) {
                            return 4;
                        }
                        if (channel.equalsIgnoreCase("store")) {
                            return -2;
                        }
                        return 0;
                    }

                    private int capScore(int score) {
                        if (score < 0) {
                            return 0;
                        }
                        return Math.min(score, 100);
                    }
                }`;
                    const nextSampleB = `class HelperGuardLong {
                    int calculateRisk(int[] items, String[] contacts, String region, String promotion, int cap, int available, String source, String client) {
                        int total = 0;
                        if (items == null || contacts == null) {
                            return 100;
                        }
                        if (available <= 0) {
                            total += 20;
                        } else if (available < items.length) {
                            total += 11;
                        }
                        total += itemRisk(items, cap);
                        total += supplyRisk(items, available);
                        total += contactRisk(contacts);
                        total += cleanRegionRisk(region);
                        total += promotionRisk(promotion);
                        total += sourceRisk(source, client);
                        total += repeatSignal(items, contacts);
                        total += reviewFallback(total, source);
                        if (total < 0) {
                            total = 0;
                        }
                        if (total > 100) {
                            total = 100;
                        }
                        return capScore(total);
                    }

                    private int itemRisk(int[] items, int cap) {
                        int score = 0;
                        int index = 0;
                        if (items.length == 0) {
                            return 12;
                        }
                        for (int item : items) {
                            index++;
                            if (item <= 0) {
                                score += 9;
                            } else if (item > cap) {
                                score += 20;
                            } else if (item > cap / 2) {
                                score += 7;
                            } else {
                                score += 1;
                            }
                            if (index > 40) {
                                score += 4;
                            }
                        }
                        return score;
                    }

                    private int supplyRisk(int[] items, int available) {
                        int score = 0;
                        int requested = 0;
                        for (int item : items) {
                            requested += Math.max(0, item);
                            if (requested > available) {
                                score += 13;
                                break;
                            }
                            if (available - requested < 3) {
                                score += 4;
                            }
                        }
                        if (requested == 0) {
                            score += 6;
                        }
                        return score;
                    }

                    private int contactRisk(String[] contacts) {
                        int score = 0;
                        for (String contact : contacts) {
                            if (contact == null || contact.isBlank()) {
                                score += 8;
                            } else if (contact.endsWith("@temp.example")) {
                                score += 16;
                            } else if (contact.contains("+promo")) {
                                score += 5;
                            } else {
                                score += 1;
                            }
                        }
                        return score;
                    }

                    private int cleanRegionRisk(String region) {
                        if (region == null) {
                            return 11;
                        }
                        String normalized = region.trim().toUpperCase();
                        if (normalized.equals("CN") || normalized.equals("BR")) {
                            return 18;
                        }
                        if (normalized.equals("IN")) {
                            return 9;
                        }
                        if (normalized.length() == 0) {
                            return 7;
                        }
                        return 2;
                    }

                    private int promotionRisk(String promotion) {
                        if (promotion == null) {
                            return 3;
                        }
                        String text = promotion.trim().toLowerCase();
                        int score = 0;
                        if (text.startsWith("free")) {
                            score += 12;
                        }
                        if (text.contains("vip")) {
                            score -= 3;
                        }
                        if (text.length() > 18) {
                            score += 6;
                        }
                        return score;
                    }

                    private int sourceRisk(String source, String client) {
                        int score = 0;
                        String cleanSource = source == null ? "unknown" : source.trim().toLowerCase();
                        String cleanClient = client == null ? "unknown" : client.trim().toLowerCase();
                        switch (cleanSource) {
                            case "marketplace":
                                score += 7;
                                if (cleanClient.contains("bot")) {
                                    score += 19;
                                }
                                break;
                            case "mobile":
                                score += cleanClient.contains("root") ? 12 : 2;
                                break;
                            case "store":
                                score -= 2;
                                break;
                            default:
                                score += 5;
                                break;
                        }
                        return score;
                    }

                    private int repeatSignal(int[] items, String[] contacts) {
                        int score = 0;
                        int repeats = 0;
                        int last = Integer.MIN_VALUE;
                        for (int item : items) {
                            if (item == last) {
                                repeats++;
                            }
                            last = item;
                        }
                        if (repeats > 5) {
                            score += 10;
                        }
                        int blank = 0;
                        for (String contact : contacts) {
                            if (contact == null || contact.isBlank()) {
                                blank++;
                            }
                        }
                        if (blank > contacts.length / 2) {
                            score += 14;
                        }
                        return score;
                    }

                    private int reviewFallback(int total, String source) {
                        if (total > 80) {
                            return 6;
                        }
                        if (source == null || source.isBlank()) {
                            return 4;
                        }
                        if (source.equalsIgnoreCase("store")) {
                            return -2;
                        }
                        return 0;
                    }

                    private int capScore(int score) {
                        if (score < 0) {
                            return 0;
                        }
                        return Math.min(score, 100);
                    }
                }`;
                    document.querySelector('#sampleBtn').addEventListener('click', () => {
                      sourceA.value = nextSampleA;
                      sourceB.value = nextSampleB;
                      fileA.value = 'InlineGuardLong.java';
                      fileB.value = 'HelperGuardLong.java';
                      fileAEdited = false;
                      fileBEdited = false;
                      syncHighlight(sourceA, highlightA);
                      syncHighlight(sourceB, highlightB);
                    });
                    fileA.addEventListener('input', () => { fileAEdited = true; });
                    fileB.addEventListener('input', () => { fileBEdited = true; });
                    sourceA.addEventListener('input', () => {
                      updateInferredFileName(sourceA, fileA, 'Code 1.java', () => fileAEdited);
                      syncHighlight(sourceA, highlightA);
                    });
                    sourceB.addEventListener('input', () => {
                      updateInferredFileName(sourceB, fileB, 'Code 2.java', () => fileBEdited);
                      syncHighlight(sourceB, highlightB);
                    });
                    sourceA.addEventListener('scroll', () => syncEditorScroll(sourceA, highlightA));
                    sourceB.addEventListener('scroll', () => syncEditorScroll(sourceB, highlightB));
                    document.querySelector('#chooseModeBtn').addEventListener('click', showModeChoice);
                    document.querySelector('#editCodeBtn').addEventListener('click', showInput);
                    document.querySelector('#runAstBtn').addEventListener('click', () => runAnalysis('ast'));
                    document.querySelector('#runCfgBtn').addEventListener('click', () => runAnalysis('cfg'));
                    document.querySelector('#runNextBtn').addEventListener('click', () => runAnalysis('next'));
                    document.querySelector('#rerunBtn').addEventListener('click', () => runAnalysis(reportMode));
                    document.querySelector('#modeBtn').addEventListener('click', showModeChoice);
                    document.querySelector('#backBtn').addEventListener('click', showInput);
                    syncHighlight(sourceA, highlightA);
                    syncHighlight(sourceB, highlightB);
                    function showInput() {
                      modeView.style.display = 'none';
                      reportView.style.display = 'none';
                      inputView.style.display = 'grid';
                      reportActions.classList.add('hidden');
                      inputActions.classList.remove('hidden');
                    }
                    function showModeChoice() {
                      inputView.style.display = 'none';
                      reportView.style.display = 'none';
                      modeView.style.display = 'grid';
                      reportActions.classList.add('hidden');
                      inputActions.classList.add('hidden');
                    }
                    function showReport() {
                      inputView.style.display = 'none';
                      modeView.style.display = 'none';
                      reportView.style.display = 'grid';
                      inputActions.classList.add('hidden');
                      reportActions.classList.remove('hidden');
                    }
                    function updateInferredFileName(sourceEl, fileEl, fallback, edited) {
                      if (edited()) return;
                      fileEl.value = inferFileName(sourceEl.value, fallback);
                    }
                    function inferFileName(source, fallback) {
                      const typeMatch = source.match(/\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)/);
                      if (typeMatch) return `${typeMatch[1]}.java`;
                      const methodMatch = source.match(/\\b(?:public|private|protected|static|final|synchronized|abstract|native|strictfp|\\s)+[A-Za-z_$][\\w$<>\\[\\], ?]*\\s+([A-Za-z_$][\\w$]*)\\s*\\(/);
                      if (methodMatch) return `${methodMatch[1]}.java`;
                      return fallback;
                    }
                    function syncEditorScroll(textarea, layer) {
                      layer.scrollTop = textarea.scrollTop;
                      layer.scrollLeft = textarea.scrollLeft;
                    }
                    function syncHighlight(textarea, layer) {
                      layer.innerHTML = highlightJava(textarea.value) + '\\n';
                      syncEditorScroll(textarea, layer);
                    }
                    function highlightJava(source) {
                      let html = escapeHtml(source);
                      const placeholders = [];
                      html = html.replace(/(&quot;(?:\\\\.|[^&])*?&quot;|'(?:\\\\.|[^'])*?')/g, match => {
                        const key = `@@str${String.fromCharCode(97 + placeholders.length)}@@`;
                        placeholders.push(`<span class="tok-string">${match}</span>`);
                        return key;
                      });
                      html = html.replace(/(\\/\\/.*$)/gm, '<span class="tok-comment">$1</span>');
                      html = html.replace(/\\b(class|interface|enum|record|public|private|protected|static|final|if|else|for|while|switch|case|default|return|new|try|catch|throw|throws|extends|implements|import|package|void|int|boolean|double|float|long|short|byte|char|String|null|true|false|break|continue)\\b/g, '<span class="tok-keyword">$1</span>');
                      html = html.replace(/\\b([A-Z][A-Za-z0-9_]*)\\b/g, '<span class="tok-type">$1</span>');
                      html = html.replace(/\\b(\\d+)\\b/g, '<span class="tok-number">$1</span>');
                      placeholders.forEach((value, index) => {
                        html = html.replace(`@@str${String.fromCharCode(97 + index)}@@`, value);
                      });
                      return html;
                    }
                    async function runAnalysis(mode) {
                      analysisMode = mode;
                      if (analysisMode === 'cfg') {
                        reportData = buildCfgReport();
                        reportMode = 'cfg';
                        selectedPairIndex = 0;
                        cfgDetailOpen = false;
                        selectedCfgNode = 'L2';
                        activeSection = 'summary';
                        showReport();
                        renderReport();
                        return;
                      }
                      if (analysisMode === 'next') {
                        reportData = buildNextReport();
                        reportMode = 'next';
                        selectedPairIndex = 0;
                        cfgDetailOpen = false;
                        selectedCfgNode = '';
                        selectedNextRegion = 'main-flow';
                        selectedNextType = 'ALL';
                        activeSection = 'summary';
                        showReport();
                        renderReport();
                        return;
                      }
                      const body = new URLSearchParams({
                        fileA: fileA.value || inferFileName(sourceA.value, 'Code 1.java'),
                        fileB: fileB.value || inferFileName(sourceB.value, 'Code 2.java'),
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
                        reportMode = 'ast';
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
                      const sections = reportMode === 'cfg' ? cfgSections : reportMode === 'next' ? nextSections : astSections;
                      reportHeading.textContent = reportMode === 'cfg'
                        ? 'CFG Evidence Report'
                        : reportMode === 'next'
                          ? 'Evidence-first Report'
                          : 'Similarity Report';
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
                      if (reportMode === 'cfg') {
                        if (activeSection === 'summary') sectionContent.innerHTML = renderCfgSummary(reportData, files);
                        if (activeSection === 'explorer') sectionContent.innerHTML = renderCfgExplorer(reportData, files);
                        if (activeSection === 'json') sectionContent.innerHTML = renderJson(reportData);
                      } else if (reportMode === 'next') {
                        if (activeSection === 'summary') sectionContent.innerHTML = renderNextSummary(reportData, files);
                        if (activeSection === 'regions') sectionContent.innerHTML = renderNextRegions(reportData, files);
                        if (activeSection === 'cfg') sectionContent.innerHTML = renderCfgExplorer(reportData, files);
                        if (activeSection === 'source') sectionContent.innerHTML = renderNextSource(reportData, files);
                        if (activeSection === 'json') sectionContent.innerHTML = renderJson(reportData);
                      } else {
                        if (activeSection === 'summary') sectionContent.innerHTML = renderSummary(reportData, files);
                        if (activeSection === 'methods') sectionContent.innerHTML = renderMethods(reportData, files);
                        if (activeSection === 'evidence') sectionContent.innerHTML = renderEvidence(reportData, files);
                        if (activeSection === 'json') sectionContent.innerHTML = renderJson(reportData);
                      }
                      bindSectionEvents();
                      if (shouldScrollNextRegion) {
                        shouldScrollNextRegion = false;
                        const targetRegion = nextScrollTargetRegion || selectedNextRegion;
                        nextScrollTargetRegion = '';
                        requestAnimationFrame(() => {
                          scrollNextRegionIntoView(targetRegion);
                        });
                      }
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
                          <div class="summary-lead">
                            <div class="summary-decision">${escapeHtml(capitalize(clone.short))}</div>
                            <div class="summary-sentence ${leadClass}">${summarySentence(summary, clone, scope)}</div>
                          </div>
                          <div class="tag-row">
                            <span class="tag">${decisionText(summary.cloneType, summary.scopeType)}</span>
                            <span class="tag">Confidence: ${summary.confidenceLevel}</span>
                            <span class="tag">Checks completed: ${percent(stage4.pipelineReliability)}</span>
                          </div>
                        </div>
                        <div class="focus-grid">
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
                          <div class="focus-card">
                            <div class="label">Main Difference</div>
                            <div class="focus-value">${differenceSummary(summary.cloneType)}</div>
                            <div class="muted">${clone.description}</div>
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
                    """, """
                    function renderEvidence(data, files) {
                      const chain = data.stage4.evidenceChain;
                      const stage0 = data.stage0;
                      const stage1 = data.stage1;
                      const stage3 = data.stage3;
                      const stage4 = data.stage4;
                      const signalCount = chain.supportingEvidence.length + chain.opposingEvidence.length
                        + chain.scopeEvidence.length + chain.reliabilityWarnings.length;
                      return `<section class="section">
                        <div class="section-head">
                          <h3>Why This Result?</h3>
                          <div class="muted">The main checks behind the report</div>
                        </div>
                        <div class="evidence-hero">
                          ${reasonBreakdownPanel(chain)}
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
                    function renderNextSummary(data, files) {
                      const summary = data.nextSummary;
                      const leftPercent = Math.round(summary.affectedContent.leftRatio * 100);
                      const rightPercent = Math.round(summary.affectedContent.rightRatio * 100);
                      const leftSeverity = affectedSeverity(leftPercent);
                      const rightSeverity = affectedSeverity(rightPercent);
                      return `<section class="section">
                        <div class="next-affected-grid">
                          <div class="next-donut-card ${leftSeverity.className}">
                            <div class="next-donut ${leftSeverity.className}" style="--value:${leftPercent}"><strong>${leftPercent}%</strong></div>
                            <div>
                              <div class="muted">Matched or changed code regions.</div>
                              <div class="next-file-name">${escapeHtml(files.left.short)}</div>
                              <div class="severity-status ${leftSeverity.className}">${escapeHtml(leftSeverity.label)}</div>
                            </div>
                          </div>
                          <div class="next-donut-card ${rightSeverity.className}">
                            <div class="next-donut ${rightSeverity.className}" style="--value:${rightPercent}"><strong>${rightPercent}%</strong></div>
                            <div>
                              <div class="muted">Matched or changed code regions.</div>
                              <div class="next-file-name">${escapeHtml(files.right.short)}</div>
                              <div class="severity-status ${rightSeverity.className}">${escapeHtml(rightSeverity.label)}</div>
                            </div>
                          </div>
                        </div>
                        <div class="next-type-summary">
                          <div class="section-head">
                            <h3>Evidence Types</h3>
                          </div>
                          <div class="next-breakdown">
                            ${summary.evidenceBreakdown.map(renderNextBreakdownRow).join('')}
                          </div>
                        </div>
                      </section>`;
                    }
                    function affectedSeverity(value) {
                      if (value >= 90) return { className: 'severity-high', label: 'Check first' };
                      if (value >= 60) return { className: 'severity-mid', label: 'Review needed' };
                      return { className: 'severity-low', label: 'Light review' };
                    }
                    function renderNextBreakdownRow(item) {
                      const typeClass = item.type.toLowerCase().replace(/_.*/, '');
                      return `<button class="next-breakdown-row" data-next-summary-type="${escapeHtml(item.type)}" type="button">
                        <span class="next-type ${escapeHtml(typeClass)}">${escapeHtml(item.type)}</span>
                        <div>
                          <strong>${escapeHtml(nextTypeLabel(item.type))}</strong>
                          <div class="muted">${item.regionCount} regions</div>
                        </div>
                        <div>
                          <div class="label">Affected</div>
                          <strong>${percent(item.affectedLeftRatio)} / ${percent(item.affectedRightRatio)}</strong>
                        </div>
                      </button>`;
                    }
                    function renderNextRegions(data, files) {
                      const regions = data.nextRegions;
                      const selected = regions.find(region => region.id === selectedNextRegion) || regions[0];
                      return `<section class="section">
                        <div class="section-head">
                          <h3>Suspicious Regions</h3>
                          <div class="muted">Pick one region.</div>
                        </div>
                        <div class="next-region-layout">
                          <div class="next-region-list">
                            ${regions.map(region => `<button class="next-region-card ${region.id === selected.id ? 'active' : ''}" data-next-region="${escapeHtml(region.id)}" type="button">
                              <span class="next-type ${escapeHtml(region.type.toLowerCase())}">${escapeHtml(region.type)}</span>
                              <strong>${escapeHtml(region.title)}</strong>
                              <span class="muted">${escapeHtml(region.affected)} · lines ${escapeHtml(region.leftRange.join('-'))}</span>
                            </button>`).join('')}
                          </div>
                          <div class="next-region-detail">
                            <div class="pair-title">
                              <div class="label">Selected</div>
                              <h3>${escapeHtml(selected.title)}</h3>
                              <div class="muted">${escapeHtml(selected.short)}</div>
                            </div>
                            <div class="detail-grid">
                              ${detailCard('Type', selected.type + '-like', 'Evidence category.')}
                              ${detailCard('Affected', selected.affected, 'Approximate content size.')}
                              ${detailCard(files.left.short, 'lines ' + selected.leftRange.join('-'), 'Click Inline Code to view.')}
                              ${detailCard(files.right.short, 'lines ' + selected.rightRange.join('-'), 'Click Inline Code to view.')}
                            </div>
                            <div class="sub-panel" style="margin-top:18px">
                              <h3>Changes</h3>
                              <div class="evidence-list">${selected.changes.slice(0, 3).map(change => `<div class="signal-row" style="grid-template-columns:minmax(0,1fr)"><div>${escapeHtml(change)}</div></div>`).join('')}</div>
                            </div>
                          </div>
                        </div>
                      </section>`;
                    }
                    function renderNextSource(data, files) {
                      const regions = data.nextRegions;
                      const selected = regions.find(region => region.id === selectedNextRegion) || regions[0];
                      const types = ['ALL', ...Array.from(new Set(regions.map(region => region.type)))];
                      return `<section class="section">
                        <div class="next-code-filter">
                          <div>
                            <h3>Code Regions</h3>
                            <div class="muted">Choose a type to highlight matching code.</div>
                          </div>
                          <div class="next-type-buttons">
                            ${types.map(type => `<button class="next-type-button ${escapeHtml(type.toLowerCase())} ${selectedNextType === type ? 'active' : ''}" data-next-type="${escapeHtml(type)}" type="button">${escapeHtml(nextTypeButtonLabel(type))}</button>`).join('')}
                          </div>
                        </div>
                        <div class="code-grid">
                          ${nextCodePanel(files.left, 'left', sourceA.value, regions, selected)}
                          ${nextCodePanel(files.right, 'right', sourceB.value, regions, selected)}
                        </div>
                      </section>`;
                    }
                    function nextCodePanel(file, side, source, regions, selected) {
                      const lines = source.split('\\n');
                      return `<div class="code-card">
                        <div class="code-head ${side}" title="${escapeHtml(file.raw)}">${escapeHtml(file.short)}</div>
                        <pre class="code">${lines.map((line, index) => {
                          const lineNo = index + 1;
                          const region = regions.find(item => {
                            const range = side === 'left' ? item.leftRange : item.rightRange;
                            return lineNo >= range[0] && lineNo <= range[1];
                          });
                          const visible = region && (selectedNextType === 'ALL' || region.type === selectedNextType);
                          const peerFocus = region && region.id === selectedNextRegion ? 'peer-focus' : '';
                          const range = region ? (side === 'left' ? region.leftRange : region.rightRange) : null;
                          const startsRegion = visible && range && lineNo === range[0];
                          const cls = region ? ` next-code-line region ${visible ? 'visible-region' : ''} ${startsRegion ? 'region-start' : ''} ${peerFocus} ${region.type ? region.type.toLowerCase() : ''}` : '';
                          const attr = region ? ` data-next-region="${escapeHtml(region.id)}" data-side="${escapeHtml(side)}"` : '';
                          const labelAttr = startsRegion ? ` data-region-label="${escapeHtml(nextRegionLabel(region, regions))}"` : '';
                          return `<span class="code-line${cls}"${attr}><span class="line-no">${lineNo}</span><span class="line-code"${labelAttr}>${escapeHtml(line || ' ')}</span></span>`;
                        }).join('')}</pre>
                      </div>`;
                    }
                    function nextRegionLabel(region, regions) {
                      const sameType = regions.filter(item => item.type === region.type);
                      const index = sameType.findIndex(item => item.id === region.id) + 1;
                      return `${region.type}-${index}`;
                    }
                    function nextTypeLabel(type) {
                      const labels = {
                        T1: 'No code changes',
                        T2: 'Names changed',
                        T3: 'Code added/changed',
                        T4: 'Works alike'
                      };
                      return `${labels[type] || type} (${type})`;
                    }
                    function nextTypeButtonLabel(type) {
                      if (type === 'ALL') return 'All highlights';
                      return nextTypeLabel(type);
                    }
                    function renderCfgSummary(data, files) {
                      const cfg = data.cfg;
                      return `<section class="section cfg-page">
                        <div class="cfg-hero">
                          <div class="cfg-statement">
                            <div class="label">CFG Analysis</div>
                            <h3>${escapeHtml(cfg.resultLabel)}</h3>
                            <p>${escapeHtml(cfg.summary)}</p>
                            <div class="tag-row">
                              ${cfg.tags.map(tag => `<span class="tag">${escapeHtml(tag)}</span>`).join('')}
                            </div>
                          </div>
                          ${cfgMetric('CFG Score', percent(cfg.score), 'best ordinary method pair')}
                          ${cfgMetric('Best Match', cfg.bestShort, 'method-level evidence')}
                          ${cfgMetric('Candidates', cfg.candidateReduction, 'after KNN narrowing')}
                        </div>
                        <div class="summary-columns">
                          <div class="sub-panel">
                            <div class="section-head">
                              <h3>What The Analyzer Found</h3>
                              <div class="muted">The strongest clues from this CFG pass.</div>
                            </div>
                            <div class="cfg-channel-grid">
                              ${cfg.channels.map(renderCfgChannel).join('')}
                            </div>
                          </div>
                          <div class="sub-panel">
                            <div class="section-head">
                              <h3>Next Step</h3>
                              <div class="muted">Open the explorer to inspect the match.</div>
                            </div>
                            <div class="cfg-note-grid">
                              <div class="cfg-note good">
                                <div class="label">Review the top method pair</div>
                                <p class="muted">Check whether the top pair is the code you expected the analyzer to compare.</p>
                              </div>
                              <div class="cfg-note warn">
                                <div class="label">Click through the CFG blocks</div>
                                <p class="muted">Select a node to see its matched block, local distance, and raw instruction summary.</p>
                              </div>
                            </div>
                          </div>
                        </div>
                      </section>`;
                    }
                    function renderCfgExplorer(data, files) {
                      const cfg = data.cfg;
                      const method = cfg.methods[selectedPairIndex] || cfg.methods[0];
                      if (!cfgDetailOpen) {
                        return renderCfgPairOverview(cfg);
                      }
                      if (cfgSubView === 'matches') {
                        return renderCfgMatches(data, files);
                      }
                      if (cfgSubView === 'closeness') {
                        return renderCfgCloseness(data, files);
                      }
                      const blocks = cfg.blockSets[method.blockSet] || cfg.blockSets.main;
                      const selected = selectedCfgNode ? selectedCfgBlock(blocks, selectedCfgNode) : null;
                      return `<section class="section cfg-page">
                        ${renderCfgPageNote('Step 2', 'Open the CFG graph and click blocks.')}
                        <div class="cfg-page-actions">
                          <div class="cfg-toggle-group">
                            <button class="cfg-toggle" data-cfg-back type="button">Back to sections</button>
                          </div>
                          <div class="cfg-toggle-group">
                            <button class="cfg-toggle" data-cfg-view="matches" type="button">Next: Block Matches</button>
                          </div>
                        </div>
                        <div class="cfg-visual ${selected ? 'with-inspector' : ''}">
                          <div class="cfg-explorer-main graph-only">
                            <div class="cfg-explorer-toolbar">
                              <div></div>
                              <div class="cfg-toggle-group">
                                <button class="cfg-toggle ${showCfgLoops ? 'active' : ''}" data-cfg-toggle="loops" type="button">Show loop lines</button>
                                <button class="cfg-toggle ${focusCfgMatch ? 'active' : ''}" data-cfg-toggle="focus" type="button">Highlight selected</button>
                              </div>
                            </div>
                            <div class="cfg-graph-grid">
                              ${renderCfgGraph(files.left.short, 'left', blocks)}
                              ${renderCfgGraph(files.right.short, 'right', blocks)}
                            </div>
                          </div>
                          ${selected ? `<aside class="cfg-inspector">
                            <div>
                              <div class="label">Selected block</div>
                              <h3>${escapeHtml(selected.block.display)}</h3>
                              <div class="muted">${escapeHtml(selected.block.detail)}</div>
                            </div>
                            <div class="cfg-kv"><span>File</span><span>${selected.side === 'left' ? escapeHtml(files.left.short) : escapeHtml(files.right.short)}</span></div>
                            <div class="cfg-kv"><span>Matched with</span><span>${escapeHtml(selected.matchLabel)}</span></div>
                            <div class="cfg-kv"><span>Closeness</span><span>${selected.match ? escapeHtml(closenessText(selected.match.distance)) : 'No close match'}</span></div>
                            <div class="cfg-kv"><span>Role</span><span>${escapeHtml(selected.block.meaning)}</span></div>
                          </aside>` : ''}
                        </div>
                      </section>`;
                    }
                    function renderCfgMatches(data, files) {
                      const cfg = data.cfg;
                      const method = cfg.methods[selectedPairIndex] || cfg.methods[0];
                      const blocks = cfg.blockSets[method.blockSet] || cfg.blockSets.main;
                      return `<section class="section cfg-page">
                        ${renderCfgPageNote('Step 3', 'Check which blocks were matched before reading closeness.')}
                        <div class="cfg-page-actions">
                          <div class="cfg-toggle-group">
                            <button class="cfg-toggle" data-cfg-view="graph" type="button">Back: CFG Graph</button>
                          </div>
                          <div class="cfg-toggle-group">
                            <button class="cfg-toggle" data-cfg-view="closeness" type="button">Next: Closeness</button>
                          </div>
                        </div>
                        <div class="section-head"><h3>Block Matches</h3><div class="muted">${escapeHtml(method.left)} -> ${escapeHtml(method.right)}</div></div>
                        <div class="cfg-match-panel always">
                          <div class="cfg-links">
                            ${blocks.matches.map(match => renderCfgMatchLine(match, blocks, true)).join('')}
                          </div>
                        </div>
                      </section>`;
                    }
                    function renderCfgCloseness(data, files) {
                      const cfg = data.cfg;
                      const method = cfg.methods[selectedPairIndex] || cfg.methods[0];
                      const blocks = cfg.blockSets[method.blockSet] || cfg.blockSets.main;
                      return `<section class="section cfg-page">
                        ${renderCfgPageNote('Step 3', 'Use closeness after checking which blocks were matched. Lower distance means closer blocks.')}
                        <div class="cfg-page-actions">
                          <div class="cfg-toggle-group">
                            <button class="cfg-toggle" data-cfg-view="matches" type="button">Back: Block Matches</button>
                          </div>
                          <div class="cfg-toggle-group">
                            <button class="cfg-toggle" data-cfg-back type="button">Back to sections</button>
                          </div>
                        </div>
                        <div class="section-head"><h3>Closeness</h3><div class="muted">Lower distance means closer blocks.</div></div>
                        <div class="bar-list">
                          ${blocks.matches.map(match => {
                            const left = blocks.left.find(block => block.id === match.left);
                            const right = blocks.right.find(block => block.id === match.right);
                            const label = `${left?.display || match.left} -> ${right?.display || match.right}`;
                            return scoreBar(label, Math.max(0, 1 - Number(match.distance || 0)), closenessText(match.distance));
                          }).join('')}
                        </div>
                      </section>`;
                    }
                    function renderCfgPairOverview(cfg) {
                      return `<section class="section cfg-page">
                        <div class="section-head">
                          <h3>CFG Explorer</h3>
                          <div class="muted">Step 1: choose a function pair first. The CFG graph opens after you pick one.</div>
                        </div>
                        <div class="cfg-category-grid">
                          ${cfg.categories.map(category => renderCfgCategory(category, cfg.methods)).join('')}
                        </div>
                      </section>`;
                    }
                    function renderCfgPageNote(step, text) {
                      return `<div class="cfg-page-note"><strong>${escapeHtml(step)}</strong><span class="muted">${escapeHtml(text)}</span></div>`;
                    }
                    function renderCfgCategory(category, methods) {
                      const pairs = methods
                        .map((method, index) => ({ ...method, index }))
                        .filter(method => method.category === category.key);
                      return `<div class="cfg-category-card ${escapeHtml(category.key)}">
                        <div class="cfg-category-head">
                          <h3>${escapeHtml(category.title)}</h3>
                          <p>${escapeHtml(category.description)}</p>
                        </div>
                        <div class="tag-row">
                          <span class="cfg-category-tag ${escapeHtml(category.key)}">${escapeHtml(category.badge)}</span>
                        </div>
                        <div class="cfg-pair-list">
                          ${pairs.length ? pairs.map(renderCfgPairCard).join('') : '<div class="cfg-empty">No function pairs yet</div>'}
                        </div>
                      </div>`;
                    }
                    function renderCfgPairCard(method) {
                      return `<button class="cfg-pair-card" data-cfg-open-pair="${method.index}" type="button">
                        <span class="cfg-pair-title">
                          <span>${escapeHtml(method.title)}</span>
                          <span class="cfg-method-score">${percent(method.score)}</span>
                        </span>
                        <span class="cfg-pair-code">${escapeHtml(method.left)}<br>${escapeHtml(method.right)}</span>
                        <span class="muted">${escapeHtml(method.explanation)}</span>
                      </button>`;
                    }
                    function renderCfgGraph(fileName, side, blocks) {
                      const nodes = blocks[side] || [];
                      return `<div class="cfg-graph-card">
                        <div class="cfg-tree-title"><strong>${escapeHtml(fileName)}</strong><span class="tag">${side === 'left' ? 'Left CFG' : 'Right CFG'}</span></div>
                        <div class="cfg-graph-canvas">
                          <svg class="cfg-edge-layer" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
                            <defs>
                              <marker id="arrow-${side}" markerWidth="4.8" markerHeight="4.8" refX="4.35" refY="2.4" orient="auto" markerUnits="strokeWidth">
                                <path d="M 0 0 L 4.8 2.4 L 0 4.8 z" fill="#64748b"></path>
                              </marker>
                              <marker id="arrow-loop-${side}" markerWidth="4.2" markerHeight="4.2" refX="3.8" refY="2.1" orient="auto" markerUnits="strokeWidth">
                                <path d="M 0 0 L 4.2 2.1 L 0 4.2 z" fill="#c47a1c"></path>
                              </marker>
                            </defs>
                            ${renderCfgEdges(nodes, blocks[`${side}Edges`] || [], side)}
                          </svg>
                          ${nodes.map(block => renderCfgNode(block, blocks)).join('')}
                        </div>
                      </div>`;
                    }
                    function renderCfgEdges(nodes, edges, side) {
                      const byId = new Map(nodes.map(node => [node.id, node]));
                      return edges.map(edge => {
                        const from = byId.get(edge.from);
                        const to = byId.get(edge.to);
                        if (!from || !to) return '';
                        const cls = edge.kind === 'loop' ? 'cfg-edge loop' : 'cfg-edge';
                        if (edge.kind === 'loop' && !showCfgLoops) return '';
                        const start = cfgEdgePoint(from, to, 1.0);
                        const end = cfgEdgePoint(to, from, 1.0);
                        if (edge.kind === 'loop') {
                          const dx = edge.side === 'left' ? -18 : 18;
                          const midY = Math.min(from.y, to.y) - 10;
                          return `<path class="${cls}" d="M ${start.x} ${start.y} C ${from.x + dx} ${midY}, ${to.x + dx} ${midY}, ${end.x} ${end.y}" marker-end="url(#arrow-loop-${side})"></path>`;
                        }
                        return `<line class="${cls}" x1="${start.x}" y1="${start.y}" x2="${end.x}" y2="${end.y}" marker-end="url(#arrow-${side})"></line>`;
                      }).join('');
                    }
                    function cfgEdgePoint(anchor, other, extra = 1) {
                      const dx = other.x - anchor.x;
                      const dy = other.y - anchor.y;
                      const absX = Math.abs(dx);
                      const absY = Math.abs(dy);
                      const radiusX = 14.0;
                      const radiusY = 4.25;
                      const xScale = absX > 0 ? radiusX / absX : Infinity;
                      const yScale = absY > 0 ? radiusY / absY : Infinity;
                      const scale = Math.min(xScale, yScale, 0.48) * extra;
                      return {
                        x: Number((anchor.x + dx * scale).toFixed(2)),
                        y: Number((anchor.y + dy * scale).toFixed(2))
                      };
                    }
                    function cfgMetric(label, value, note) {
                      return `<div class="cfg-metric"><div class="label">${escapeHtml(label)}</div><strong>${escapeHtml(value)}</strong><div class="muted">${escapeHtml(note)}</div></div>`;
                    }
                    function renderCfgChannel(channel) {
                      return `<div class="cfg-channel">
                        <div class="label">${escapeHtml(channel.name)}</div>
                        <strong>${escapeHtml(channel.state)}</strong>
                        <div class="cfg-track"><span style="width:${Math.round(channel.value * 100)}%"></span></div>
                        <div class="muted">${escapeHtml(channel.note)}</div>
                      </div>`;
                    }
                    function renderCfgMatchLine(match, blocks, forceDistance = false) {
                      const left = blocks.left.find(block => block.id === match.left);
                      const right = blocks.right.find(block => block.id === match.right);
                      const leftName = left ? (left.display || left.name) : match.left;
                      const rightName = right ? (right.display || right.name) : match.right;
                      return `<span><strong>${escapeHtml(leftName)} -> ${escapeHtml(rightName)}</strong><small>${escapeHtml(match.friendly || `${match.label} matched`)}${(forceDistance || showCfgDistance) ? ` · ${escapeHtml(closenessShort(match.distance))}` : ''}</small></span>`;
                    }
                    function closenessText(distance) {
                      if (distance == null || Number.isNaN(Number(distance))) return 'No close match';
                      const value = Number(distance);
                      if (value <= 0.05) return `${value.toFixed(2)} · very close`;
                      if (value <= 0.20) return `${value.toFixed(2)} · similar with changes`;
                      return `${value.toFixed(2)} · weak match`;
                    }
                    function closenessShort(distance) {
                      if (distance == null || Number.isNaN(Number(distance))) return '';
                      const value = Number(distance);
                      if (value <= 0.05) return 'very close';
                      if (value <= 0.20) return 'changed';
                      return 'weak';
                    }
                    function selectedCfgBlock(blocks, id) {
                      const left = blocks.left.find(block => block.id === id);
                      const right = blocks.right.find(block => block.id === id);
                      const block = left || right || blocks.left[0];
                      const side = left ? 'left' : 'right';
                      const match = blocks.matches.find(item => item.left === block.id || item.right === block.id);
                      const matchedId = match ? (side === 'left' ? match.right : match.left) : '';
                      const matchedBlock = matchedId
                        ? [...blocks.left, ...blocks.right].find(item => item.id === matchedId)
                        : null;
                      return {
                        block,
                        side,
                        match,
                        matchLabel: matchedBlock ? `${matchedBlock.display || matchedBlock.name} in the other file` : 'No matched block'
                      };
                    }
                    function renderCfgNode(block, blocks) {
                      const match = blocks.matches.find(item => item.left === block.id || item.right === block.id);
                      const active = selectedCfgNode === block.id ? 'active' : '';
                      const related = match && (match.left === selectedCfgNode || match.right === selectedCfgNode);
                      const dim = focusCfgMatch && selectedCfgNode && !active && !related ? 'dim' : '';
                      const style = block.x != null && block.y != null ? `style="left:${Number(block.x)}%;top:${Number(block.y)}%"` : '';
                      return `<button class="cfg-node ${escapeHtml(block.kind || '')} ${active} ${dim}" ${style} data-cfg-node="${escapeHtml(block.id)}" type="button"><strong>${escapeHtml(block.display || block.name)}</strong><span>${escapeHtml(block.detail)}</span></button>`;
                    }
                    function bindSectionEvents() {
                      sectionContent.querySelectorAll('[data-cfg-open-pair]').forEach(btn => {
                        btn.addEventListener('click', () => {
                          selectedPairIndex = Number(btn.dataset.cfgOpenPair);
                          selectedCfgNode = '';
                          cfgSubView = 'graph';
                          cfgDetailOpen = true;
                          renderReport();
                        });
                      });
                      sectionContent.querySelectorAll('[data-cfg-back]').forEach(btn => {
                        btn.addEventListener('click', () => {
                          cfgDetailOpen = false;
                          cfgSubView = 'graph';
                          renderReport();
                        });
                      });
                      sectionContent.querySelectorAll('[data-cfg-view]').forEach(btn => {
                        btn.addEventListener('click', () => {
                          cfgSubView = btn.dataset.cfgView || 'graph';
                          renderReport();
                        });
                      });
                      sectionContent.querySelectorAll('[data-pair]').forEach(btn => {
                        btn.addEventListener('click', () => {
                          selectedPairIndex = Number(btn.dataset.pair);
                          selectedCfgNode = 'L2';
                          renderReport();
                        });
                      });
                      sectionContent.querySelectorAll('[data-cfg-node]').forEach(btn => {
                        btn.addEventListener('click', () => {
                          const nodeId = btn.dataset.cfgNode;
                          selectedCfgNode = selectedCfgNode === nodeId ? '' : nodeId;
                          renderReport();
                        });
                      });
                      sectionContent.querySelectorAll('[data-cfg-toggle]').forEach(btn => {
                        btn.addEventListener('click', () => {
                          const toggle = btn.dataset.cfgToggle;
                          if (toggle === 'matches') showCfgMatches = !showCfgMatches;
                          if (toggle === 'distance') showCfgDistance = !showCfgDistance;
                          if (toggle === 'focus') focusCfgMatch = !focusCfgMatch;
                          if (toggle === 'raw') showCfgRaw = !showCfgRaw;
                          if (toggle === 'loops') showCfgLoops = !showCfgLoops;
                          renderReport();
                        });
                      });
                      sectionContent.querySelectorAll('[data-next-summary-type]').forEach(btn => {
                        btn.addEventListener('click', () => {
                          selectNextType(btn.dataset.nextSummaryType);
                          activeSection = 'source';
                          renderReport();
                        });
                      });
                      sectionContent.querySelectorAll('[data-next-type]').forEach(btn => {
                        btn.addEventListener('click', () => {
                          selectNextType(btn.dataset.nextType);
                          renderReport();
                        });
                      });
                      sectionContent.querySelectorAll('[data-next-region]').forEach(btn => {
                        btn.addEventListener('click', () => {
                          selectedNextRegion = btn.dataset.nextRegion;
                          const region = reportData?.nextRegions?.find(item => item.id === selectedNextRegion);
                          if (region) {
                            if (selectedNextType !== 'ALL') selectedNextType = region.type || selectedNextType;
                            nextScrollTargetRegion = region.id;
	                            if (region.id === 'quantity') selectedCfgNode = 'L3';
	                            if (region.id === 'inventory') selectedCfgNode = 'L4';
	                            if (region.id === 'email') selectedCfgNode = 'L5';
	                            if (region.id === 'region') selectedCfgNode = 'L6';
	                            if (region.id === 'coupon') selectedCfgNode = 'L7';
	                            if (region.id === 'channel') selectedCfgNode = 'L8';
	                            if (region.id === 'stability') selectedCfgNode = 'L9';
	                            if (region.id === 'fallback') selectedCfgNode = 'L10';
	                            if (region.id === 'cap') selectedCfgNode = 'L11';
	                            if (region.id === 'main-flow') selectedCfgNode = 'L1';
                          }
                          shouldScrollNextRegion = true;
                          renderReport();
                        });
                      });
                    }
                    function selectNextType(type) {
                      selectedNextType = type || 'ALL';
                      const firstRegion = selectedNextType === 'ALL'
                        ? reportData?.nextRegions?.[0]
                        : reportData?.nextRegions?.find(item => item.type === selectedNextType);
                      if (firstRegion) {
                        selectedNextRegion = firstRegion.id;
                        nextScrollTargetRegion = firstRegion.id;
                      }
                      shouldScrollNextRegion = true;
                    }
                    function scrollNextRegionIntoView(regionId) {
                      if (!regionId) return;
                      ['left', 'right'].forEach(side => {
                        const lines = Array.from(sectionContent.querySelectorAll(`[data-next-region="${cssEscape(regionId)}"][data-side="${cssEscape(side)}"]`));
                        const line = lines.find(item => item.classList.contains('region-start')) || lines[0];
                        const scroller = line?.closest('pre.code');
                        if (line && scroller) {
                          const scrollerRect = scroller.getBoundingClientRect();
                          const lineRect = line.getBoundingClientRect();
                          const targetTop = scroller.scrollTop + lineRect.top - scrollerRect.top - 18;
                          scroller.scrollTo({ top: Math.max(0, targetTop), behavior: 'auto' });
                        } else {
                          line?.scrollIntoView({ block: 'start', behavior: 'smooth' });
                        }
                      });
                    }
                    function cssEscape(value) {
                      return String(value).replace(/["\\\\]/g, '\\\\$&');
                    }
                    function fileInfo(data) {
                      return {
                        left: displayFileName(data.fileA, 'Left Code'),
                        right: displayFileName(data.fileB, 'Right Code')
                      };
                    }
                    function buildNextReport() {
                      const cfgReport = buildCfgReport();
                      cfgReport.schemaVersion = 'next-evidence-preview-1.0';
                      cfgReport.mode = 'Evidence-first';
                      cfgReport.nextSummary = {
                        inspectionPriority: 'High',
                        relationshipShape: 'Full overlap with edited regions',
	                        affectedContent: {
	                          leftRatio: 0.91,
	                          rightRatio: 0.93
	                        },
                        headline: 'Several connected regions deserve inspection.',
                        interpretation: 'The affected regions form a connected scoring workflow. The report shows what matched and what changed, but it does not force the user to accept a single file-level clone label.',
                        tags: ['renaming', 'statement edits', 'helper extraction', 'control-flow branches'],
	                        evidenceBreakdown: [
	                          { type: 'T3', label: 'Edited-region evidence', regionCount: 4, affectedLeftRatio: 0.48, affectedRightRatio: 0.51 },
	                          { type: 'T2', label: 'Renamed-region evidence', regionCount: 5, affectedLeftRatio: 0.39, affectedRightRatio: 0.38 },
	                          { type: 'T1', label: 'Exact local fragments', regionCount: 1, affectedLeftRatio: 0.04, affectedRightRatio: 0.04 }
	                        ]
                      };
                      cfgReport.nextRegions = [
                        {
                          id: 'main-flow',
                          type: 'T3',
	                          title: 'Main scoring workflow',
	                          short: 'The top-level methods share guards, staged scoring, fallback handling, and final capping.',
	                          leftRange: [2, 27],
	                          rightRange: [2, 27],
	                          affected: 'Left 14% / Right 14%',
                          tags: ['renaming', 'statement modified', 'helper calls'],
                          changes: [
                            'evaluateOrderRisk is renamed to calculateRisk.',
                            'Both methods accumulate risk from quantity, email/contact, region, coupon/promotion, and stability signals.',
                            'Both methods cap the final score before returning.'
                          ]
                        },
                        {
	                          id: 'quantity',
	                          type: 'T3',
	                          title: 'Quantity risk branch',
	                          short: 'The loop and branch scoring are preserved with small edits.',
	                          leftRange: [29, 48],
	                          rightRange: [29, 51],
	                          affected: 'Left 11% / Right 12%',
                          tags: ['literal changed', 'statement inserted', 'loop branch'],
                          changes: [
                            'Right side adds an empty-array guard.',
                            'The high-threshold score changes from 18 to 20.',
                            'The loop counter is renamed from seen to index.'
	                          ]
	                        },
	                        {
	                          id: 'inventory',
	                          type: 'T3',
	                          title: 'Inventory pressure branch',
	                          short: 'Both files add stock pressure logic with loop exits and low-stock scoring.',
	                          leftRange: [50, 67],
	                          rightRange: [53, 70],
	                          affected: 'Left 10% / Right 9%',
	                          tags: ['statement inserted', 'early break', 'loop branch'],
	                          changes: [
	                            'stock is renamed to available.',
	                            'Both sides stop scanning once requested quantity exceeds inventory.',
	                            'Both sides add a low-stock branch near the loop body.'
	                          ]
	                        },
	                        {
	                          id: 'email',
	                          type: 'T2',
	                          title: 'Email/contact signal',
	                          short: 'The email scoring helper is structurally the same after renaming.',
	                          leftRange: [69, 83],
	                          rightRange: [72, 86],
	                          affected: 'Left 8% / Right 8%',
                          tags: ['renaming', 'branch preserved'],
                          changes: [
                            'email is renamed to contact.',
                            'The null/blank/temp/promo branch structure is preserved.'
                          ]
                        },
                        {
	                          id: 'region',
	                          type: 'T2',
	                          title: 'Region normalization branch',
	                          short: 'Country and region checks preserve the same branch skeleton.',
	                          leftRange: [85, 100],
	                          rightRange: [88, 103],
	                          affected: 'Left 9% / Right 8%',
                          tags: ['renaming', 'branch preserved'],
                          changes: [
                            'country is renamed to region.',
                            'Both sides normalize text before checking CN, BR, and IN.'
	                          ]
	                        },
	                        {
	                          id: 'coupon',
	                          type: 'T2',
	                          title: 'Coupon/promotion branch',
	                          short: 'Coupon scoring keeps the same branch order after renaming.',
	                          leftRange: [102, 118],
	                          rightRange: [105, 121],
	                          affected: 'Left 9% / Right 9%',
	                          tags: ['renaming', 'branch preserved'],
	                          changes: [
	                            'coupon is renamed to promotion.',
	                            'Both sides check free, vip, and long-code signals.'
	                          ]
	                        },
	                        {
	                          id: 'channel',
	                          type: 'T3',
	                          title: 'Channel/device decision tree',
	                          short: 'One side uses if/else while the other uses switch cases for the same decision tree.',
	                          leftRange: [120, 137],
	                          rightRange: [123, 145],
	                          affected: 'Left 10% / Right 12%',
	                          tags: ['control structure changed', 'branch preserved'],
	                          changes: [
	                            'channel is renamed to source and device is renamed to client.',
	                            'The left side uses if/else; the right side uses switch.',
	                            'Marketplace, mobile, store, and default branches remain aligned.'
	                          ]
	                        },
	                        {
	                          id: 'stability',
	                          type: 'T2',
	                          title: 'Repeat/blank stability checks',
	                          short: 'Two loops preserve repeated quantity and blank contact checks.',
	                          leftRange: [139, 162],
	                          rightRange: [147, 170],
	                          affected: 'Left 13% / Right 13%',
	                          tags: ['renaming', 'loop preserved'],
	                          changes: [
	                            'quantities/emails are renamed to items/contacts.',
	                            'Both loops count repeated values and blank contact fields.'
	                          ]
	                        },
	                        {
	                          id: 'fallback',
	                          type: 'T2',
	                          title: 'Fallback review branch',
	                          short: 'The fallback helper keeps the same threshold and store-channel branch.',
	                          leftRange: [164, 175],
	                          rightRange: [172, 183],
	                          affected: 'Left 7% / Right 6%',
	                          tags: ['renaming', 'branch preserved'],
	                          changes: [
	                            'score is renamed to total.',
	                            'Both sides add review risk for high scores, blank channels, and store overrides.'
	                          ]
	                        },
	                        {
	                          id: 'cap',
	                          type: 'T1',
	                          title: 'Score cap fragment',
	                          short: 'The cap helper is exact after comment and whitespace normalization.',
	                          leftRange: [177, 182],
	                          rightRange: [185, 190],
	                          affected: 'Left 3% / Right 3%',
                          tags: ['exact local fragment'],
                          changes: ['The final cap helper keeps the same logic.']
                        }
                      ];
                      return cfgReport;
                    }
                    function buildCfgReport() {
                      const leftName = fileA.value || inferFileName(sourceA.value, 'Code 1.java');
                      const rightName = fileB.value || inferFileName(sourceB.value, 'Code 2.java');
                      const leftType = typeName(sourceA.value, 'Left');
                      const rightType = typeName(sourceB.value, 'Right');
                      const leftMethod = firstMethodName(sourceA.value, 'leftMethod');
                      const rightMethod = firstMethodName(sourceB.value, 'rightMethod');
                      const helperName = helperMethodName(sourceB.value, 'isValid');
                      const helperSignature = `${rightType}.${helperName}()`;
                      const looksLikeHelper = /isValid|helper|private\\s+boolean|private\\s+int|looksLike|clean|normalize/i.test(sourceB.value);
                      const score = looksLikeHelper ? 0.91 : 0.78;
                      return {
                        schemaVersion: 'cfg-preview-1.0',
                        mode: 'CFG',
                        fileA: leftName,
                        fileB: rightName,
                        cfg: {
                          resultLabel: looksLikeHelper ? 'Similar behavior found, with one check moved into a helper method' : 'Similar execution path found',
                          summary: 'CFG mode compares how each method runs: input checks, loop flow, updates, and return behavior. The explorer shows which execution blocks were matched.',
                          score,
                          bestShort: `${methodNameOnly(leftMethod)} -> ${methodNameOnly(rightMethod)}`,
                          candidateReduction: looksLikeHelper ? '96 -> 31' : '61 -> 42',
                          tags: ['CFG similarity', 'method ranking', 'basic-block evidence'],
                          categories: [
                            {
                              key: 'primary',
                              title: 'Main pair to inspect',
                              description: 'The function pair the analyzer thinks is most worth opening for detailed CFG comparison.',
                              badge: 'Main evidence'
                            },
                            {
                              key: 'low',
                              title: 'High score, low value',
                              description: 'These functions may look very similar, but they are usually setup, empty, or boilerplate code.',
                              badge: 'De-emphasized'
                            },
                            {
                              key: 'partial',
                              title: 'Small method may match part of a main method',
                              description: 'One file may keep logic inside the main method while the other moves part of it into a smaller method.',
                              badge: 'Partial relation'
                            }
                          ],
                          story: {
                            title: looksLikeHelper ? 'The same risk-scoring flow appears in a different shape.' : 'The methods follow a similar execution path.',
                            body: looksLikeHelper
                              ? 'Both methods collect quantity risk, email risk, country risk, and then cap the final score. Some checks are delegated to smaller helper methods.'
                              : 'The analyzer matched the main path through both methods: input preparation, branch checks, loop work, and final return.',
                            badge: 'Click any block to inspect the match'
                          },
                          channels: [
                            { name: 'Method pair', value: 0.9, state: 'Strong', note: 'The main methods were selected for inspection.' },
                            { name: 'Execution path', value: score, state: score > 0.85 ? 'Strong' : 'Medium', note: 'Input checks, loop, update, and return blocks line up.' },
                            { name: 'Raw analysis data', value: 0.82, state: 'Available', note: 'The low-level CFG evidence can be inspected.' },
                            { name: 'AST/source', value: 0.58, state: 'Auxiliary', note: 'Can be used alongside the source-structure mode.' },
                            { name: 'Inter-procedural', value: 0.0, state: 'Not enabled', note: 'Future mode for helper and call-chain expansion.' },
                            { name: 'Semantic check', value: 0.0, state: 'Not enabled', note: 'Future mode for false-positive reduction.' }
                          ],
                          methods: [
                            {
                              category: 'primary',
                              categoryLabel: 'Main pair to inspect',
                              title: `${methodNameOnly(leftMethod)} ↔ ${methodNameOnly(rightMethod)}`,
                              left: leftMethod,
                              right: rightMethod,
                              score,
                              blockSet: 'main',
                              defaultNode: 'L2',
                              explanation: 'This is the main function pair used by the report. Open it to see why their CFGs look similar.',
                              storyTitle: 'These two main functions follow a similar risk-scoring flow.',
                              storyBody: 'Both compute quantity risk, check email and region signals, and cap the final score at 100. Most differences are naming and small helper extraction.'
                            },
                            {
                              category: 'low',
                              categoryLabel: 'High score, low value',
                              title: '<init>() ↔ <init>()',
                              left: `${leftType}.<init>()V`,
                              right: `${rightType}.<init>()V`,
                              score: 1.0,
                              blockSet: 'constructor',
                              defaultNode: 'CL1',
                              explanation: 'The constructors are very similar, but this is usually setup code and should not drive the decision.',
                              storyTitle: 'This match scores high, but carries little evidence.',
                              storyBody: 'Constructors and empty initialization code often look naturally similar. The analyzer shows it, but does not treat it as the main reason.'
                            },
                            {
                              category: 'partial',
                              categoryLabel: 'Small method may match part of a main method',
                              title: `${methodNameOnly(leftMethod)} ↔ ${methodNameOnly(helperSignature)}`,
                              left: leftMethod,
                              right: helperSignature,
                              score: 0.61,
                              blockSet: 'helper',
                              defaultNode: 'HL2',
                              explanation: 'This smaller function may explain where part of the main method logic was moved.',
                              storyTitle: 'A small method may carry one local decision from the main method.',
                              storyBody: 'The left side keeps the decision in the main method. The right side places related logic in a smaller function. This is not a full clone by itself, but it explains local logic movement.'
                            }
                          ],
                          blockSets: buildCfgBlockSets(looksLikeHelper),
                          why: 'The selected methods follow a similar execution path: prepare data, check the input, loop through values, update the count, and return the result.',
                          nextCheck: looksLikeHelper
                            ? 'Enable inter-procedural expansion when helper bodies need to be merged into the caller CFG.'
                            : 'Enable semantic or dynamic checks if the CFG shape is high but the task meaning is uncertain.'
                        }
                      };
                    }
                    function firstMethodName(source, fallback) {
                      const owner = typeName(source, 'Code');
                      const match = source.match(/\\b(?:(?:public|private|protected|static|final|synchronized|abstract|native|strictfp)\\s+)*[A-Za-z_$][\\w$<>\\[\\], ?]*\\s+([A-Za-z_$][\\w$]*)\\s*\\(/);
                      return `${owner}.${match ? match[1] : fallback}()`;
                    }
                    function helperMethodName(source, fallback) {
                      const matches = [...source.matchAll(/\\b(?:private|public|protected)?\\s*(?:static\\s+)?(?:boolean|int|String|double|long|void)\\s+([A-Za-z_$][\\w$]*)\\s*\\(/g)]
                        .map(match => match[1])
                        .filter(name => !/^(calculateRisk|scoreOrder|countValid|main)$/.test(name));
                      return matches.find(name => /valid|helper|looks|clean|normalize|suspicious|risk/i.test(name)) || matches[0] || fallback;
                    }
                    function buildCfgBlockSets(looksLikeHelper) {
	                      const main = {
	                        left: [
	                          { id: 'L1', x: 50, y: 6, name: 'start', display: 'Start', detail: 'read order inputs', meaning: 'The method receives order, inventory, channel, and device inputs.', raw: 'BB L1\\nread quantities, emails, country, coupon, stock, channel, device\\nscore = 0' },
	                          { id: 'L2', x: 50, y: 16, name: 'input guard', display: 'Input guard', detail: 'reject missing arrays', kind: 'branch', meaning: 'Rejects missing order data before scoring.', raw: 'BB L2\\nif quantities == null || emails == null\\n  return 100' },
	                          { id: 'L3', x: 50, y: 27, name: 'quantity risk', display: 'Quantity risk', detail: 'loop and score quantities', kind: 'helper', meaning: 'Computes risk from item quantities.', raw: 'BB L3\\nfor quantity in quantities\\n  threshold branches' },
	                          { id: 'L4', x: 28, y: 39, name: 'inventory branch', display: 'Inventory branch', detail: 'stock pressure loop', kind: 'branch', meaning: 'Checks requested quantity against available stock.', raw: 'BB L4\\nrequested += quantity\\nif requested > stock break' },
	                          { id: 'L5', x: 72, y: 39, name: 'email branch', display: 'Email branch', detail: 'temporary or suspicious', kind: 'branch', meaning: 'Adds risk when the email looks suspicious.', raw: 'BB L5\\nif temp email or promo marker\\n  risk += points' },
	                          { id: 'L6', x: 24, y: 53, name: 'region branch', display: 'Region branch', detail: 'country risk checks', kind: 'branch', meaning: 'Normalizes country and adds regional risk.', raw: 'BB L6\\nif CN/BR/IN add risk' },
	                          { id: 'L7', x: 50, y: 53, name: 'coupon branch', display: 'Coupon branch', detail: 'promo and discount rules', kind: 'branch', meaning: 'Scores coupon text and promotional signals.', raw: 'BB L7\\nfree/vip/long coupon checks' },
	                          { id: 'L8', x: 76, y: 53, name: 'channel branch', display: 'Channel branch', detail: 'source and device tree', kind: 'branch', meaning: 'Scores marketplace, mobile, store, and unknown channel cases.', raw: 'BB L8\\nif marketplace/mobile/store/default\\n  nested device check' },
	                          { id: 'L9', x: 35, y: 70, name: 'stability loop', display: 'Stability loop', detail: 'repeat and blank checks', kind: 'helper', meaning: 'Checks repeated quantities and blank email density.', raw: 'BB L9\\nloop quantities for repeats\\nloop emails for blanks' },
	                          { id: 'L10', x: 65, y: 70, name: 'fallback branch', display: 'Fallback branch', detail: 'review override', kind: 'branch', meaning: 'Adds review risk for high scores or blank channels.', raw: 'BB L10\\nif score > 80\\nif blank channel\\nif store override' },
	                          { id: 'L11', x: 50, y: 91, name: 'output', display: 'Output', detail: 'cap and return risk score', kind: 'return', meaning: 'Returns the final risk score after capping it at 100.', raw: 'BB L11\\nreturn capScore(score)' }
	                        ],
	                        right: [
	                          { id: 'R1', x: 50, y: 6, name: 'start', display: 'Start', detail: 'read order inputs', meaning: 'The method receives item, supply, source, and client inputs.', raw: 'BB R1\\nread items, contacts, region, promotion, available, source, client\\ntotal = 0' },
	                          { id: 'R2', x: 50, y: 16, name: 'input guard', display: 'Input guard', detail: 'reject missing arrays', kind: 'branch', meaning: 'Rejects missing order data before scoring.', raw: 'BB R2\\nif items == null || contacts == null\\n  return 100' },
	                          { id: 'R3', x: 50, y: 27, name: 'item risk', display: 'Item risk', detail: 'loop and score items', kind: 'helper', meaning: 'Computes risk from item quantities.', raw: 'BB R3\\nfor item in items\\n  threshold branches' },
	                          { id: 'R4', x: 28, y: 39, name: 'supply branch', display: 'Supply branch', detail: 'available stock loop', kind: 'branch', meaning: 'Checks requested item count against available supply.', raw: 'BB R4\\nrequested += item\\nif requested > available break' },
	                          { id: 'R5', x: 72, y: 39, name: 'contact branch', display: 'Contact branch', detail: 'temporary or suspicious', kind: 'branch', meaning: 'Adds risk when the contact looks suspicious.', raw: 'BB R5\\nif temp contact or promo marker\\n  score += points' },
	                          { id: 'R6', x: 24, y: 53, name: 'region branch', display: 'Region branch', detail: 'clean region checks', kind: 'branch', meaning: 'Cleans region and adds regional risk.', raw: 'BB R6\\nif CN/BR/IN add risk' },
	                          { id: 'R7', x: 50, y: 53, name: 'promotion branch', display: 'Promotion branch', detail: 'promo and discount rules', kind: 'branch', meaning: 'Scores promotion text and discount signals.', raw: 'BB R7\\nfree/vip/long promotion checks' },
	                          { id: 'R8', x: 76, y: 53, name: 'source branch', display: 'Source branch', detail: 'switch over source', kind: 'branch', meaning: 'Scores marketplace, mobile, store, and unknown source cases.', raw: 'BB R8\\nswitch source\\n  marketplace/mobile/store/default' },
	                          { id: 'R9', x: 35, y: 70, name: 'repeat loop', display: 'Repeat loop', detail: 'repeat and blank checks', kind: 'helper', meaning: 'Checks repeated items and blank contact density.', raw: 'BB R9\\nloop items for repeats\\nloop contacts for blanks' },
	                          { id: 'R10', x: 65, y: 70, name: 'fallback branch', display: 'Fallback branch', detail: 'review override', kind: 'branch', meaning: 'Adds review risk for high totals or blank source.', raw: 'BB R10\\nif total > 80\\nif blank source\\nif store override' },
	                          { id: 'R11', x: 50, y: 91, name: 'output', display: 'Output', detail: 'cap and return risk score', kind: 'return', meaning: 'Returns the final risk score after capping it at 100.', raw: 'BB R11\\nreturn capScore(total)' }
	                        ],
	                        leftEdges: [
	                          { from: 'L1', to: 'L2' },
	                          { from: 'L2', to: 'L3' },
	                          { from: 'L3', to: 'L4' },
	                          { from: 'L3', to: 'L5' },
	                          { from: 'L4', to: 'L6' },
	                          { from: 'L5', to: 'L8' },
	                          { from: 'L6', to: 'L7' },
	                          { from: 'L7', to: 'L9' },
	                          { from: 'L8', to: 'L10' },
	                          { from: 'L9', to: 'L11' },
	                          { from: 'L10', to: 'L11' },
	                          { from: 'L9', to: 'L3', kind: 'loop', side: 'left' },
	                          { from: 'L10', to: 'L8', kind: 'loop', side: 'right' }
	                        ],
                    """, """
                        rightEdges: [
	                          { from: 'R1', to: 'R2' },
	                          { from: 'R2', to: 'R3' },
	                          { from: 'R3', to: 'R4' },
	                          { from: 'R3', to: 'R5' },
	                          { from: 'R4', to: 'R6' },
	                          { from: 'R5', to: 'R8' },
	                          { from: 'R6', to: 'R7' },
	                          { from: 'R7', to: 'R9' },
	                          { from: 'R8', to: 'R10' },
	                          { from: 'R9', to: 'R11' },
	                          { from: 'R10', to: 'R11' },
	                          { from: 'R9', to: 'R3', kind: 'loop', side: 'left' },
	                          { from: 'R10', to: 'R8', kind: 'loop', side: 'right' }
	                        ],
	                        matches: [
	                          { left: 'L1', right: 'R1', label: 'start', friendly: 'Inputs matched', distance: 0.05 },
	                          { left: 'L2', right: 'R2', label: 'input guard', friendly: 'Input guard matched', distance: 0.03 },
	                          { left: 'L3', right: 'R3', label: 'quantity risk', friendly: 'Quantity scoring matched', distance: 0.12 },
	                          { left: 'L4', right: 'R4', label: 'inventory branch', friendly: 'Inventory pressure matched', distance: 0.14 },
	                          { left: 'L5', right: 'R5', label: 'email branch', friendly: 'Contact branch matched', distance: 0.10 },
	                          { left: 'L6', right: 'R6', label: 'region branch', friendly: 'Region branch changed', distance: 0.18 },
	                          { left: 'L7', right: 'R7', label: 'coupon branch', friendly: 'Promotion branch matched', distance: 0.15 },
	                          { left: 'L8', right: 'R8', label: 'channel branch', friendly: 'If/switch branch matched', distance: 0.21 },
	                          { left: 'L9', right: 'R9', label: 'stability loop', friendly: 'Repeat loop matched', distance: 0.08 },
	                          { left: 'L10', right: 'R10', label: 'fallback branch', friendly: 'Fallback branch matched', distance: 0.07 },
	                          { left: 'L11', right: 'R11', label: 'output', friendly: 'Output matched', distance: 0.03 }
	                        ]
	                      };
                      return {
                        main,
                        constructor: {
                          left: [
                            { id: 'CL1', x: 50, y: 24, name: 'start', display: 'Start', detail: 'create object', meaning: 'The class instance is created.', raw: 'BB CL1\\nload this\\ncall Object.<init>()' },
                            { id: 'CL2', x: 50, y: 70, name: 'finish', display: 'Finish', detail: 'return constructed object', kind: 'return', meaning: 'Constructor exits without meaningful domain logic.', raw: 'BB CL2\\nreturn' }
                          ],
                          right: [
                            { id: 'CR1', x: 50, y: 24, name: 'start', display: 'Start', detail: 'create object', meaning: 'The class instance is created.', raw: 'BB CR1\\nload this\\ncall Object.<init>()' },
                            { id: 'CR2', x: 50, y: 70, name: 'finish', display: 'Finish', detail: 'return constructed object', kind: 'return', meaning: 'Constructor exits without meaningful domain logic.', raw: 'BB CR2\\nreturn' }
                          ],
                          leftEdges: [
                            { from: 'CL1', to: 'CL2' }
                          ],
                          rightEdges: [
                            { from: 'CR1', to: 'CR2' }
                          ],
                          matches: [
                            { left: 'CL1', right: 'CR1', label: 'start', friendly: 'Setup matched', distance: 0.00 },
                            { left: 'CL2', right: 'CR2', label: 'finish', friendly: 'Finish matched', distance: 0.00 }
                          ]
                        },
                        helper: {
                          left: [
                            { id: 'HL1', x: 50, y: 18, name: 'main context', display: 'Main context', detail: 'risk score branch in caller', meaning: 'A branch inside the main method decides whether to add risk.', raw: 'BB HL1\\nif isSuspiciousEmail(email)\\n  score += 25' },
                            { id: 'HL2', x: 34, y: 50, name: 'local condition', display: 'Local condition', detail: 'check email shape', kind: 'branch', meaning: 'The caller relies on a condition that can be represented by a smaller helper.', raw: 'BB HL2\\nlower = email.toLowerCase()\\nendsWith tempmail or contains test' },
                            { id: 'HL3', x: 66, y: 78, name: 'update', display: 'Update', detail: 'add risk if condition holds', meaning: 'Updates the score when the condition is true.', raw: 'BB HL3\\nscore += 25' }
                          ],
                          right: [
                            { id: 'HR1', x: 50, y: 18, name: 'helper input', display: 'Helper input', detail: 'receive one value', meaning: 'The helper receives the value it needs to check.', raw: 'BB HR1\\nread contact parameter' },
                            { id: 'HR2', x: 34, y: 50, name: 'helper guard', display: 'Helper guard', detail: 'handle missing value', kind: 'branch', meaning: 'The helper checks a boundary case before applying the detailed predicate.', raw: 'BB HR2\\nif contact == null return true' },
                            { id: 'HR3', x: 66, y: 78, name: 'helper condition', display: 'Helper condition', detail: 'return boolean result', kind: 'return', meaning: 'Returns whether the value matches the suspicious condition.', raw: 'BB HR3\\nreturn lower.endsWith(...) || lower.contains(...)' }
                          ],
                          leftEdges: [
                            { from: 'HL1', to: 'HL2' },
                            { from: 'HL2', to: 'HL3' },
                            { from: 'HL3', to: 'HL2', kind: 'loop', side: 'left' }
                          ],
                          rightEdges: [
                            { from: 'HR1', to: 'HR2' },
                            { from: 'HR2', to: 'HR3' },
                            { from: 'HR3', to: 'HR2', kind: 'loop', side: 'right' }
                          ],
                          matches: [
                            { left: 'HL1', right: 'HR1', label: 'context', friendly: 'Caller context linked', distance: 0.29 },
                            { left: 'HL2', right: 'HR3', label: 'condition', friendly: 'Condition logic linked', distance: looksLikeHelper ? 0.16 : 0.24 },
                            { left: 'HL3', right: 'HR3', label: 'effect', friendly: 'Update depends on helper result', distance: 0.33 }
                          ]
                        }
                      };
                    }
                    function typeName(source, fallback) {
                      const match = source.match(/\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)/);
                      return match ? match[1] : fallback;
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
                        return `We are ${confidence} confident that the files do not show a clear copy pattern.`;
                      }
                      return `We are ${confidence} confident, and ${escapeHtml(scope.sentence)}.`;
                    }
                    function differenceSummary(cloneType) {
                      const map = {
                        T1: 'Almost none',
                        T2: 'Mostly renamed',
                        T3: 'Real edits',
                        T4_WEAK: 'Different shape',
                        NON_CLONE: 'No clear match',
                        INCONCLUSIVE: 'Unclear'
                      };
                      return map[cloneType] || 'Unclear';
                    }
                    function capitalize(value) {
                      const text = String(value || '');
                      return text ? text.charAt(0).toUpperCase() + text.slice(1) : text;
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
                    function reasonBreakdownPanel(chain) {
                      const parts = [
                        ['Why it looks similar', chain.supportingEvidence, '#0f766e'],
                        ['Where it changed', chain.opposingEvidence, '#9a5b00'],
                        ['How much matched', chain.scopeEvidence, '#4b6f9f'],
                        ['What to check', chain.reliabilityWarnings, '#b42318']
                      ];
                      const total = parts.reduce((sum, part) => sum + part[1].length, 0);
                      const donut = donutGradient(parts, total);
                      return `<div class="breakdown-panel">
                        <div class="reason-donut" style="--donut:${donut}">
                          <div class="reason-center">
                            <div><strong>${total}</strong><span>reasons</span></div>
                          </div>
                        </div>
                        <div>
                          <div class="label">Reason Breakdown</div>
                          <div class="muted" style="margin-top:8px">Each ring segment shows how many highlighted reasons came from that group. The items below name those reasons directly.</div>
                          <div class="legend-grid">
                            ${parts.map(part => legendRow(part[0], part[1], part[2])).join('')}
                          </div>
                        </div>
                      </div>`;
                    }
                    function donutGradient(parts, total) {
                      if (total <= 0) {
                        return 'conic-gradient(#e7ebf0 0 100%)';
                      }
                      let cursor = 0;
                      const stops = [];
                      for (const part of parts) {
                        const count = part[1].length;
                        if (count <= 0) continue;
                        const start = cursor;
                        cursor += count / total * 100;
                        stops.push(`${part[2]} ${start.toFixed(2)}% ${cursor.toFixed(2)}%`);
                      }
                      return `conic-gradient(${stops.join(', ')})`;
                    }
                    function legendRow(label, items, color) {
                      const count = items.length;
                      return `<div class="legend-row">
                        <div class="legend-head">
                          <span class="legend-dot" style="--dot-color:${color}"></span>
                          <span>${escapeHtml(label)}</span>
                          <strong>${count}</strong>
                        </div>
                        <div class="reason-mini-list">
                          ${items.length ? items.map(reasonMini).join('') : '<div class="muted">No reasons in this group.</div>'}
                        </div>
                      </div>`;
                    }
                    function reasonMini(item) {
                      const tagOnly = isTagOnlyEvidence(item);
                      return `<div class="reason-mini" title="${escapeHtml(interpretationDisplay(item))}">
                        <span class="reason-mini-main">
                          <span class="reason-mini-name">${escapeHtml(signalDisplay(item.signal))}</span>
                          <span class="reason-mini-score">${escapeHtml(reasonScoreText(item))}</span>
                        </span>
                        ${tagOnly ? '<span class="signal-pill note">NOTE</span>'
                          : `<span class="signal-pill ${item.strength.toLowerCase()}">${escapeHtml(item.strength)}</span>`}
                      </div>`;
                    }
                    function reasonScoreText(item) {
                      if (isTagOnlyEvidence(item)) return 'Reliability note';
                      return `Score ${percent(Number(item.value))}`;
                    }
                    function isTagOnlyEvidence(item) {
                      return item.value === 'true' || item.category === 'RELIABILITY_WARNING';
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
                      if (hash < 0) {
                        const dot = methodId.lastIndexOf('.');
                        return dot >= 0 ? methodId.slice(dot + 1) : methodId;
                      }
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
                    function strongest(items) {
                      if (items.some(item => item.strength === 'HIGH')) return 'High';
                      if (items.some(item => item.strength === 'MEDIUM')) return 'Medium';
                      if (items.some(item => item.strength === 'LOW')) return 'Low';
                      return 'No';
                    }
                    function signalDisplay(signal) {
                      const map = {
                        file_exact_normalized_match: 'Exact cleaned-code match',
                        structural_exactness_avg: 'Structure match',
                        method_similarity_strength: 'Function-to-function match',
                        modification_strength: 'Evidence of edits',
                        S5: 'Shared API usage',
                        coverage_strength: 'Unmatched code amount',
                        partial_clone_signal: 'Partial-copy pattern',
                        coverage_asymmetry: 'One-sided coverage',
                        confirmed_ratio: 'Two-way confirmation',
                        'coverage_A/B': 'Coverage in both files',
                        spread_avg: 'Mixed method results',
                        CLASS_CONTEXT_WEAK: 'Weak class context',
                        TRIVIAL_METHOD_HEAVY: 'Too many tiny methods',
                        S4_COST_RISK: 'Expensive structure check',
                        S5_NOT_APPLICABLE: 'Not enough API usage',
                        LOW_EVIDENCE: 'Limited supporting clues',
                        S1_if_reliable: 'File context',
                        match_score_avg: 'Match score',
                        magnitude_avg: 'Overall match',
                        scope_confidence: 'Match area',
                        evidence_strength: 'Reason strength',
                        pipeline_reliability: 'Checks run'
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
                """);
    }
}
