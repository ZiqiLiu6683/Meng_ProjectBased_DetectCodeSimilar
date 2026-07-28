#!/usr/bin/env python3
"""
Scorer for the region corpus, which carries three reference kinds at two scales.

`score_regions.py` scores one kind of reference against regions. This corpus separates what the
protocol has always asked to be separated (§6: "retain the official clone intervals AND the mutation
interval"), because conflating them is what produced a nine-line "T3" reference containing a single
inserted statement and eight identical lines:

  CLONE_INTERVAL -> matched against REGIONS      : did the system find the corresponding block?
  MUTATION       -> matched against SUB-REGIONS  : did it identify the edit, and of which kind?
  UNTOUCHED      -> matched against SUB-REGIONS  : is code that was NOT changed recognised as T1?

Two different questions need two different rules, both preregistered:

  c-match          min(reference coverage per side) >= 0.70          -- §4, for clone intervals
  mutation-capture the prediction OVERLAPS the changed lines at all  -- §6, for mutations

A reference may be one-sided: a deletion has no right-hand extent and an insertion no left-hand one.
Coverage is then taken over the side that exists; scoring the empty side would make every deletion
unmatchable regardless of what the system reported.

The primary matched prediction is chosen by boundary quality WITHOUT consulting its type -- maximum
minimum-side IoU, then boundary precision, then stable id -- so the scorer can never pick, among
overlapping predictions, whichever one happens to carry the expected label.

Usage:
  score_region_corpus.py --results run/merged.jsonl --references regions_left.csv \
                         --manifest manifest.csv --out scored/
"""

from __future__ import annotations

import argparse
import csv
import json
import statistics
from collections import Counter, defaultdict
from pathlib import Path

C_MATCH = 0.70


def read_results(path: Path) -> dict[str, dict]:
    out: dict[str, dict] = {}
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if not line.startswith('{"schemaVersion"'):
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError:
            continue
        out[record["pairId"]] = record
    return out


def span(begin: str, end: str) -> set[int]:
    if not begin or not end:
        return set()
    return set(range(int(begin), int(end) + 1))


def region_lines(endpoint: dict) -> set[int]:
    """A region's real content: its runs when present, else the bounding box.

    The box is only the minimum and maximum over the region's statements, so it can enclose code the
    verdict never examined; measured on this corpus, 58% of regions consist of more than one run and
    the worst case reported 13 lines of content as an 89-line span.
    """
    segments = endpoint.get("segments")
    if segments:
        out: set[int] = set()
        for segment in segments:
            out.update(range(int(segment["begin"]), int(segment["end"]) + 1))
        return out
    return span(str(endpoint.get("beginLine", "")), str(endpoint.get("endLine", "")))


def sub_lines(runs: list[dict]) -> set[int]:
    out: set[int] = set()
    for run in runs or []:
        out.update(range(int(run["begin"]), int(run["end"]) + 1))
    return out


def quality(reference: tuple[set[int], set[int]],
            prediction: tuple[set[int], set[int]]) -> tuple[float, float, float]:
    """Minimum-side coverage, boundary precision and IoU, over the sides the reference has."""
    coverages: list[float] = []
    precisions: list[float] = []
    ious: list[float] = []
    for ref, pred in zip(reference, prediction):
        if not ref:
            continue  # one-sided reference: a deletion has no right side, an insertion no left
        inter = len(ref & pred)
        union = len(ref | pred)
        coverages.append(inter / len(ref))
        precisions.append(inter / len(pred) if pred else 0.0)
        ious.append(inter / union if union else 0.0)
    if not coverages:
        return 0.0, 0.0, 0.0
    return min(coverages), min(precisions), min(ious)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--results", required=True, type=Path)
    parser.add_argument("--references", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--c-match", type=float, default=C_MATCH)
    args = parser.parse_args()

    results = read_results(args.results)
    references: dict[str, list[dict]] = defaultdict(list)
    for row in csv.DictReader(args.references.open(encoding="utf-8")):
        references[row["pair_id"]].append(row)

    args.out.mkdir(parents=True, exist_ok=True)
    scored: list[dict] = []

    for pair_id, refs in sorted(references.items()):
        record = results.get(pair_id)
        regions = []
        subs = []
        if record is not None:
            for region in (record.get("report") or {}).get("regions") or []:
                regions.append({
                    "id": region.get("candidateId", ""),
                    "type": region.get("type", ""),
                    "left": region_lines(region["left"]),
                    "right": region_lines(region["right"]),
                })
                for index, sub in enumerate(region.get("subRegions") or []):
                    subs.append({
                        "id": f"{region.get('candidateId', '')}#{index}",
                        "type": sub.get("type", ""),
                        "left": sub_lines(sub.get("left")),
                        "right": sub_lines(sub.get("right")),
                    })

        for ref in refs:
            reference = (span(ref["left_begin"], ref["left_end"]),
                         span(ref["right_begin"], ref["right_end"]))
            against = regions if ref["kind"] == "CLONE_INTERVAL" else subs
            row = {
                "pair_id": pair_id,
                "ref_index": ref["ref_index"],
                "kind": ref["kind"],
                "expected": ref["clone_type"],
                "operator": ref["operator"].rsplit("_x", 1)[0],
                "status": "missing" if record is None else record.get("status", ""),
                "candidates": len(against),
                "matched": False, "predicted_type": "", "type_correct": False,
                "coverage": 0.0, "precision": 0.0, "iou": 0.0,
            }
            if against and (reference[0] or reference[1]):
                ranked = []
                for prediction in against:
                    c, p, i = quality(reference, (prediction["left"], prediction["right"]))
                    ranked.append((i, p, c, prediction["id"], prediction))
                ranked.sort(key=lambda item: (-item[0], -item[1], -item[2], item[3]))
                iou, precision, coverage, _, best = ranked[0]
                row["coverage"] = round(coverage, 4)
                row["precision"] = round(precision, 4)
                row["iou"] = round(iou, 4)
                # A clone interval must be COVERED; a mutation only has to be reached.
                threshold_met = (coverage >= args.c_match if ref["kind"] == "CLONE_INTERVAL"
                                 else coverage > 0.0)
                if threshold_met:
                    row["matched"] = True
                    row["predicted_type"] = best["type"]
                    row["type_correct"] = best["type"] == ref["clone_type"]
            scored.append(row)

    fields = list(scored[0].keys()) if scored else []
    with (args.out / "scored_references.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        writer.writerows(scored)

    lines = ["# Region corpus scoring summary", "",
             f"Clone intervals: c-match = minimum-side reference coverage >= {args.c_match:.2f}, "
             "scored against REGIONS.",
             "Mutations and untouched runs: capture = any overlap (protocol §6), scored against "
             "SUB-REGIONS.",
             "Primary match chosen by boundary quality without consulting its type.", ""]

    for kind, title in (("CLONE_INTERVAL", "Clone intervals vs regions"),
                        ("MUTATION", "Mutations vs sub-regions"),
                        ("UNTOUCHED", "Untouched runs vs sub-regions")):
        group = [row for row in scored if row["kind"] == kind]
        if not group:
            continue
        lines += ["", f"## {title}", "",
                  "| Expected | Operator | N | Matched | Type correct | Conditional type accuracy "
                  "| Median IoU |", "| --- | --- | ---: | ---: | ---: | ---: | ---: |"]
        keys = sorted({(row["expected"], row["operator"]) for row in group})
        for expected, operator in keys:
            rows = [row for row in group if row["expected"] == expected and row["operator"] == operator]
            matched = [row for row in rows if row["matched"]]
            correct = [row for row in matched if row["type_correct"]]
            ious = sorted(row["iou"] for row in matched) or [0.0]
            lines.append(
                f"| {expected} | {operator} | {len(rows)} "
                f"| {len(matched) / len(rows) * 100:.1f}% "
                f"| {len(correct) / len(rows) * 100:.1f}% "
                f"| {(len(correct) / len(matched) * 100 if matched else 0.0):.1f}% "
                f"| {statistics.median(ious):.3f} |")

    confusion = Counter((row["kind"], row["expected"], row["predicted_type"] or "NOT_MATCHED")
                        for row in scored)
    lines += ["", "## Confusion (kind, expected -> primary matched prediction)", "",
              "| Kind | Expected | Predicted | N |", "| --- | --- | --- | ---: |"]
    for (kind, expected, predicted), count in sorted(confusion.items()):
        lines.append(f"| {kind} | {expected} | {predicted} | {count} |")

    summary = "\n".join(lines) + "\n"
    (args.out / "summary.md").write_text(summary, encoding="utf-8")
    print(summary)


if __name__ == "__main__":
    main()
