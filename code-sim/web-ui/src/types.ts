// API contract between the SPA and the region-based backend (WalaNextPipelineRunner).
// There is NO file-level clone type: results are a list of per-REGION verdicts (T1/T2/T3)
// plus method-level behavioural T4. The input is always a pair of files.

export type CloneFamily = "T1" | "T2" | "T3" | "T4";
export type AnalysisDepth = "SOURCE_AST" | "WALA_REGIONS";
export type T4Mode = "OFF" | "SMT_ONLY" | "SMT_DYNAMIC";
export type PreflightStatus = "idle" | "checking" | "ready" | "error";
export type WorkloadBand = "LOW" | "MODERATE" | "HIGH";

export interface PreflightSideMetrics {
  lines: number;
  characters: number;
  methods: number;
  regions: number;
}

export interface PreflightResponse {
  parseable: boolean;
  error?: string;
  left?: PreflightSideMetrics;
  right?: PreflightSideMetrics;
  quickComparisonUpperBound?: number;
  quickComparisonBudget?: number;
  workload?: WorkloadBand;
  recommendedMode?: AnalysisDepth;
  quickAllowed?: boolean;
}

/** Exact backend type, e.g. T4_CONFIRMED vs T4_DYNAMIC_EVIDENCE vs POSSIBLE_T4_CANDIDATE. */
export type CloneType =
  | "T1"
  | "T2"
  | "T3"
  | "T4_CONFIRMED"
  | "T4_DYNAMIC_EVIDENCE"
  | "POSSIBLE_T4_CANDIDATE";

export interface LineSegment {
  /** 1-based inclusive run of lines that is genuinely part of the region. */
  begin: number;
  end: number;
}

export interface LineSpan {
  /**
   * Bounding box: the minimum and maximum line of the region, 0/absent when not applicable.
   * NOT the region's content -- a region grown across two methods encloses code belonging to
   * neither, so highlighting begin..end colours lines the verdict never examined. Use `segments`.
   */
  begin: number;
  end: number;
  /**
   * The contiguous runs the region is actually made of, ascending and non-overlapping. Always
   * within begin..end, and often far smaller: one measured T1 region reported as lines 12-100
   * (89 lines) was really 12-17, 22-23 and 96-100 -- 13 lines.
   * Absent only in responses from a backend older than this field.
   */
  segments?: LineSegment[];
}

export interface SubRegionVerdict {
  /**
   * The same T1->T2->T3 cascade applied to one aligned statement pair instead of the whole region.
   * A region typed T2 means the WHOLE region differs only by identifiers; a sub-region typed T2
   * means that run does, while the region around it may differ in other ways. `left` is empty for
   * a statement that exists only on the right (inserted), and vice versa.
   */
  type: CloneType;
  left: LineSegment[];
  right: LineSegment[];
  reason: string;
}

export interface RegionVerdict {
  id: string;
  family: CloneFamily;
  type: CloneType;
  /** "region" = boundary-free Phase A region (T1/T2/T3); "method" = whole-method behavioural T4. */
  scope: "region" | "method";
  crossMethod: boolean;
  left: LineSpan;
  right: LineSpan;
  /** Syntactic similarity for T3 (null when not applicable, e.g. exact T1/T2 or behavioural T4). */
  similarity: number | null;
  tags: string[];
  /** The recognizer's decision trail — why this verdict. */
  path: string[];
  /**
   * Uniformly-typed runs inside this region. The region's own `type` stays authoritative; this is
   * a finer view of the same evidence, so a count of clones must use one scale or the other, never
   * both. Absent from a backend older than this field.
   */
  subRegions?: SubRegionVerdict[];
}

export interface FileSide {
  name: string;
  source: string;
}

export interface AnalyzeResponse {
  left: FileSide;
  right: FileSide;
  regions: RegionVerdict[];
  /** Whether the WALA region backend ran (false => source-only fallback, weaker). */
  regionBackend: boolean;
  analysisMode:
    | "SOURCE_AST"
    | "SOURCE_PLUS_WALA"
    | "SOURCE_PLUS_WALA_SMT"
    | "SOURCE_PLUS_WALA_SMT_DYNAMIC"
    | "SOURCE_PLUS_PROJECT_CONTEXT_WALA"
    | "SOURCE_PLUS_PROJECT_CONTEXT_WALA_SMT"
    | "SOURCE_PLUS_PROJECT_CONTEXT_WALA_SMT_DYNAMIC"
    | "SOURCE_PLUS_STUBBED_WALA"
    | "SOURCE_PLUS_STUBBED_WALA_SMT"
    | "SOURCE_PLUS_STUBBED_WALA_SMT_DYNAMIC"
    | "SOURCE_ONLY_FALLBACK";
  requestedAnalysisDepth: AnalysisDepth;
  requestedT4Mode: T4Mode;
  effectiveT4Mode: T4Mode;
  degraded: boolean;
  fallbackStage: string;
  fallbackReason: string;
  stages: Record<string, {
    status: "SUCCESS" | "FAILED" | "SKIPPED_CONFIG" | "NOT_REACHED";
    durationMs: number;
    detail: string;
  }>;
  compilations: Partial<Record<
    "left" | "right",
    {
      mode: "STANDALONE" | "PROJECT_CONTEXT" | "STUBBED";
      cacheHit: boolean;
      generatedStubCount: number;
      javaRelease: number;
      diagnosticSummary: string;
    }
  >>;
  note?: string;
}

export interface AnalyzeRequest {
  leftName: string;
  rightName: string;
  leftSource: string;
  rightSource: string;
  analysisDepth: AnalysisDepth;
  t4Mode: T4Mode;
}
