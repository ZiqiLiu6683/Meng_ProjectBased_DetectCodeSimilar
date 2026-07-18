#!/usr/bin/env python3
"""
Region-level scorer for BatchPairMain outputs.

Scoring rule (the region-level rule of PipelineClassificationHarness):
  - expected T1 / T2 / T3      hit if any reported region carries that type
  - expected T4 (any variant)  hit if any reported region carries a T4-family type
                               (T4_CONFIRMED / T4_DYNAMIC_EVIDENCE / POSSIBLE_T4)
  - expected NON_CLONE         hit if no reported region carries a clone type
  - expected INCONCLUSIVE      reported separately, excluded from accuracy

Inputs:  --results  merged .jsonl from BatchPairMain (handles pretty-printed rows)
         --labels   labels.csv (pair_id,expected_type,...)
Outputs: <out>/scored_pairs.csv  one row per pair with expected/found/hit
         <out>/summary.md        per-type table + misses + runtime stats
"""

import argparse
import csv
import json
import statistics
from collections import defaultdict
from pathlib import Path

CLONE_PREFIXES = ("T1", "T2", "T3", "T4", "POSSIBLE_T4")


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


def region_types(row) -> list[str]:
    report = row.get("report") or {}
    return [r.get("type", "") for r in report.get("regions", [])]


def is_clone_type(t: str) -> bool:
    return any(t.startswith(p) for p in CLONE_PREFIXES)


def hit(expected: str, types: list[str]) -> bool:
    clones = [t for t in types if is_clone_type(t)]
    if expected == "NON_CLONE":
        return not clones
    if expected.startswith("T4"):
        return any(t.startswith(("T4", "POSSIBLE_T4")) for t in clones)
    return any(t == expected or t.startswith(expected + "_") for t in clones)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--results", required=True, type=Path)
    ap.add_argument("--labels", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    args = ap.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    labels = {r["pair_id"]: r for r in csv.DictReader(open(args.labels, encoding="utf-8"))}
    rows = {r["pairId"]: r for r in stream_rows(args.results)}

    per_type = defaultdict(lambda: {"n": 0, "hits": 0, "errors": 0, "misses": []})
    walls = defaultdict(list)
    inconclusive, missing = [], []

    with open(args.out / "scored_pairs.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["pair_id", "expected", "status", "found_types", "hit", "wall_ms"])
        for pair_id, lab in labels.items():
            expected = lab["expected_type"]
            row = rows.get(pair_id)
            if row is None:
                missing.append(pair_id)
                continue
            types = sorted(set(region_types(row)))
            wall = row.get("wallMs", 0)
            if expected == "INCONCLUSIVE":
                inconclusive.append((pair_id, types))
                w.writerow([pair_id, expected, row["status"], ";".join(types), "", wall])
                continue
            b = per_type[expected]
            b["n"] += 1
            walls[expected].append(wall)
            if row["status"] != "ok":
                b["errors"] += 1
                w.writerow([pair_id, expected, row["status"], "", False, wall])
                continue
            ok = hit(expected, types)
            b["hits"] += ok
            if not ok:
                b["misses"].append((pair_id, types))
            w.writerow([pair_id, expected, row["status"], ";".join(types), ok, wall])

    lines = ["# Scoring summary", "",
             f"results: `{args.results}`  labels: `{args.labels}`", "",
             "| Expected | Pairs | Hits | Accuracy | Errors | Avg ms | Median ms |",
             "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
    tot_n = tot_h = 0
    for exp in sorted(per_type):
        b = per_type[exp]
        ws = walls[exp]
        tot_n += b["n"]
        tot_h += b["hits"]
        lines.append(f"| {exp} | {b['n']} | {b['hits']} | {b['hits']/b['n']:.2%} | "
                     f"{b['errors']} | {int(statistics.mean(ws))} | {int(statistics.median(ws))} |")
    if tot_n:
        lines += ["", f"**Overall (determinate labels): {tot_h}/{tot_n} = {tot_h/tot_n:.2%}**"]
    if inconclusive:
        lines += ["", f"INCONCLUSIVE pairs (excluded, {len(inconclusive)}):"]
        lines += [f"- {p}: found {';'.join(t) or '(none)'}" for p, t in inconclusive]
    misses = [(e, p, t) for e, b in per_type.items() for p, t in b["misses"]]
    if misses:
        lines += ["", "## Misses"]
        lines += [f"- {p} (expected {e}): found {';'.join(t) or '(none)'}" for e, p, t in misses]
    if missing:
        lines += ["", f"Pairs in labels but not in results: {missing}"]
    (args.out / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
