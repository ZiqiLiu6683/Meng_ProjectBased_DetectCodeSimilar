import { useEffect, useMemo, useRef, useState } from "react";
import type {
  AnalysisDepth,
  AnalyzeRequest,
  AnalyzeResponse,
  CloneFamily,
  LineSpan,
  PreflightResponse,
  PreflightStatus,
  RegionVerdict,
  T4Mode,
} from "./types";
import { DEMO_LEFT, DEMO_RIGHT } from "./sample";

const FAMILY: Record<CloneFamily, { name: string; tone: string; soft: string }> = {
  T1: { name: "Identical", tone: "#1a7f37", soft: "#dafbe1" },
  T2: { name: "Renamed", tone: "#0969da", soft: "#ddf4ff" },
  T3: { name: "Near-miss", tone: "#9a6700", soft: "#fff8c5" },
  T4: { name: "Same behavior", tone: "#8250df", soft: "#fbefff" },
};

const TYPE_LABEL: Record<string, string> = {
  T1: "T1 · exact copy",
  T2: "T2 · renamed",
  T3: "T3 · near-miss",
  T4_CONFIRMED: "T4 · proven equivalent",
  T4_DYNAMIC_EVIDENCE: "T4 · evidence (sampled)",
  POSSIBLE_T4_CANDIDATE: "T4 · possible (cross-method)",
};

type StageDefinition = { key: string; label: string; hint: string };

const ADVANCED_STAGES: StageDefinition[] = [
  { key: "compile", label: "Compile", hint: "compiling both files" },
  { key: "graph", label: "Graph", hint: "building the dependence graph" },
  { key: "smt", label: "Equivalence", hint: "proving equivalence with SMT" },
  { key: "dynamic", label: "Behaviour", hint: "sampling runtime behaviour" },
  { key: "regions", label: "Regions", hint: "selecting boundary-free regions" },
  { key: "classify", label: "Classify", hint: "classifying each region" },
];

// --- Minimal, dependency-free Java highlighter (escapes everything it emits) ---
const KEYWORDS = new Set(
  ("abstract assert break case catch class const continue default do else enum extends final finally for goto if " +
    "implements import instanceof interface native new package private protected public return static strictfp super " +
    "switch synchronized this throw throws transient try volatile while var record yield sealed permits").split(" "),
);
const TYPES = new Set("boolean byte char double float int long short void".split(" "));
const TOKEN = /(\/\/[^\n]*)|("(?:\\.|[^"\\])*")|('(?:\\.|[^'\\])*')|(\d[\w.]*)|([A-Za-z_$][A-Za-z0-9_$]*)/g;

function esc(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function highlight(line: string): string {
  let out = "";
  let last = 0;
  let m: RegExpExecArray | null;
  TOKEN.lastIndex = 0;
  while ((m = TOKEN.exec(line))) {
    out += esc(line.slice(last, m.index));
    const t = m[0];
    let cls = "";
    if (m[1]) cls = "tok-comment";
    else if (m[2] || m[3]) cls = "tok-string";
    else if (m[4]) cls = "tok-number";
    else if (m[5]) cls = KEYWORDS.has(t) ? "tok-keyword" : TYPES.has(t) || /^[A-Z]/.test(t) ? "tok-type" : "";
    out += cls ? `<span class="${cls}">${esc(t)}</span>` : esc(t);
    last = m.index + t.length;
    if (m.index === TOKEN.lastIndex) TOKEN.lastIndex++;
  }
  out += esc(line.slice(last));
  return out || "&nbsp;";
}

export default function App() {
  const [view, setView] = useState<"input" | "loading" | "result">("input");
  const [leftName, setLeftName] = useState("Left.java");
  const [rightName, setRightName] = useState("Right.java");
  const [leftSource, setLeftSource] = useState(DEMO_LEFT);
  const [rightSource, setRightSource] = useState(DEMO_RIGHT);
  const [data, setData] = useState<AnalyzeResponse | null>(null);
  const [stage, setStage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [analysisDepth, setAnalysisDepth] = useState<AnalysisDepth>("SOURCE_AST");
  const [checkT4, setCheckT4] = useState(false);
  const [runtimeSampling, setRuntimeSampling] = useState(false);
  const [runningRequest, setRunningRequest] = useState<AnalyzeRequest | null>(null);
  const [preflightStatus, setPreflightStatus] = useState<PreflightStatus>("idle");
  const [preflight, setPreflight] = useState<PreflightResponse | null>(null);
  const [preflightError, setPreflightError] = useState<string | null>(null);
  const preflightSequence = useRef(0);
  const userSelectedDepth = useRef(false);

  useEffect(() => {
    if (view !== "input") return;
    if (!leftSource.trim() || !rightSource.trim()) {
      setPreflightStatus("idle");
      setPreflight(null);
      setPreflightError(null);
      return;
    }

    const sequence = ++preflightSequence.current;
    const controller = new AbortController();
    setPreflightStatus("checking");
    setPreflightError(null);
    const timer = window.setTimeout(async () => {
      try {
        const response = await fetch("/api/preflight", {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: new URLSearchParams({ leftSource, rightSource }).toString(),
          signal: controller.signal,
        });
        if (!response.ok) throw new Error(`backend returned ${response.status}`);
        const result = (await response.json()) as PreflightResponse;
        if (sequence !== preflightSequence.current) return;
        setPreflight(result);
        setPreflightStatus("ready");
        if (result.parseable && result.recommendedMode && !userSelectedDepth.current) {
          setAnalysisDepth(result.recommendedMode);
        }
      } catch (reason) {
        if (controller.signal.aborted || sequence !== preflightSequence.current) return;
        setPreflight(null);
        setPreflightError((reason as Error).message);
        setPreflightStatus("error");
      }
    }, 700);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [leftSource, rightSource, view]);

  async function analyzeRequest(request: AnalyzeRequest) {
    setError(null);
    setStage(null);
    setView("loading");
    try {
      const res = await fetch("/api/analyze", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          leftName: request.leftName,
          rightName: request.rightName,
          leftSource: request.leftSource,
          rightSource: request.rightSource,
          analysisDepth: request.analysisDepth,
          t4Mode: request.t4Mode,
        }).toString(),
      });
      if (!res.ok || !res.body) throw new Error(`backend returned ${res.status}`);
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buf = "";
      let finished = false;
      while (!finished) {
        const { value, done } = await reader.read();
        if (done) break;
        buf += decoder.decode(value, { stream: true });
        let sep: number;
        while ((sep = buf.indexOf("\n\n")) >= 0) {
          const frame = buf.slice(0, sep);
          buf = buf.slice(sep + 2);
          let ev = "message";
          let payload = "";
          for (const line of frame.split("\n")) {
            if (line.startsWith("event:")) ev = line.slice(6).trim();
            else if (line.startsWith("data:")) payload += line.slice(5).trim();
          }
          if (ev === "stage") setStage((JSON.parse(payload) as { stage: string }).stage);
          else if (ev === "result") {
            setData(JSON.parse(payload) as AnalyzeResponse);
            setView("result");
            finished = true;
          } else if (ev === "error") throw new Error((JSON.parse(payload) as { error: string }).error);
        }
      }
    } catch (e) {
      const message = (e as Error).message;
      setError(message.startsWith("Quick scan is unavailable")
        ? message
        : `Couldn't reach the region backend (${message}). Start it with the semantic profile and try again.`);
      setView("input");
    }
  }

  function analyze() {
    if (preflightStatus !== "ready" || !preflight?.parseable) return;
    if (analysisDepth === "SOURCE_AST" && preflight.quickAllowed === false) return;
    const request = {
      leftName,
      rightName,
      leftSource,
      rightSource,
      analysisDepth,
      t4Mode: selectedT4Mode(analysisDepth, checkT4, runtimeSampling),
    } satisfies AnalyzeRequest;
    setRunningRequest(request);
    void analyzeRequest(request);
  }

  function chooseAnalysisDepth(value: AnalysisDepth) {
    userSelectedDepth.current = true;
    setAnalysisDepth(value);
  }

  function useRecommendedDepth() {
    if (!preflight?.recommendedMode) return;
    userSelectedDepth.current = true;
    setAnalysisDepth(preflight.recommendedMode);
  }

  function showDemo() {
    const request: AnalyzeRequest = {
      leftName: "Left.java",
      rightName: "Right.java",
      leftSource: DEMO_LEFT,
      rightSource: DEMO_RIGHT,
      analysisDepth,
      t4Mode: selectedT4Mode(analysisDepth, checkT4, runtimeSampling),
    };
    setLeftName(request.leftName);
    setRightName(request.rightName);
    setLeftSource(request.leftSource);
    setRightSource(request.rightSource);
    setRunningRequest(request);
    void analyzeRequest(request);
  }

  return (
    <div className="min-h-full flex flex-col">
      <Header
        onHome={() => setView("input")}
        showNew={view === "result"}
        backend={view === "result" ? data?.regionBackend : undefined}
        analysisMode={view === "result" ? data?.analysisMode : undefined}
      />
      {view === "input" && (
        <InputView
          leftName={leftName}
          rightName={rightName}
          leftSource={leftSource}
          rightSource={rightSource}
          setLeftName={setLeftName}
          setRightName={setRightName}
          setLeftSource={setLeftSource}
          setRightSource={setRightSource}
          analysisDepth={analysisDepth}
          setAnalysisDepth={chooseAnalysisDepth}
          checkT4={checkT4}
          setCheckT4={setCheckT4}
          runtimeSampling={runtimeSampling}
          setRuntimeSampling={setRuntimeSampling}
          onAnalyze={analyze}
          onDemo={showDemo}
          error={error}
          preflightStatus={preflightStatus}
          preflight={preflight}
          preflightError={preflightError}
          onUseRecommended={useRecommendedDepth}
        />
      )}
      {view === "loading" && runningRequest && (
        <LoadingView
          leftName={runningRequest.leftName}
          rightName={runningRequest.rightName}
          stage={stage}
          analysisDepth={runningRequest.analysisDepth}
          t4Mode={runningRequest.t4Mode}
        />
      )}
      {view === "result" && data && <ResultView data={data} />}
    </div>
  );
}

function Header(props: {
  onHome: () => void;
  showNew?: boolean;
  backend?: boolean;
  analysisMode?: AnalyzeResponse["analysisMode"];
}) {
  return (
    <header className="sticky top-0 z-10 bg-panel/70 backdrop-blur border-b border-line relative">
      <div
        aria-hidden
        className="absolute inset-x-0 bottom-0 h-px"
        style={{ background: "linear-gradient(90deg,transparent 6%,rgba(124,92,255,0.5),rgba(6,182,212,0.5),transparent 94%)" }}
      />
      <div className="mx-auto max-w-[1600px] px-6 h-14 flex items-center justify-between">
        <button
          onClick={props.onHome}
          title="Back to start"
          className="group flex items-center gap-2.5 -ml-1 pl-1 pr-2 py-1 rounded-lg hover:bg-canvas transition"
        >
          <span
            className="w-6 h-6 rounded-lg flex items-center justify-center group-hover:scale-105 transition-transform shadow-[0_2px_8px_rgba(109,94,252,0.35)]"
            style={{ background: "linear-gradient(135deg,#7c5cff,#06b6d4)" }}
          >
            <span className="w-2.5 h-2.5 rounded-[3px] bg-white" />
          </span>
          <span
            className="font-semibold tracking-tight text-transparent bg-clip-text"
            style={{ backgroundImage: "linear-gradient(135deg,#5b4bd6,#0e7490)" }}
          >
            CodeSim
          </span>
        </button>
        <div className="flex items-center gap-3">
          {props.backend !== undefined && (
            <span
              className={`text-xs font-medium px-2.5 py-1 rounded-full ${
                props.analysisMode === "SOURCE_AST"
                  ? "text-accent-ink bg-accent-soft"
                  : props.backend
                    ? "text-t1 bg-t1-soft"
                    : "text-t3 bg-t3-soft"
              }`}
            >
              {props.analysisMode === "SOURCE_AST"
                ? "Quick source + AST"
                : props.analysisMode?.includes("STUBBED")
                ? "WALA + generated context"
                : props.analysisMode?.includes("PROJECT_CONTEXT")
                  ? "WALA + project context"
                  : props.backend
                    ? "WALA standalone"
                    : "source-only fallback"}
            </span>
          )}
          {props.showNew && (
            <button
              onClick={props.onHome}
              className="text-sm font-medium px-3 py-1.5 rounded-lg border border-line bg-surface hover:bg-canvas transition"
            >
              New comparison
            </button>
          )}
        </div>
      </div>
    </header>
  );
}

function InputView(props: {
  leftName: string;
  rightName: string;
  leftSource: string;
  rightSource: string;
  setLeftName: (v: string) => void;
  setRightName: (v: string) => void;
  setLeftSource: (v: string) => void;
  setRightSource: (v: string) => void;
  analysisDepth: AnalysisDepth;
  setAnalysisDepth: (v: AnalysisDepth) => void;
  checkT4: boolean;
  setCheckT4: (v: boolean) => void;
  runtimeSampling: boolean;
  setRuntimeSampling: (v: boolean) => void;
  onAnalyze: () => void;
  onDemo: () => void;
  error: string | null;
  preflightStatus: PreflightStatus;
  preflight: PreflightResponse | null;
  preflightError: string | null;
  onUseRecommended: () => void;
}) {
  const parseBlocked = props.preflightStatus === "ready" && props.preflight?.parseable === false;
  const quickBlocked = props.preflightStatus === "ready"
    && props.preflight?.parseable === true
    && props.preflight.quickAllowed === false;
  const checking = props.preflightStatus === "checking" || props.preflightStatus === "idle";
  const canAnalyze = !checking
    && props.preflightStatus === "ready"
    && props.preflight?.parseable === true
    && !(props.analysisDepth === "SOURCE_AST" && quickBlocked);
  const quickRecommended = props.preflight?.parseable === true
    && props.preflight.recommendedMode === "SOURCE_AST";
  const deepRecommended = props.preflight?.parseable === true
    && props.preflight.recommendedMode === "WALA_REGIONS";

  return (
    <main className="relative mx-auto w-full max-w-[1240px] px-6 py-12 flex-1">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 -top-10 h-56 opacity-60 blur-2xl"
        style={{
          background:
            "radial-gradient(60% 100% at 20% 0%, rgba(124,92,255,0.18), transparent 60%), radial-gradient(50% 100% at 80% 0%, rgba(6,182,212,0.16), transparent 60%)",
        }}
      />
      <div className="relative mb-8 max-w-2xl">
        <span
          className="inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full mb-4"
          style={{ color: "#4b3fd6", background: "#eeecff" }}
        >
          <span className="w-1.5 h-1.5 rounded-full" style={{ background: "linear-gradient(135deg,#7c5cff,#06b6d4)" }} />
          Boundary-free region clone detection
        </span>
        <h1 className="text-3xl font-semibold tracking-tight">Compare two Java files</h1>
        <p className="text-ink-soft mt-2 leading-relaxed">
          Each matched region is typed T1–T3 on its own, and behaviourally-equivalent methods surface
          as T4 — no whole-file verdict, just the regions that actually match.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        <Editor label="A" tone="#0969da" name={props.leftName} setName={props.setLeftName} value={props.leftSource} onChange={props.setLeftSource} />
        <Editor label="B" tone="#1a7f37" name={props.rightName} setName={props.setRightName} value={props.rightSource} onChange={props.setRightSource} />
      </div>

      <PreflightPanel
        status={props.preflightStatus}
        result={props.preflight}
        networkError={props.preflightError}
        selectedDepth={props.analysisDepth}
        onUseRecommended={props.onUseRecommended}
      />

      <fieldset className="mt-6 rounded-2xl border border-line bg-surface p-4 shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
        <legend className="px-1 text-[13px] font-semibold text-ink">Analysis depth</legend>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <AnalysisDepthCard
            checked={props.analysisDepth === "SOURCE_AST"}
            value="SOURCE_AST"
            onChange={props.setAnalysisDepth}
            title="Quick source scan"
            badge={quickBlocked ? "Not suitable" : quickRecommended ? "Recommended" : "Small inputs"}
            badgeTone={quickBlocked ? "danger" : quickRecommended ? "success" : "neutral"}
            description={quickBlocked
              ? `This input may require up to ${formatNumber(props.preflight?.quickComparisonUpperBound)} source-region comparisons.`
              : "Usually fastest for smaller files with fewer methods. Source and AST matching for T1–T3."}
            disabled={quickBlocked || parseBlocked}
          />
          <AnalysisDepthCard
            checked={props.analysisDepth === "WALA_REGIONS"}
            value="WALA_REGIONS"
            onChange={props.setAnalysisDepth}
            title="Deep structural analysis"
            badge={deepRecommended ? "Recommended" : "Large & reorganized"}
            badgeTone={deepRecommended ? "success" : "neutral"}
            description="Control/data-flow and cross-method region analysis. Better suited to large or many-method inputs."
            disabled={parseBlocked}
          />
        </div>

        {props.analysisDepth === "WALA_REGIONS" && (
          <div className="mt-4 rounded-xl border border-line bg-panel/50 p-4">
            <label className="flex items-start gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={props.checkT4}
                onChange={(e) => props.setCheckT4(e.target.checked)}
                className="mt-0.5 h-4 w-4 rounded border-line accent-[#6d5efc]"
              />
              <span>
                <span className="block text-[13px] font-semibold">Check behavioural similarity (T4)</span>
                <span className="block mt-0.5 text-[12px] leading-5 text-ink-soft">
                  Attempts formal equivalence proof for supported methods.
                </span>
              </span>
            </label>

            {props.checkT4 && (
              <label className="mt-3 ml-7 flex items-start gap-3 cursor-pointer rounded-lg border border-[#e5d8ff] bg-[#fdf9ff] px-3 py-2.5">
                <input
                  type="checkbox"
                  checked={props.runtimeSampling}
                  onChange={(e) => props.setRuntimeSampling(e.target.checked)}
                  className="mt-0.5 h-4 w-4 rounded border-line accent-[#8250df]"
                />
                <span>
                  <span className="block text-[12px] font-semibold text-[#6e40c9]">Also run bounded runtime sampling</span>
                  <span className="block mt-0.5 text-[11px] leading-4 text-ink-soft">
                    Executes unresolved methods with generated inputs. Use trusted code only; matching samples are evidence, not proof.
                  </span>
                </span>
              </label>
            )}
          </div>
        )}
      </fieldset>

      {props.error && (
        <div className="mt-5 text-sm text-t3 bg-t3-soft rounded-xl px-4 py-3">{props.error}</div>
      )}

      <div className="mt-6 flex items-center gap-3">
        <button
          onClick={props.onAnalyze}
          disabled={!canAnalyze}
          className="text-sm font-semibold px-5 py-2.5 rounded-lg text-white transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:brightness-100 shadow-[0_4px_14px_rgba(109,94,252,0.35)]"
          style={{ background: "linear-gradient(135deg,#7c5cff,#06b6d4)" }}
        >
          {checking
            ? "Checking input…"
            : props.analysisDepth === "SOURCE_AST" && quickBlocked
              ? "Choose deep analysis"
              : props.analysisDepth === "SOURCE_AST"
                ? "Run quick scan"
                : props.checkT4
                  ? "Run full analysis"
                  : "Run structural analysis"}
        </button>
        <button
          onClick={props.onDemo}
          className="text-sm font-medium px-4 py-2.5 rounded-lg text-ink-soft hover:text-accent-ink hover:bg-accent-soft transition"
        >
          Analyze demo input
        </button>
      </div>
    </main>
  );
}

function PreflightPanel(props: {
  status: PreflightStatus;
  result: PreflightResponse | null;
  networkError: string | null;
  selectedDepth: AnalysisDepth;
  onUseRecommended: () => void;
}) {
  const result = props.result;

  if (props.status === "idle" || props.status === "checking") {
    return (
      <section
        className="mt-6 rounded-2xl border border-[#d8d4ff] bg-[#faf9ff] px-5 py-4"
        role="status"
        aria-atomic="true"
      >
        <div className="flex items-center gap-3">
          <span className="h-2.5 w-2.5 rounded-full bg-[#7c5cff] animate-pulse" />
          <div>
            <h2 className="text-[13px] font-semibold">Checking input</h2>
            <p className="mt-0.5 text-[12px] text-ink-soft">
              Measuring source structure before recommending an analysis mode…
            </p>
          </div>
        </div>
      </section>
    );
  }

  if (props.status === "error") {
    return (
      <section
        className="mt-6 rounded-2xl border border-[#f2cc60] bg-t3-soft px-5 py-4"
        role="status"
        aria-atomic="true"
      >
        <h2 className="text-[13px] font-semibold text-t3">Input check unavailable</h2>
        <p className="mt-1 text-[12px] leading-5 text-ink-soft">
          The server could not size this comparison ({props.networkError ?? "unknown error"}). Analysis is paused until the check succeeds.
        </p>
      </section>
    );
  }

  if (!result?.parseable) {
    return (
      <section
        className="mt-6 rounded-2xl border border-[#f2cc60] bg-t3-soft px-5 py-4"
        role="status"
        aria-atomic="true"
      >
        <h2 className="text-[13px] font-semibold text-t3">Check the Java syntax</h2>
        <p className="mt-1 text-[12px] leading-5 text-ink-soft">
          CodeSim could not parse both inputs, so it cannot recommend a safe mode yet.
        </p>
        {result?.error && (
          <details className="mt-2 text-[11px] text-ink-soft">
            <summary className="cursor-pointer font-medium">Parser detail</summary>
            <p className="mt-1 font-mono break-words">{result.error}</p>
          </details>
        )}
      </section>
    );
  }

  const high = result.workload === "HIGH";
  const moderate = result.workload === "MODERATE";
  const recommended = result.recommendedMode === "SOURCE_AST"
    ? "Quick source scan"
    : "Deep structural analysis";
  const recommendationChanged = result.recommendedMode !== props.selectedDepth;
  const panelClass = high
    ? "border-[#f3b7ae] bg-[#fff8f6]"
    : moderate
      ? "border-[#f2cc60] bg-[#fffdf2]"
      : "border-[#b7ebc6] bg-[#f6fff8]";
  const statusClass = high ? "text-[#b42318]" : moderate ? "text-t3" : "text-t1";
  const workloadLabel = high ? "High workload" : moderate ? "Moderate workload" : "Low workload";

  return (
    <section className={`mt-6 rounded-2xl border px-5 py-4 ${panelClass}`} aria-labelledby="input-check-title">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0" role="status" aria-atomic="true">
          <div className="flex items-center gap-2 flex-wrap">
            <h2 id="input-check-title" className="text-[13px] font-semibold">Input check</h2>
            <span className={`text-[10px] font-semibold rounded-full bg-white/80 px-2 py-0.5 ${statusClass}`}>
              {workloadLabel}
            </span>
          </div>
          <p className="mt-1.5 text-[13px] font-semibold">{recommended} is recommended for this input.</p>
          <p className="mt-1 text-[12px] leading-5 text-ink-soft">
            {high
              ? `Quick scan could require up to ${formatNumber(result.quickComparisonUpperBound)} source-region comparisons, above the temporary safety budget.`
              : moderate
                ? `Quick scan could require up to ${formatNumber(result.quickComparisonUpperBound)} source-region comparisons and may take several seconds.`
                : "This pair has a small source/AST comparison space, so a quick preview is appropriate."}
          </p>
        </div>
        {recommendationChanged && (
          <button
            type="button"
            onClick={props.onUseRecommended}
            className="shrink-0 rounded-lg border border-line bg-white px-3 py-2 text-[12px] font-semibold text-accent-ink shadow-sm hover:bg-accent-soft transition"
          >
            Use {result.recommendedMode === "SOURCE_AST" ? "Quick" : "Deep"}
          </button>
        )}
      </div>

      <div className="mt-3 grid grid-cols-1 gap-2 text-[11px] text-ink-soft sm:grid-cols-2">
        <div className="rounded-lg border border-white/80 bg-white/65 px-3 py-2">
          <span className="font-semibold text-ink">Left</span>
          {` · ${formatNumber(result.left?.lines)} lines · ${formatNumber(result.left?.methods)} methods`}
        </div>
        <div className="rounded-lg border border-white/80 bg-white/65 px-3 py-2">
          <span className="font-semibold text-ink">Right</span>
          {` · ${formatNumber(result.right?.lines)} lines · ${formatNumber(result.right?.methods)} methods`}
        </div>
      </div>

      <details className="mt-3 text-[11px] text-ink-soft">
        <summary className="cursor-pointer font-medium hover:text-ink">How this was estimated</summary>
        <p className="mt-1.5 leading-5">
          The check parses the current source and counts the regions Quick would compare. It does not classify clones or change either analysis pipeline.
          {` Current upper bound: ${formatNumber(result.quickComparisonUpperBound)}; temporary web budget: ${formatNumber(result.quickComparisonBudget)}.`}
        </p>
      </details>
    </section>
  );
}

function AnalysisDepthCard(props: {
  checked: boolean;
  value: AnalysisDepth;
  onChange: (value: AnalysisDepth) => void;
  title: string;
  badge: string;
  badgeTone: "neutral" | "success" | "danger";
  description: string;
  disabled?: boolean;
}) {
  const badgeClass = props.badgeTone === "success"
    ? "text-t1 bg-t1-soft"
    : props.badgeTone === "danger"
      ? "text-[#b42318] bg-[#fff0ee]"
      : "text-accent-ink bg-accent-soft";
  return (
    <label
      className={`relative flex items-start gap-3 rounded-xl border p-4 cursor-pointer transition ${
        props.disabled
          ? "border-line bg-panel/40 opacity-65 cursor-not-allowed"
          : props.checked
          ? "border-[#7c5cff] bg-[#f8f7ff] shadow-[0_0_0_1px_rgba(124,92,255,0.12)]"
          : "border-line hover:border-ink-faint hover:bg-panel/40"
      }`}
    >
      <input
        type="radio"
        name="analysis-depth"
        value={props.value}
        checked={props.checked}
        disabled={props.disabled}
        onChange={() => props.onChange(props.value)}
        className="mt-0.5 h-4 w-4 shrink-0 accent-[#6d5efc]"
      />
      <span className="min-w-0">
        <span className="flex items-center gap-2 flex-wrap">
          <span className="text-[13px] font-semibold">{props.title}</span>
          <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded-full ${badgeClass}`}>
            {props.badge}
          </span>
        </span>
        <span className="block mt-1 text-[12px] leading-5 text-ink-soft">{props.description}</span>
      </span>
    </label>
  );
}

const DISPLAY_NUMBER = new Intl.NumberFormat("en-CA");

function formatNumber(value: number | undefined): string {
  return value === undefined ? "—" : DISPLAY_NUMBER.format(value);
}

function Editor(props: {
  label: string;
  tone: string;
  name: string;
  setName: (v: string) => void;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <div className="rounded-2xl border border-line bg-surface overflow-hidden shadow-[0_1px_2px_rgba(0,0,0,0.04)] focus-within:border-ink-faint transition">
      <div className="flex items-center gap-2.5 px-3.5 h-12 border-b border-line bg-panel/60">
        <span
          className="w-5 h-5 rounded-md text-white text-[11px] font-bold flex items-center justify-center"
          style={{ background: props.tone }}
        >
          {props.label}
        </span>
        <input
          value={props.name}
          onChange={(e) => props.setName(e.target.value)}
          className="text-sm font-medium bg-transparent outline-none flex-1 min-w-0"
        />
      </div>
      <textarea
        value={props.value}
        onChange={(e) => props.onChange(e.target.value)}
        spellCheck={false}
        className="scroll-thin w-full h-[440px] resize-none font-mono text-[13px] leading-6 p-4 outline-none text-ink"
      />
    </div>
  );
}

function ResultView({ data }: { data: AnalyzeResponse }) {
  const [activeId, setActiveId] = useState<string | null>(data.regions[0]?.id ?? null);
  const active = data.regions.find((r) => r.id === activeId) ?? null;

  const counts = useMemo(() => {
    const c: Record<CloneFamily, number> = { T1: 0, T2: 0, T3: 0, T4: 0 };
    data.regions.forEach((r) => (c[r.family] += 1));
    return c;
  }, [data.regions]);

  return (
    <main className="mx-auto w-full max-w-[1600px] px-6 py-6 flex-1">
      <div className="flex items-center justify-between flex-wrap gap-3 mb-5">
        <div className="flex items-center gap-2 text-sm">
          <FilePill tone="#0969da" name={data.left.name} />
          <span className="text-ink-faint text-xs font-semibold">→</span>
          <FilePill tone="#1a7f37" name={data.right.name} />
        </div>
        <div className="flex items-center gap-1.5">
          {(["T1", "T2", "T3"] as CloneFamily[])
            .filter((f) => counts[f] > 0)
            .map((f) => (
              <span
                key={f}
                className="inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full"
                style={{ color: FAMILY[f].tone, background: FAMILY[f].soft }}
                title={FAMILY[f].name}
              >
                <span className="w-1.5 h-1.5 rounded-full" style={{ background: FAMILY[f].tone }} />
                {f} · {counts[f]}
              </span>
            ))}
          {data.requestedT4Mode === "OFF" ? (
            <span
              className="inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full text-ink-faint bg-panel border border-line"
              title="Behavioural similarity was not checked in this run"
            >
              T4 · not checked
            </span>
          ) : (
            <span
              className="inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full"
              style={{ color: FAMILY.T4.tone, background: FAMILY.T4.soft }}
              title={data.effectiveT4Mode === "OFF" ? "T4 verification was unavailable" : FAMILY.T4.name}
            >
              <span className="w-1.5 h-1.5 rounded-full" style={{ background: FAMILY.T4.tone }} />
              {data.effectiveT4Mode === "OFF" ? "T4 · unavailable" : `T4 · ${counts.T4}`}
            </span>
          )}
          {data.regions.length === 0 && <span className="text-xs text-ink-soft">no clone regions</span>}
        </div>
      </div>

      <AnalysisReceipt data={data} />

      <div className="grid grid-cols-1 xl:grid-cols-[1fr_360px] gap-5 mt-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 min-w-0">
          <CodePane side="left" file={data.left} regions={data.regions} active={active} onPick={setActiveId} />
          <CodePane side="right" file={data.right} regions={data.regions} active={active} onPick={setActiveId} />
        </div>
        <Sidebar regions={data.regions} activeId={activeId} onPick={setActiveId} active={active} />
      </div>
    </main>
  );
}

function AnalysisReceipt({ data }: { data: AnalyzeResponse }) {
  const quick = data.analysisMode === "SOURCE_AST";
  const fallback = data.analysisMode === "SOURCE_ONLY_FALLBACK";
  const t4Label = data.requestedT4Mode === "OFF"
    ? "T4 not checked"
    : data.effectiveT4Mode === "SMT_DYNAMIC"
      ? "T4 proof + runtime evidence"
      : data.effectiveT4Mode === "SMT_ONLY"
        ? "T4 formal proof"
        : "T4 unavailable";

  return (
    <div
      className={`rounded-xl border px-4 py-3 flex items-start justify-between gap-4 flex-wrap ${
        fallback || data.degraded ? "border-[#f2cc60] bg-t3-soft/70" : "border-line bg-panel/50"
      }`}
    >
      <div>
        <div className="text-[12px] font-semibold">
          {fallback
            ? "Deep analysis fell back to source and AST evidence"
            : quick
              ? "Quick scan result"
              : "Deep structural analysis result"}
        </div>
        <div className="mt-0.5 text-[11px] leading-4 text-ink-soft">
          {fallback
            ? `The requested WALA path could not complete${data.fallbackReason ? ` (${data.fallbackReason})` : ""}.`
            : quick
              ? "Source and AST evidence only; no compilation or control-flow analysis was run."
              : "Compiled WALA control/data-flow and boundary-free region analysis completed."}
        </div>
      </div>
      <span className="shrink-0 rounded-full border border-line bg-surface px-2.5 py-1 text-[10px] font-semibold text-ink-soft">
        {t4Label}
      </span>
    </div>
  );
}

function FilePill({ tone, name }: { tone: string; name: string }) {
  return (
    <span className="inline-flex items-center gap-2 max-w-[240px] px-3 py-1.5 rounded-full bg-surface border border-line text-[13px] font-medium truncate">
      <span className="w-2 h-2 rounded-full shrink-0" style={{ background: tone }} />
      <span className="truncate">{name}</span>
    </span>
  );
}

function CodePane(props: {
  side: "left" | "right";
  file: { name: string; source: string };
  regions: RegionVerdict[];
  active: RegionVerdict | null;
  onPick: (id: string) => void;
}) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const lines = props.file.source.replace(/\n$/, "").split("\n");
  const span = (r: RegionVerdict) => (props.side === "left" ? r.left : r.right);

  useEffect(() => {
    if (!props.active) return;
    const begin = span(props.active).begin;
    const el = scrollRef.current?.querySelector<HTMLElement>(`[data-line="${begin}"]`);
    el?.scrollIntoView({ block: "center", behavior: "smooth" });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [props.active?.id]);

  return (
    <div className="rounded-2xl border border-line bg-surface overflow-hidden shadow-[0_1px_2px_rgba(0,0,0,0.04)] flex flex-col">
      <div className="flex items-center gap-2 px-3.5 h-10 border-b border-line text-[13px] font-medium bg-panel/60">
        <span className="text-ink-faint uppercase text-[10px] font-bold tracking-wide">{props.side}</span>
        <span className="truncate">{props.file.name}</span>
      </div>
      <div ref={scrollRef} className="scroll-thin overflow-auto max-h-[70vh] font-mono text-[12.5px] leading-[22px] py-1">
        {lines.map((text, i) => {
          const n = i + 1;
          const owning = props.regions.filter((r) => within(span(r), n));
          const isActive = props.active ? within(span(props.active), n) : false;
          // The region family stays authoritative and owns the left stripe. A statement-level
          // sub-region may use a more specific background colour, but its type is always labelled
          // in the dedicated gutter so a T1 run inside a T2 region cannot look like an unexplained
          // second colour for T2.
          const regionFam = isActive && props.active ? FAMILY[props.active.family] : null;
          const activeSub = isActive && props.active ? subRegionAt(props.active, props.side, n) : null;
          const subFamily = activeSub ? familyOf(activeSub.type) : null;
          const lineFam = subFamily ? FAMILY[subFamily] : regionFam;
          const startsActive = props.active ? span(props.active).begin === n : false;
          const startsSub = activeSub
            ? (props.side === "left" ? activeSub.left : activeSub.right).some((run) => run.begin === n)
            : false;
          const inlineFamily = startsSub
            ? subFamily
            : startsActive && !props.active?.subRegions?.length
              ? props.active?.family ?? null
              : null;
          const marker = owning[0];
          return (
            <div
              key={n}
              data-line={n}
              onClick={() => marker && props.onPick(marker.id)}
              className={`group flex ${marker ? "cursor-pointer" : ""}`}
              style={lineFam && regionFam
                ? { background: lineFam.soft, boxShadow: `inset 3px 0 0 ${regionFam.tone}` }
                : undefined}
            >
              <span className="select-none w-11 shrink-0 text-right pr-3 text-ink-faint/70 bg-gutter/70 border-r border-line/60">{n}</span>
              <span className="select-none w-9 shrink-0 flex items-center justify-center relative">
                {inlineFamily && (
                  <span
                    className="text-[9px] leading-4 font-bold uppercase px-1 rounded-sm text-white tracking-wide"
                    style={{ background: FAMILY[inlineFamily].tone }}
                  >
                    {inlineFamily}
                  </span>
                )}
                {!isActive && marker && (
                  <span
                    className="w-1.5 h-1.5 rounded-full"
                    style={{ background: FAMILY[marker.family].tone, opacity: 0.45 }}
                  />
                )}
              </span>
              <span className="pl-1 pr-4 whitespace-pre flex-1 relative">
                <span dangerouslySetInnerHTML={{ __html: highlight(text) }} />
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function Sidebar(props: {
  regions: RegionVerdict[];
  activeId: string | null;
  onPick: (id: string) => void;
  active: RegionVerdict | null;
}) {
  return (
    <div className="flex flex-col gap-4 min-w-0">
      <div className="rounded-2xl border border-line bg-surface shadow-[0_1px_2px_rgba(0,0,0,0.04)] overflow-hidden">
        <div className="px-4 h-11 flex items-center border-b border-line text-[13px] font-semibold text-ink-soft bg-panel/60">
          {props.regions.length} region{props.regions.length === 1 ? "" : "s"}
        </div>
        <div className="max-h-[40vh] overflow-auto scroll-thin">
          {props.regions.map((r) => {
            const fam = FAMILY[r.family];
            const on = r.id === props.activeId;
            return (
              <button
                key={r.id}
                onClick={() => props.onPick(r.id)}
                className={`w-full text-left px-4 py-3 border-b border-line/70 last:border-0 transition ${
                  on ? "bg-canvas" : "hover:bg-canvas/60"
                }`}
                style={{ boxShadow: `inset 3px 0 0 ${on ? fam.tone : fam.tone + "40"}` }}
              >
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-[11px] font-bold px-1.5 py-0.5 rounded" style={{ color: fam.tone, background: fam.soft }}>
                    {r.family}
                  </span>
                  <span className="text-[13px] font-medium">{TYPE_LABEL[r.type] ?? r.type}</span>
                  {r.crossMethod && (
                    <span className="text-[10px] font-semibold text-ink-faint px-1.5 py-0.5 rounded bg-canvas border border-line">
                      cross-method
                    </span>
                  )}
                </div>
                <div className="mt-1 text-[12px] text-ink-soft font-mono">
                  L{r.left.begin}–{r.left.end} · R{r.right.begin}–{r.right.end}
                  {coverageNote(r.left)}
                  {r.similarity != null && ` · sim ${r.similarity.toFixed(2)}`}
                </div>
              </button>
            );
          })}
          {props.regions.length === 0 && (
            <div className="px-4 py-8 text-sm text-ink-soft text-center">
              No clone regions found between these files.
            </div>
          )}
        </div>
      </div>

      {props.active && (
        <div className="rounded-2xl border border-line bg-surface shadow-[0_1px_2px_rgba(0,0,0,0.04)] overflow-hidden">
          <div className="px-4 h-11 flex items-center border-b border-line text-[13px] font-semibold text-ink-soft bg-panel/60">
            Why this verdict
          </div>
          <div className="p-4 space-y-2.5">
            {props.active.path.map((step, i) => (
              <div key={i} className="flex gap-2.5 text-[12.5px] leading-5">
                <span className="text-ink-faint select-none tabular-nums">{i + 1}</span>
                <span className="text-ink-soft">{step}</span>
              </div>
            ))}
            {props.active.tags.length > 0 && (
              <div className="pt-1 flex flex-wrap gap-1.5">
                {props.active.tags.map((t) => (
                  <span key={t} className="text-[10px] font-semibold text-ink-faint px-1.5 py-0.5 rounded bg-canvas border border-line">
                    {t}
                  </span>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {props.active?.subRegions && props.active.subRegions.length > 0 && (
        <div className="rounded-2xl border border-line bg-surface shadow-[0_1px_2px_rgba(0,0,0,0.04)] overflow-hidden">
          <div className="px-4 h-11 flex items-center border-b border-line text-[13px] font-semibold text-ink-soft bg-panel/60">
            Sub-region breakdown
          </div>
          <div className="divide-y divide-line/70">
            {props.active.subRegions.map((sub, i) => {
              const family = familyOf(sub.type);
              const fam = FAMILY[family];
              return (
                <div key={`${sub.type}-${i}`} className="px-4 py-3">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span
                      className="text-[11px] font-bold px-1.5 py-0.5 rounded"
                      style={{ color: fam.tone, background: fam.soft }}
                    >
                      {family}
                    </span>
                    <span className="text-[12px] font-mono text-ink-soft">
                      L{segmentLabel(sub.left)} · R{segmentLabel(sub.right)}
                    </span>
                  </div>
                  <p className="mt-1.5 text-[12px] leading-5 text-ink-soft">{sub.reason}</p>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

function LoadingView({
  leftName,
  rightName,
  stage,
  analysisDepth,
  t4Mode,
}: {
  leftName: string;
  rightName: string;
  stage: string | null;
  analysisDepth: AnalysisDepth;
  t4Mode: T4Mode;
}) {
  const [elapsed, setElapsed] = useState(0);
  useEffect(() => {
    const start = Date.now();
    const id = window.setInterval(() => setElapsed((Date.now() - start) / 1000), 100);
    return () => window.clearInterval(id);
  }, []);

  const fallback = stage === "fallback";
  const stages = stagesFor(analysisDepth, t4Mode);
  const current = stages.findIndex((s) => s.key === stage);
  const hint = fallback
    ? "Deep analysis could not continue — producing a source and AST result"
    : current >= 0
      ? stages[current].hint
      : "starting…";

  return (
    <main className="mx-auto w-full max-w-[720px] px-6 py-24 flex-1 flex flex-col items-center text-center">
      <span
        className="inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full mb-5"
        style={{ color: "#4b3fd6", background: "#eeecff" }}
      >
        <span className="w-1.5 h-1.5 rounded-full animate-pulse" style={{ background: "linear-gradient(135deg,#7c5cff,#06b6d4)" }} />
        {analysisDepth === "SOURCE_AST" ? "Quick scan" : "Deep analysis"}
      </span>
      <h1 className="text-2xl font-semibold tracking-tight">
        {leftName} <span className="text-ink-faint mx-1">↔</span> {rightName}
      </h1>
      <p className="text-ink-soft mt-2 text-sm">{hint}</p>

      <div className="w-full mt-12">
        <StageTracker stages={stages} current={current} fallback={fallback} hint={hint} />
      </div>

      <p className="mt-7 text-[13px] text-ink-faint font-mono tabular-nums">{elapsed.toFixed(1)}s elapsed</p>
    </main>
  );
}

function StageTracker({
  stages,
  current,
  fallback,
  hint,
}: {
  stages: StageDefinition[];
  current: number;
  fallback: boolean;
  hint: string;
}) {
  return (
    <div role="progressbar" aria-label="Analysis progress" aria-valuetext={hint}>
      <div className="flex flex-wrap justify-center gap-2">
        {stages.map((stage, index) => {
          const done = !fallback && current >= 0 && index < current;
          const active = !fallback && index === current;
          return (
            <div
              key={stage.key}
              className={`inline-flex items-center gap-2 rounded-full border px-3 py-2 text-[11px] font-semibold transition ${
                done
                  ? "border-[#b7ebc6] bg-t1-soft text-t1"
                  : active
                    ? "border-[#c9c2ff] bg-accent-soft text-accent-ink shadow-[0_0_0_3px_rgba(124,92,255,0.10)]"
                    : "border-line bg-surface text-ink-faint"
              }`}
            >
              <span
                className={`h-2 w-2 rounded-full ${active ? "animate-pulse" : ""}`}
                style={{ background: done ? "#1a7f37" : active ? "#7c5cff" : "#d1d9e0" }}
              />
              {stage.label}
            </div>
          );
        })}
        {fallback && (
          <div className="inline-flex items-center gap-2 rounded-full border border-[#f2cc60] bg-t3-soft px-3 py-2 text-[11px] font-semibold text-t3">
            <span className="h-2 w-2 rounded-full animate-pulse bg-[#bf8700]" />
            Source fallback
          </div>
        )}
      </div>
    </div>
  );
}

function stagesFor(analysisDepth: AnalysisDepth, t4Mode: T4Mode): StageDefinition[] {
  if (analysisDepth === "SOURCE_AST") {
    return [{ key: "source", label: "Source & AST", hint: "matching source and AST evidence" }];
  }
  return ADVANCED_STAGES.filter((stage) => {
    if (stage.key === "smt") return t4Mode !== "OFF";
    if (stage.key === "dynamic") return t4Mode === "SMT_DYNAMIC";
    return true;
  });
}

function selectedT4Mode(
  analysisDepth: AnalysisDepth,
  checkT4: boolean,
  runtimeSampling: boolean,
): T4Mode {
  if (analysisDepth === "SOURCE_AST" || !checkT4) return "OFF";
  return runtimeSampling ? "SMT_DYNAMIC" : "SMT_ONLY";
}

/**
 * How much of the bounding box the region really covers, shown only when the two differ.
 * "L12-100" on its own reads as 89 contiguous lines; when the region is three runs totalling 13
 * lines, the label has to say so or it contradicts the highlighting right next to it.
 */
function coverageNote(span: LineSpan): string {
  const segments = span.segments;
  if (!segments || segments.length === 0) return "";
  const covered = segments.reduce((sum, s) => sum + (s.end - s.begin + 1), 0);
  const boxed = span.begin > 0 ? span.end - span.begin + 1 : 0;
  if (segments.length <= 1 || covered >= boxed) return "";
  return ` · ${covered} lines in ${segments.length} runs`;
}

function segmentLabel(segments: { begin: number; end: number }[]): string {
  if (segments.length === 0) return "—";
  return segments.map((segment) => (
    segment.begin === segment.end ? String(segment.begin) : `${segment.begin}–${segment.end}`
  )).join(", ");
}

function subRegionAt(region: RegionVerdict, side: "left" | "right", line: number) {
  for (const sub of region.subRegions ?? []) {
    const runs = side === "left" ? sub.left : sub.right;
    if (runs.some((run) => line >= run.begin && line <= run.end)) return sub;
  }
  return null;
}

function familyOf(type: string): CloneFamily {
  if (type.startsWith("T4") || type === "POSSIBLE_T4_CANDIDATE") return "T4";
  if (type === "T1" || type === "T2" || type === "T3") return type;
  return "T3";
}

function within(span: LineSpan, line: number): boolean {
  // Prefer the real runs. Falling back to the bounding box would highlight code between two runs
  // of a cross-method region -- code the verdict never compared, so it must not be coloured as
  // part of the match. The fallback exists only for a backend that predates `segments`.
  if (span.segments && span.segments.length > 0) {
    return span.segments.some((s) => line >= s.begin && line <= s.end);
  }
  return span.begin > 0 && line >= span.begin && line <= span.end;
}
