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
                      grid-template-columns: repeat(2, minmax(0, 1fr));
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
                      grid-template-columns: minmax(0, 1fr) 230px;
                      gap: 14px;
                      align-items: stretch;
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
                      margin-bottom: 8px;
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
                    .cfg-tree-grid {
                      display: grid;
                      grid-template-columns: minmax(220px, 1fr) 120px minmax(220px, 1fr);
                      gap: 14px;
                      align-items: start;
                    }
                    .cfg-graph-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(280px, 1fr));
                      gap: 12px;
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
                      min-height: 460px;
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
                    }
                    .cfg-edge {
                      stroke: #8795a7;
                      stroke-width: 1.15;
                      stroke-linecap: round;
                      fill: none;
                      opacity: 0.86;
                    }
                    .cfg-edge.loop {
                      stroke: #d49535;
                      stroke-dasharray: 5 5;
                    }
                    .cfg-graph-canvas .cfg-node {
                      position: absolute;
                      width: 118px;
                      min-height: 56px;
                      transform: translate(-50%, -50%);
                      z-index: 2;
                    }
                    .cfg-graph-canvas .cfg-node::after {
                      display: none;
                    }
                    .cfg-match-panel {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 6px 8px;
                      background: white;
                      display: grid;
                      gap: 8px;
                      align-content: start;
                      min-width: 0;
                      margin-bottom: 8px;
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
                      opacity: 0.48;
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
                      gap: 10px;
                      align-self: stretch;
                      min-width: 0;
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
                    let showCfgDistance = true;
                    let showCfgRaw = true;
                    let focusCfgMatch = false;
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
                    document.querySelector('#sampleBtn').addEventListener('click', () => {
                      sourceA.value = cfgSampleA;
                      sourceB.value = cfgSampleB;
                      fileA.value = 'InlineGuard.java';
                      fileB.value = 'HelperGuard.java';
                      fileAEdited = false;
                      fileBEdited = false;
                    });
                    fileA.addEventListener('input', () => { fileAEdited = true; });
                    fileB.addEventListener('input', () => { fileBEdited = true; });
                    sourceA.addEventListener('input', () => updateInferredFileName(sourceA, fileA, 'Code 1.java', () => fileAEdited));
                    sourceB.addEventListener('input', () => updateInferredFileName(sourceB, fileB, 'Code 2.java', () => fileBEdited));
                    document.querySelector('#chooseModeBtn').addEventListener('click', showModeChoice);
                    document.querySelector('#editCodeBtn').addEventListener('click', showInput);
                    document.querySelector('#runAstBtn').addEventListener('click', () => runAnalysis('ast'));
                    document.querySelector('#runCfgBtn').addEventListener('click', () => runAnalysis('cfg'));
                    document.querySelector('#rerunBtn').addEventListener('click', () => runAnalysis(reportMode));
                    document.querySelector('#backBtn').addEventListener('click', showInput);
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
                      const sections = reportMode === 'cfg' ? cfgSections : astSections;
                      reportHeading.textContent = reportMode === 'cfg' ? 'CFG Evidence Report' : 'Similarity Report';
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
                      } else {
                        if (activeSection === 'summary') sectionContent.innerHTML = renderSummary(reportData, files);
                        if (activeSection === 'methods') sectionContent.innerHTML = renderMethods(reportData, files);
                        if (activeSection === 'evidence') sectionContent.innerHTML = renderEvidence(reportData, files);
                        if (activeSection === 'json') sectionContent.innerHTML = renderJson(reportData);
                      }
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
                      const blocks = cfg.blockSets[method.blockSet] || cfg.blockSets.main;
                      const selected = selectedCfgBlock(blocks, selectedCfgNode);
                      return `<section class="section cfg-page">
                        <div class="cfg-detail-head">
                          <div class="cfg-detail-title">
                            <button class="secondary" data-cfg-back type="button">Back to Step 1</button>
                            <span class="cfg-category-tag ${escapeHtml(method.category)}">${escapeHtml(method.categoryLabel)}</span>
                            <div class="cfg-detail-main">
                              <h3>${escapeHtml(method.left)} -> ${escapeHtml(method.right)}</h3>
                              <div class="muted">${escapeHtml(method.explanation)}</div>
                            </div>
                          </div>
                          <div class="cfg-method-score">${percent(method.score)}</div>
                        </div>
                        <div class="cfg-visual">
                          <div class="cfg-explorer-main">
                            <div class="cfg-story">
                              <div>
                                <strong>${escapeHtml(method.storyTitle || cfg.story.title)}</strong>
                                <span>Click a graph node to inspect its matching evidence.</span>
                              </div>
                              <span class="tag">${percent(method.score)}</span>
                            </div>
                            <div class="cfg-explorer-toolbar">
                              <div>
                                <div class="label">Step 2: Compare CFG blocks</div>
                                <div class="muted">Nodes are execution blocks; arrows show control flow.</div>
                              </div>
                              <div class="cfg-toggle-group">
                                <button class="cfg-toggle ${showCfgDistance ? 'active' : ''}" data-cfg-toggle="distance" type="button">Show closeness</button>
                                <button class="cfg-toggle ${focusCfgMatch ? 'active' : ''}" data-cfg-toggle="focus" type="button">Highlight selected</button>
                                <button class="cfg-toggle ${showCfgRaw ? 'active' : ''}" data-cfg-toggle="raw" type="button">Show raw block text</button>
                              </div>
                            </div>
                            <div class="cfg-match-panel">
                              <h4>Matched blocks for ${escapeHtml(methodNameOnly(method.left))} -> ${escapeHtml(methodNameOnly(method.right))}</h4>
                              <div class="cfg-links ${showCfgDistance ? '' : 'hide-distance'}">
                                ${blocks.matches.map(match => renderCfgMatchLine(match, blocks)).join('')}
                              </div>
                            </div>
                            <div class="cfg-graph-grid">
                              ${renderCfgGraph(files.left.short, 'left', blocks)}
                              ${renderCfgGraph(files.right.short, 'right', blocks)}
                            </div>
                          </div>
                          <aside class="cfg-inspector">
                            <div>
                              <div class="label">Step 3: Selected block details</div>
                              <h3>${escapeHtml(selected.block.display)}</h3>
                              <div class="muted">${escapeHtml(selected.block.detail)}</div>
                            </div>
                            <div class="cfg-kv"><span>File</span><span>${selected.side === 'left' ? escapeHtml(files.left.short) : escapeHtml(files.right.short)}</span></div>
                            <div class="cfg-kv"><span>Matched with</span><span>${escapeHtml(selected.matchLabel)}</span></div>
                            <div class="cfg-kv"><span>Closeness</span><span>${selected.match ? escapeHtml(closenessText(selected.match.distance)) : 'No close match'}</span></div>
                            <div class="cfg-kv"><span>What it does</span><span>${escapeHtml(selected.block.meaning)}</span></div>
                            ${showCfgRaw ? `<div class="cfg-raw">${escapeHtml(selected.block.raw)}</div>` : ''}
                          </aside>
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
                              <marker id="arrow-${side}" markerWidth="5" markerHeight="5" refX="4.6" refY="2.5" orient="auto" markerUnits="strokeWidth">
                                <path d="M 0 0 L 5 2.5 L 0 5 z" fill="#8795a7"></path>
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
                        if (edge.kind === 'loop') {
                          const dx = edge.side === 'left' ? -18 : 18;
                          const midY = Math.min(from.y, to.y) - 10;
                          return `<path class="${cls}" d="M ${from.x} ${from.y} C ${from.x + dx} ${midY}, ${to.x + dx} ${midY}, ${to.x} ${to.y}" marker-end="url(#arrow-${side})"></path>`;
                        }
                        return `<line class="${cls}" x1="${from.x}" y1="${from.y}" x2="${to.x}" y2="${to.y}" marker-end="url(#arrow-${side})"></line>`;
                      }).join('');
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
                    function renderCfgMatchLine(match, blocks) {
                      const left = blocks.left.find(block => block.id === match.left);
                      const right = blocks.right.find(block => block.id === match.right);
                      const leftName = left ? (left.display || left.name) : match.left;
                      const rightName = right ? (right.display || right.name) : match.right;
                      return `<span><strong>${escapeHtml(leftName)} -> ${escapeHtml(rightName)}</strong><small>${escapeHtml(match.friendly || `${match.label} matched`)}${showCfgDistance ? ` · ${escapeHtml(closenessShort(match.distance))}` : ''}</small></span>`;
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
                          const method = reportData?.cfg?.methods?.[selectedPairIndex];
                          const blockSet = reportData?.cfg?.blockSets?.[method?.blockSet] || reportData?.cfg?.blockSets?.main;
                          selectedCfgNode = method?.defaultNode || blockSet?.left?.[0]?.id || 'L2';
                          cfgDetailOpen = true;
                          renderReport();
                        });
                      });
                      sectionContent.querySelectorAll('[data-cfg-back]').forEach(btn => {
                        btn.addEventListener('click', () => {
                          cfgDetailOpen = false;
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
                          selectedCfgNode = btn.dataset.cfgNode;
                          renderReport();
                        });
                      });
                      sectionContent.querySelectorAll('[data-cfg-toggle]').forEach(btn => {
                        btn.addEventListener('click', () => {
                          const toggle = btn.dataset.cfgToggle;
                          if (toggle === 'distance') showCfgDistance = !showCfgDistance;
                          if (toggle === 'focus') focusCfgMatch = !focusCfgMatch;
                          if (toggle === 'raw') showCfgRaw = !showCfgRaw;
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
                          { id: 'L1', x: 50, y: 9, name: 'start', display: 'Start', detail: 'read order inputs', meaning: 'The method receives quantities, email, and country values.', raw: 'BB L1\\nread quantities, email, country\\nscore = 0' },
                          { id: 'L2', x: 50, y: 26, name: 'quantity risk', display: 'Quantity risk', detail: 'call quantity scoring logic', kind: 'helper', meaning: 'Computes risk from item quantities before other checks.', raw: 'BB L2\\nscore += quantityRisk(quantities)' },
                          { id: 'L3', x: 29, y: 52, name: 'email branch', display: 'Email branch', detail: 'if suspicious, add risk', kind: 'branch', meaning: 'Adds risk when the email looks suspicious.', raw: 'BB L3\\nif isSuspiciousEmail(email)\\n  score += 25' },
                          { id: 'L4', x: 71, y: 52, name: 'country branch', display: 'Country branch', detail: 'if country is CN, add risk', kind: 'branch', meaning: 'Normalizes the country and adds a regional risk value.', raw: 'BB L4\\nif CN equals normalizeCountry(country)\\n  score += 5' },
                          { id: 'L5', x: 50, y: 83, name: 'output', display: 'Output', detail: 'cap and return risk score', kind: 'return', meaning: 'Returns the final risk score after capping it at 100.', raw: 'BB L5\\nreturn Math.min(score, 100)' }
                        ],
                        right: [
                          { id: 'R1', x: 50, y: 9, name: 'start', display: 'Start', detail: 'read order inputs', meaning: 'The method receives items, contact, and region values.', raw: 'BB R1\\nread items, contact, region\\ntotal = itemRiskScore(items)' },
                          { id: 'R2', x: 50, y: 26, name: 'quantity risk', display: 'Item risk', detail: 'call item scoring logic', kind: 'helper', meaning: 'Computes risk from item quantities before other checks.', raw: 'BB R2\\ntotal = itemRiskScore(items)' },
                          { id: 'R3', x: 29, y: 52, name: 'email branch', display: 'Email branch', detail: 'if temporary email, add risk', kind: 'branch', meaning: 'Adds risk when the contact email looks suspicious.', raw: 'BB R3\\nif looksLikeTemporaryEmail(contact)\\n  total += 25' },
                          { id: 'R4', x: 71, y: 52, name: 'country branch', display: 'Region branch', detail: 'clean region, then add risk', kind: 'branch', meaning: 'Cleans the region and adds a regional risk value.', raw: 'BB R4\\nnormalizedRegion = cleanRegion(region)\\nif CN equals normalizedRegion\\n  total += 5' },
                          { id: 'R5', x: 50, y: 83, name: 'output', display: 'Output', detail: 'cap and return risk score', kind: 'return', meaning: 'Returns the final risk score after capping it at 100.', raw: 'BB R5\\nreturn Math.min(100, total)' }
                        ],
                        leftEdges: [
                          { from: 'L1', to: 'L2' },
                          { from: 'L2', to: 'L3' },
                          { from: 'L2', to: 'L4' },
                          { from: 'L3', to: 'L5' },
                          { from: 'L4', to: 'L5' }
                        ],
                        rightEdges: [
                          { from: 'R1', to: 'R2' },
                          { from: 'R2', to: 'R3' },
                          { from: 'R2', to: 'R4' },
                          { from: 'R3', to: 'R5' },
                          { from: 'R4', to: 'R5' }
                        ],
                        matches: [
                          { left: 'L1', right: 'R1', label: 'start', friendly: 'Inputs matched', distance: 0.05 },
                          { left: 'L2', right: 'R2', label: 'quantity risk', friendly: 'Quantity scoring matched', distance: 0.12 },
                          { left: 'L3', right: 'R3', label: 'email branch', friendly: 'Email branch matched', distance: 0.10 },
                          { left: 'L4', right: 'R4', label: 'country branch', friendly: 'Country branch changed', distance: 0.18 },
                          { left: 'L5', right: 'R5', label: 'output', friendly: 'Output matched', distance: 0.03 }
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
                """;
    }
}
