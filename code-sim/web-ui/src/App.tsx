import { useEffect, useMemo, useRef, useState } from "react";
import type { AnalyzeResponse, CloneFamily, RegionVerdict } from "./types";
import { SAMPLE, DEMO_LEFT, DEMO_RIGHT } from "./sample";

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
  const [view, setView] = useState<"input" | "result">("input");
  const [leftName, setLeftName] = useState("Left.java");
  const [rightName, setRightName] = useState("Right.java");
  const [leftSource, setLeftSource] = useState(DEMO_LEFT);
  const [rightSource, setRightSource] = useState(DEMO_RIGHT);
  const [data, setData] = useState<AnalyzeResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function analyze() {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch("/api/analyze", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ leftName, rightName, leftSource, rightSource }).toString(),
      });
      if (!res.ok) throw new Error(`backend returned ${res.status}`);
      const json = (await res.json()) as AnalyzeResponse;
      setData(json);
      setView("result");
    } catch (e) {
      setError(
        `Couldn't reach the region backend (${(e as Error).message}). Start it with the semantic ` +
          "profile, or view the built-in demo.",
      );
    } finally {
      setLoading(false);
    }
  }

  function showDemo() {
    setData(SAMPLE);
    setLeftName(SAMPLE.left.name);
    setRightName(SAMPLE.right.name);
    setView("result");
  }

  return (
    <div className="min-h-full flex flex-col">
      <Header
        onHome={() => setView("input")}
        showNew={view === "result"}
        backend={view === "result" ? data?.regionBackend : undefined}
      />
      {view === "input" ? (
        <InputView
          leftName={leftName}
          rightName={rightName}
          leftSource={leftSource}
          rightSource={rightSource}
          setLeftName={setLeftName}
          setRightName={setRightName}
          setLeftSource={setLeftSource}
          setRightSource={setRightSource}
          onAnalyze={analyze}
          onDemo={showDemo}
          loading={loading}
          error={error}
        />
      ) : (
        data && <ResultView data={data} />
      )}
    </div>
  );
}

function Header(props: { onHome: () => void; showNew?: boolean; backend?: boolean }) {
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
                props.backend ? "text-t1 bg-t1-soft" : "text-t3 bg-t3-soft"
              }`}
            >
              {props.backend ? "WALA region backend" : "source-only fallback"}
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
  onAnalyze: () => void;
  onDemo: () => void;
  loading: boolean;
  error: string | null;
}) {
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

      {props.error && (
        <div className="mt-5 text-sm text-t3 bg-t3-soft rounded-xl px-4 py-3">{props.error}</div>
      )}

      <div className="mt-6 flex items-center gap-3">
        <button
          onClick={props.onAnalyze}
          disabled={props.loading}
          className="text-sm font-semibold px-5 py-2.5 rounded-lg text-white transition hover:brightness-110 disabled:opacity-50 shadow-[0_4px_14px_rgba(109,94,252,0.35)]"
          style={{ background: "linear-gradient(135deg,#7c5cff,#06b6d4)" }}
        >
          {props.loading ? "Analyzing…" : "Analyze"}
        </button>
        <button
          onClick={props.onDemo}
          className="text-sm font-medium px-4 py-2.5 rounded-lg text-ink-soft hover:text-accent-ink hover:bg-accent-soft transition"
        >
          View demo result
        </button>
      </div>
    </main>
  );
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
          {(["T1", "T2", "T3", "T4"] as CloneFamily[])
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
          {data.regions.length === 0 && <span className="text-xs text-ink-soft">no clone regions</span>}
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-[1fr_360px] gap-5">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 min-w-0">
          <CodePane side="left" file={data.left} regions={data.regions} active={active} onPick={setActiveId} />
          <CodePane side="right" file={data.right} regions={data.regions} active={active} onPick={setActiveId} />
        </div>
        <Sidebar regions={data.regions} activeId={activeId} onPick={setActiveId} active={active} />
      </div>
    </main>
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
          const activeFam = isActive && props.active ? FAMILY[props.active.family] : null;
          const startsActive = props.active ? span(props.active).begin === n : false;
          const marker = owning[0];
          return (
            <div
              key={n}
              data-line={n}
              onClick={() => marker && props.onPick(marker.id)}
              className={`group flex ${marker ? "cursor-pointer" : ""}`}
              style={activeFam ? { background: activeFam.soft, boxShadow: `inset 3px 0 0 ${activeFam.tone}` } : undefined}
            >
              <span className="select-none w-11 shrink-0 text-right pr-3 text-ink-faint/70 bg-gutter/70 border-r border-line/60">{n}</span>
              <span className="pl-3 pr-4 whitespace-pre flex-1 relative">
                {startsActive && props.active && (
                  <span
                    className="absolute -top-[7px] left-3 text-[9px] font-bold uppercase px-1 rounded-sm text-white tracking-wide"
                    style={{ background: FAMILY[props.active.family].tone }}
                  >
                    {props.active.family}
                  </span>
                )}
                {!isActive && marker && (
                  <span
                    className="absolute left-0 top-2 w-1.5 h-1.5 rounded-full"
                    style={{ background: FAMILY[marker.family].tone, opacity: 0.45 }}
                  />
                )}
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
    </div>
  );
}

function within(span: { begin: number; end: number }, line: number): boolean {
  return span.begin > 0 && line >= span.begin && line <= span.end;
}
