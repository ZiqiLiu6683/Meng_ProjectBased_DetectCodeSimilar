// API contract between the SPA and the region-based backend (WalaNextPipelineRunner).
// There is NO file-level clone type: results are a list of per-REGION verdicts (T1/T2/T3)
// plus method-level behavioural T4. The input is always a pair of files.

export type CloneFamily = "T1" | "T2" | "T3" | "T4";

/** Exact backend type, e.g. T4_CONFIRMED vs T4_DYNAMIC_EVIDENCE vs POSSIBLE_T4_CANDIDATE. */
export type CloneType =
  | "T1"
  | "T2"
  | "T3"
  | "T4_CONFIRMED"
  | "T4_DYNAMIC_EVIDENCE"
  | "POSSIBLE_T4_CANDIDATE";

export interface LineSpan {
  /** 1-based inclusive line range on that side; 0/absent when not applicable. */
  begin: number;
  end: number;
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
  note?: string;
}

export interface AnalyzeRequest {
  leftName: string;
  rightName: string;
  leftSource: string;
  rightSource: string;
}
