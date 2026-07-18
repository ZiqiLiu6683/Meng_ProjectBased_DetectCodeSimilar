#!/usr/bin/env python3
"""
Dual-metric scorer for BatchPairMain outputs.

Metric 1 - detection (BigCloneEval-comparable): a pair is DETECTED when some
reported clone region covers >= 70% of the reference fragment on BOTH sides
(line coverage, the official CoverageMatcher criterion, type-agnostic).

Metric 2 - type accuracy (this project's stricter claim): the pair is detected
AND the covering region's type matches the expected type family.

Negatives (expected NON_CLONE): a false positive is a covering clone region;
sub-coverage regions are reported separately as a diagnostic, not as FPs.
INCONCLUSIVE labels are listed, excluded from both metrics.

Usage:
    py scripts/experiments/score_results.py --results run.jsonl \
        --labels labels.csv --manifest manifest.csv --out scored/
Labels may carry an optional `band` column (VST3/ST3/MT3): rows are then also
summarised per band.
"""

import argparse
import csv
import json
import statistics
from collections import defaultdict
from pathlib import Path

CLONE_PREFIXES = ("T1", "T2", "T3", "T4", "POSSIBLE_T4")
COVERAGE = 0.70


def stream_rows(path: Path):
    dec = json.JSONDecoder()
    s = path.read_text(encoding="utf-8")
    i = 0
    while i < len(s):
        while i < len(s) and s[i] in " \n\r\t":
            i += 1
        if i >= len(s):
            break
        obj, i = dec.raw_decode(s, i)
        yield obj


def fragment_range(java_file: Path) -> tuple[int, int] | None:
    """Line range (1-based, inclusive) of the wrapped fragment inside a pair file."""
    try:
        lines = java_file.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return None
    start = next((i + 2 for i, l in enumerate(lines)
                  if l.startswith("public class ")), None)
    end = next((i for i in range(len(lines), 0, -1) if lines[i - 1].strip() == "}"), None)
    if start is None or end is None or end - 1 < start:
        return None
    return start, end - 1  # exclude the closing brace line


def is_clone_type(t: str) -> bool:
    return any(t.startswith(p) for p in CLONE_PREFIXES)


def type_family(t: str) -> str:
    if t.startswith(("T4", "POSSIBLE_T4")):
        return "T4"
    return t.split("_")[0] if "_" in t else t


def coverage(region_side: dict, frag: tuple[int, int]) -> float:
    b, e = region_side.get("beginLine"), region_side.get("endLine")
    if b is None or e is None:
        return 0.0
    lo, hi = frag
    inter = max(0, min(e, hi) - max(b, lo) + 1)
    return inter / max(1, hi - lo + 1)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--results", required=True, type=Path)
    ap.add_argument("--labels", required=True, type=Path)
    ap.add_argument("--manifest", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    args = ap.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    labels = {r["pair_id"]: r for r in csv.DictReader(open(args.labels, encoding="utf-8"))}
    paths = {r["pair_id"]: (Path(r["left_path"]), Path(r["right_path"]))
             for r in csv.DictReader(open(args.manifest, encoding="utf-8"))}
    rows = {r["pairId"]: r for r in stream_rows(args.results)}

    groups = defaultdict(lambda: {"n": 0, "det": 0, "typ": 0, "err": 0,
                                  "sub": 0, "misses": [], "wall": []})
    inconclusive, missing = [], []

    with open(args.out / "scored_pairs.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["pair_id", "expected", "band", "status", "detected",
                    "type_hit", "found_types", "best_coverage", "wall_ms"])
        for pair_id, lab in labels.items():
            expected, band = lab["expected_type"], lab.get("band", "")
            row = rows.get(pair_id)
            if row is None:
                missing.append(pair_id)
                continue
            key = band if band else expected
            g = groups[key]
            if expected == "INCONCLUSIVE":
                inconclusive.append(pair_id)
                continue
            g["n"] += 1
            g["wall"].append(row.get("wallMs", 0))
            if row["status"] != "ok":
                g["err"] += 1
                w.writerow([pair_id, expected, band, row["status"], "", "", "", "", row.get("wallMs", 0)])
                continue
            frags = [fragment_range(p) for p in paths.get(pair_id, (None, None)) if p]
            regions = (row.get("report") or {}).get("regions", [])
            clones = [r for r in regions if is_clone_type(r.get("type", ""))]
            best_cov, det, typ_hit, found = 0.0, False, False, set()
            for r in clones:
                found.add(r["type"])
                if len(frags) == 2 and all(frags):
                    cov = min(coverage(r.get("left", {}), frags[0]),
                              coverage(r.get("right", {}), frags[1]))
                else:
                    cov = 1.0  # no fragment info: fall back to presence
                best_cov = max(best_cov, cov)
                if cov >= COVERAGE:
                    det = True
                    if type_family(r["type"]) == type_family(expected):
                        typ_hit = True
            if expected == "NON_CLONE":
                fp = det
                g["det"] += 0 if fp else 1     # for negatives, "det" counts correct rejections
                g["typ"] += 0 if fp else 1
                g["sub"] += 1 if (clones and not det) else 0
                if fp:
                    g["misses"].append((pair_id, sorted(found)))
            else:
                g["det"] += det
                g["typ"] += typ_hit
                g["sub"] += 1 if (clones and not det) else 0
                if not det:
                    g["misses"].append((pair_id, sorted(found)))
            w.writerow([pair_id, expected, band, "ok", det, typ_hit,
                        ";".join(sorted(found)), f"{best_cov:.2f}", row.get("wallMs", 0)])

    lines = ["# Scoring summary (dual metric, coverage >= 70%)", "",
             f"results: `{args.results}`", "",
             "| Group | Pairs | Detected/Correct | Detection | Type-match | Errors | Sub-coverage regions | Avg ms | Median ms |",
             "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for key in sorted(groups):
        g = groups[key]
        if not g["n"]:
            continue
        lines.append(
            f"| {key} | {g['n']} | {g['det']} | {g['det']/g['n']:.2%} | "
            f"{g['typ']/g['n']:.2%} | {g['err']} | {g['sub']} | "
            f"{int(statistics.mean(g['wall']))} | {int(statistics.median(g['wall']))} |")
    lines += ["", "Group = similarity band where available (VST3/ST3/MT3), else expected type.",
              "For NON_CLONE the Detection column is the correct-rejection rate.", ""]
    for key in sorted(groups):
        ms = groups[key]["misses"]
        if ms:
            lines.append(f"## Misses / false positives: {key}")
            lines += [f"- {p}: found {';'.join(t) or '(none)'}" for p, t in ms]
            lines.append("")
    if inconclusive:
        lines.append(f"INCONCLUSIVE (excluded): {len(inconclusive)}")
    if missing:
        lines.append(f"Pairs in labels but missing from results: {len(missing)}")
    (args.out / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
