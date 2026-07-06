import { useMemo, useState } from "react";
import type { AnalyzeResponse, CloneFamily, RegionVerdict } from "./types";
import { SAMPLE, DEMO_LEFT, DEMO_RIGHT } from "./sample";

const FAMILY: Record<
  CloneFamily,
  { name: string; tone: string; soft: string; text: string; border: string; dot: string }
> = {
  T1: { name: "Identical", tone: "#1a7f37", soft: "#dafbe1", text: "text-t1", border: "border-t1", dot: "bg-t1" },
  T2: { name: "Renamed", tone: "#0969da", soft: "#ddf4ff", text: "text-t2", border: "border-t2", dot: "bg-t2" },
  T3: { name: "Near-miss", tone: "#9a6700", soft: "#fff8c5", text: "text-t3", border: "border-t3", dot: "bg-t3" },
  T4: { name: "Same behavior", tone: "#8250df", soft: "#fbefff", text: "text-t4", border: "border-t4", dot: "bg-t4" },
};

const TYPE_LABEL: Record<string, string> = {
  T1: "T1 · exact",
  T2: "T2 · renamed",
  T3: "T3 · near-miss",
  T4_CONFIRMED: "T4 · proven equivalent",
  T4_DYNAMIC_EVIDENCE: "T4 · evidence (sampled)",
  POSSIBLE_T4_CANDIDATE: "T4 · possible (cross-method)",
};

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
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ leftName, rightName, leftSource, rightSource }),
      });
      if (!res.ok) throw new Error(`Backend returned ${res.status}`);
      const json = (await res.json()) as AnalyzeResponse;
      setData(json);
      setView("result");
    } catch (e) {
      setError(
        `Could not reach the region backend (${(e as Error).message}). ` +
          "Start it with the semantic profile, or view the built-in demo result.",
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
        onNew={() => setView("input")}
        showNew={view === "result"}
        backend={data?.regionBackend}
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

function Header(props: { onNew: () => void; showNew: boolean; backend?: boolean }) {
  return (
    <header className="sticky top-0 z-10 bg-surface/90 backdrop-blur border-b border-line">
      <div className="mx-auto max-w-[1600px] px-6 h-14 flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <div className="w-6 h-6 rounded-md bg-ink flex items-center justify-center">
            <div className="w-2.5 h-2.5 rounded-sm bg-white" />
          </div>
          <div className="font-semibold tracking-tight">CodeSim</div>
          <span className="text-xs text-ink-faint font-medium px-1.5 py-0.5 rounded bg-canvas border border-line">
            region clones
          </span>
        </div>
        <div className="flex items-center gap-3">
          {props.backend !== undefined && (
            <span
              className={`text-xs font-medium px-2 py-1 rounded-full border ${
                props.backend
                  ? "text-t1 border-t1/30 bg-t1-soft"
                  : "text-t3 border-t3/30 bg-t3-soft"
              }`}
            >
              {props.backend ? "WALA region backend" : "source-only fallback"}
            </span>
          )}
          {props.showNew && (
            <button
              onClick={props.onNew}
              className="text-sm font-medium px-3 py-1.5 rounded-md border border-line bg-surface hover:bg-canvas transition"
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
    <main className="mx-auto w-full max-w-[1600px] px-6 py-8 flex-1">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Compare two Java files</h1>
        <p className="text-ink-soft mt-1 text-sm">
          Boundary-free region detection: each matched region is typed T1–T3, plus method-level
          behavioural T4. Paste two files and analyze.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        <Editor label="A" name={props.leftName} setName={props.setLeftName} value={props.leftSource} onChange={props.setLeftSource} />
        <Editor label="B" name={props.rightName} setName={props.setRightName} value={props.rightSource} onChange={props.setRightSource} />
      </div>

      {props.error && (
        <div className="mt-5 text-sm text-t3 bg-t3-soft border border-t3/30 rounded-lg px-4 py-3">
          {props.error}
        </div>
      )}

      <div className="mt-6 flex items-center gap-3">
        <button
          onClick={props.onAnalyze}
          disabled={props.loading}
          className="text-sm font-semibold px-4 py-2 rounded-md bg-ink text-white hover:bg-black transition disabled:opacity-50"
        >
          {props.loading ? "Analyzing…" : "Analyze"}
        </button>
        <button
          onClick={props.onDemo}
          className="text-sm font-medium px-4 py-2 rounded-md border border-line bg-surface hover:bg-canvas transition"
        >
          View demo result
        </button>
      </div>
    </main>
  );
}

function Editor(props: {
  label: string;
  name: string;
  setName: (v: string) => void;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <div className="rounded-xl border border-line bg-surface overflow-hidden shadow-sm">
      <div className="flex items-center gap-2 px-3 h-11 border-b border-line bg-canvas/60">
        <span className="w-5 h-5 rounded bg-ink text-white text-xs font-bold flex items-center justify-center">
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
        className="scroll-thin w-full h-[460px] resize-none font-mono text-[13px] leading-6 p-4 outline-none text-ink"
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
          <span className="text-ink-faint text-xs font-semibold uppercase">vs</span>
          <FilePill tone="#1a7f37" name={data.right.name} />
        </div>
        <div className="flex items-center gap-2">
          {(["T1", "T2", "T3", "T4"] as CloneFamily[]).map((f) => (
            <span
              key={f}
              className="inline-flex items-center gap-1.5 text-xs font-semibold px-2 py-1 rounded-md border"
              style={{ color: FAMILY[f].tone, borderColor: FAMILY[f].tone + "40", background: FAMILY[f].soft }}
              title={FAMILY[f].name}
            >
              <span className="w-1.5 h-1.5 rounded-full" style={{ background: FAMILY[f].tone }} />
              {f} · {counts[f]}
            </span>
          ))}
        </div>
      </div>

      {data.regions.length === 0 && (
        <div className="text-sm text-ink-soft bg-surface border border-line rounded-lg px-4 py-6 text-center">
          No clone regions found between these two files.
        </div>
      )}

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
    <span
      className="inline-flex items-center gap-1.5 max-w-[240px] px-2.5 py-1 rounded-full border bg-surface text-[13px] font-medium truncate"
      style={{ borderColor: tone + "55" }}
    >
      <span className="w-1.5 h-1.5 rounded-full shrink-0" style={{ background: tone }} />
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
  const lines = props.file.source.replace(/\n$/, "").split("\n");
  const span = (r: RegionVerdict) => (props.side === "left" ? r.left : r.right);

  return (
    <div className="rounded-xl border border-line bg-surface overflow-hidden shadow-sm flex flex-col">
      <div className="flex items-center gap-2 px-3 h-10 border-b border-line bg-canvas/60 text-[13px] font-medium">
        <span className="text-ink-faint uppercase text-[11px] font-bold">{props.side}</span>
        <span className="truncate">{props.file.name}</span>
      </div>
      <div className="scroll-thin overflow-auto max-h-[68vh] font-mono text-[12.5px] leading-6">
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
              onClick={() => marker && props.onPick(marker.id)}
              className={`group flex ${marker ? "cursor-pointer" : ""}`}
              style={
                activeFam ? { background: activeFam.soft, boxShadow: `inset 3px 0 0 ${activeFam.tone}` } : undefined
              }
            >
              <span className="select-none w-10 shrink-0 text-right pr-3 text-ink-faint/70 border-r border-line/60">
                {n}
              </span>
              <span className="pl-3 pr-4 whitespace-pre flex-1 relative">
                {startsActive && props.active && (
                  <span
                    className="absolute -top-0.5 left-3 text-[9px] font-bold uppercase px-1 rounded text-white"
                    style={{ background: FAMILY[props.active.family].tone }}
                  >
                    {props.active.family}
                  </span>
                )}
                {!isActive && marker && (
                  <span
                    className="absolute left-0 top-2 w-1.5 h-1.5 rounded-full"
                    style={{ background: FAMILY[marker.family].tone, opacity: 0.5 }}
                  />
                )}
                {text || " "}
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
    <div className="flex flex-col gap-3 min-w-0">
      <div className="rounded-xl border border-line bg-surface shadow-sm overflow-hidden">
        <div className="px-4 h-10 flex items-center border-b border-line text-[13px] font-semibold text-ink-soft">
          Regions ({props.regions.length})
        </div>
        <div className="max-h-[38vh] overflow-auto scroll-thin">
          {props.regions.map((r) => {
            const fam = FAMILY[r.family];
            const on = r.id === props.activeId;
            return (
              <button
                key={r.id}
                onClick={() => props.onPick(r.id)}
                className={`w-full text-left px-4 py-3 border-b border-line/70 transition ${
                  on ? "bg-canvas" : "hover:bg-canvas/60"
                }`}
                style={on ? { boxShadow: `inset 3px 0 0 ${fam.tone}` } : undefined}
              >
                <div className="flex items-center gap-2">
                  <span
                    className="text-[11px] font-bold px-1.5 py-0.5 rounded"
                    style={{ color: fam.tone, background: fam.soft }}
                  >
                    {r.family}
                  </span>
                  <span className="text-[13px] font-medium">{TYPE_LABEL[r.type] ?? r.type}</span>
                  {r.crossMethod && (
                    <span className="text-[10px] font-semibold text-ink-faint px-1 py-0.5 rounded bg-canvas border border-line">
                      cross-method
                    </span>
                  )}
                </div>
                <div className="mt-1 text-[12px] text-ink-soft font-mono">
                  L{r.left.begin}-{r.left.end} · R{r.right.begin}-{r.right.end}
                  {r.similarity != null && ` · sim ${r.similarity.toFixed(2)}`}
                </div>
              </button>
            );
          })}
          {props.regions.length === 0 && (
            <div className="px-4 py-6 text-sm text-ink-soft text-center">No regions.</div>
          )}
        </div>
      </div>

      {props.active && (
        <div className="rounded-xl border border-line bg-surface shadow-sm overflow-hidden">
          <div className="px-4 h-10 flex items-center border-b border-line text-[13px] font-semibold text-ink-soft">
            Why this verdict
          </div>
          <div className="p-4 space-y-2">
            {props.active.path.map((step, i) => (
              <div key={i} className="flex gap-2 text-[12.5px] leading-5">
                <span className="text-ink-faint select-none">{i + 1}.</span>
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
