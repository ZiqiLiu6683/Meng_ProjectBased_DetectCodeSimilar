import type { AnalyzeResponse } from "./types";

// Sample response used before the backend is wired, and as the default demo. Mirrors the
// helper-extraction case: a region T3 plus a method-level behavioural T4.
export const SAMPLE: AnalyzeResponse = {
  regionBackend: true,
  left: {
    name: "Left.java",
    source: `class Left {
  int score(int x) {
    int t = x + 1;
    return t * t;
  }

  int total(int[] a) {
    int s = 0;
    for (int i = 0; i < a.length; i++) {
      s += a[i];
    }
    return s;
  }
}`,
  },
  right: {
    name: "Right.java",
    source: `class Right {
  int score(int x) {
    int t = x + 1;
    return sq(t);
  }

  int sq(int y) {
    return y * y;
  }

  int total(int[] a) {
    return sum(a, 0);
  }

  int sum(int[] a, int i) {
    if (i >= a.length) return 0;
    return a[i] + sum(a, i + 1);
  }
}`,
  },
  regions: [
    {
      id: "R1",
      family: "T3",
      type: "T3",
      scope: "region",
      crossMethod: true,
      left: { begin: 3, end: 4 },
      right: { begin: 3, end: 8 },
      similarity: 0.71,
      tags: ["POSSIBLE_SEMANTIC_RELATION"],
      path: [
        "T1 failed: token sequences are not 100% identical.",
        "T2 failed: normalized tokens differ or statement edits exist.",
        "T3 passed: statement edit script has insert evidence and syntactic similarity 0.71 is in the Type-3 range.",
      ],
    },
    {
      id: "M1",
      family: "T4",
      type: "T4_DYNAMIC_EVIDENCE",
      scope: "method",
      crossMethod: false,
      left: { begin: 7, end: 13 },
      right: { begin: 11, end: 18 },
      similarity: null,
      tags: ["POSSIBLE_SEMANTIC_RELATION"],
      path: [
        "T1/T2/T3 failed: loop vs recursion are structurally different.",
        "T4 evidenced: I/O sampling agreed on every tested input (evidence, not proof; SMT could not prove it).",
      ],
    },
  ],
};

export const DEMO_LEFT = SAMPLE.left.source;
export const DEMO_RIGHT = SAMPLE.right.source;
