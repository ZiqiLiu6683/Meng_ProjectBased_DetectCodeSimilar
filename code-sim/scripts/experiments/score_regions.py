#!/usr/bin/env python3
"""
Region-level scorer for a corpus where ONE pair carries SEVERAL reference regions of
DIFFERENT clone types.

`score_results_v2.py` scores one `expected_type` per pair, which is the right shape for
BigCloneBench (one reference function pair per execution) but cannot express this corpus: a
seed is mutated in two disjoint statement ranges, so a single pair holds a T2 region and a T3
region at once, plus untouched code between them. Scoring such a pair against a single expected
type would either discard half the ground truth or average two different relationships into one
verdict.

Scoring follows `docs/../evaluation/paper_evaluation_protocol.md` §4 exactly:

  reference coverage = |G n P| / |G|      boundary precision = |G n P| / |P|
  IoU               = |G n P| / |G u P|
  c-match           = min(coverage_left, coverage_right) >= 0.70

The primary matched prediction for a reference is chosen by boundary quality WITHOUT consulting
its type -- max min-side IoU, then max min-side boundary precision, then stable candidate id --
so the scorer can never search the overlapping predictions for whichever one happens to carry
the expected label.

Right-hand reference ranges are not stored by the generator. Statement insertion and deletion
shift line numbers, so the right range is derived here by diffing the two printed sides. That is
exact for this corpus because the generator prints both sides from the same Spoon configuration
and edit locality was measured at 100%: every line outside a mutated range is byte-identical, so
the diff alignment cannot drift.

Usage:
  score_regions.py --results run/merged.jsonl --regions regions_left.csv \
                   --manifest manifest.csv --out scored/
"""

from __future__ import annotations

import argparse
import csv
import difflib
import json
import statistics
from collections import Counter, defaultdict
from pathlib import Path

C_MATCH = 0.70


def read_jsonl(path: Path) -> dict[str, dict]:
    results: dict[str, dict] = {}
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if not line.startswith('{"schemaVersion"'):
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError:
            continue
        results[record["pairId"]] = record
    return results


def right_range(left_lines: list[str], right_lines: list[str],
                begin: int, end: int) -> tuple[int, int] | None:
    """Map a left line range onto the right side using the diff alignment.

    Equal blocks align line for line, so a left line inside one has an exact counterpart. A left
    line inside a changed block has no single counterpart, so the whole opposing block is taken:
    that is the honest reading, since the edit really did replace that span.
    """
    matcher = difflib.SequenceMatcher(a=left_lines, b=right_lines, autojunk=False)
    lo: int | None = None
    hi: int | None = None
    for tag, i1, i2, j1, j2 in matcher.get_opcodes():
        # opcode covers left lines [i1+1, i2] and right lines [j1+1, j2] in 1-based terms
        overlap_start = max(begin, i1 + 1)
        overlap_end = min(end, i2)
        if overlap_start > overlap_end:
            continue
        if tag == "equal":
            r_start = j1 + (overlap_start - (i1 + 1)) + 1
            r_end = j1 + (overlap_end - (i1 + 1)) + 1
        else:
            r_start = j1 + 1
            r_end = j2
        if r_end < r_start:
            continue
        lo = r_start if lo is None else min(lo, r_start)
        hi = r_end if hi is None else max(hi, r_end)
    if lo is None or hi is None:
        return None
    return lo, hi


def segments_of(endpoint: dict) -> list[tuple[int, int]]:
    """The runs the verdict actually covers, falling back to the bounding box for old results.

    A region is not necessarily contiguous: `beginLine`/`endLine` are only the minimum and maximum
    over its statements, so a region reported as lines 12-100 may really be 12-17, 22-23 and 96-100.
    Scoring the bounding box lets a prediction that encloses the reference score full coverage while
    having examined almost none of it, which is exactly what the protocol's parallel substance-line
    reporting exists to prevent.
    """
    raw = endpoint.get("segments")
    if raw:
        return [(int(s["begin"]), int(s["end"])) for s in raw]
    return [(int(endpoint["beginLine"]), int(endpoint["endLine"]))]


def lines_of(segments: list[tuple[int, int]]) -> set[int]:
    covered: set[int] = set()
    for begin, end in segments:
        if end >= begin >= 1:
            covered.update(range(begin, end + 1))
    return covered


def quality(reference: list[tuple[int, int]],
            prediction: list[tuple[int, int]]) -> tuple[float, float, float]:
    g = lines_of(reference)
    p = lines_of(prediction)
    inter = len(g & p)
    union = len(g | p)
    coverage = inter / len(g) if g else 0.0
    precision = inter / len(p) if p else 0.0
    return coverage, precision, (inter / union if union else 0.0)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--results", required=True, type=Path)
    parser.add_argument("--regions", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--c-match", type=float, default=C_MATCH)
    args = parser.parse_args()

    results = read_jsonl(args.results)
    manifest = {row["pair_id"]: row for row in csv.DictReader(args.manifest.open(encoding="utf-8"))}
    references: dict[str, list[dict]] = defaultdict(list)
    for row in csv.DictReader(args.regions.open(encoding="utf-8")):
        references[row["pair_id"]].append(row)

    args.out.mkdir(parents=True, exist_ok=True)
    scored_rows: list[dict] = []
    source_cache: dict[str, list[str]] = {}

    # Named distinctly from the module-level lines_of(segments): the two take different things and
    # a shadowed name here would silently change what quality() compares.
    def source_lines(path: str) -> list[str]:
        if path not in source_cache:
            source_cache[path] = Path(path).read_text(encoding="utf-8", errors="replace").splitlines()
        return source_cache[path]

    for pair_id, refs in sorted(references.items()):
        record = results.get(pair_id)
        entry = manifest.get(pair_id)
        if entry is None:
            continue
        left_lines = source_lines(entry["left_path"])
        right_lines = source_lines(entry["right_path"])

        predictions = []
        if record is not None:
            for region in (record.get("report") or {}).get("regions") or []:
                predictions.append({
                    "id": region.get("candidateId", ""),
                    "type": region.get("type", ""),
                    "left": segments_of(region["left"]),
                    "right": segments_of(region["right"]),
                    "left_box": [(region["left"]["beginLine"], region["left"]["endLine"])],
                    "right_box": [(region["right"]["beginLine"], region["right"]["endLine"])],
                })

        for ref in refs:
            g_left_span = (int(ref["left_begin"]), int(ref["left_end"]))
            g_right_span = right_range(left_lines, right_lines, *g_left_span)
            g_left = [g_left_span]
            g_right = None if g_right_span is None else [g_right_span]
            row = {
                "pair_id": pair_id,
                "region_index": ref["region_index"],
                "expected": ref["clone_type"],
                "operator": ref["operator"],
                "analysis_mode": "" if record is None else record.get("analysisMode", ""),
                "status": "missing" if record is None else record.get("status", ""),
                "left_begin": g_left_span[0], "left_end": g_left_span[1],
                "right_begin": "" if g_right_span is None else g_right_span[0],
                "right_end": "" if g_right_span is None else g_right_span[1],
                "detected": False, "primary_type": "", "type_correct": False,
                "coverage_min": 0.0, "precision_min": 0.0, "iou_min": 0.0,
                "box_coverage_min": 0.0, "box_precision_min": 0.0, "box_iou_min": 0.0,
                "box_detected": False,
                "predictions": len(predictions),
            }
            if g_right is not None and predictions:
                ranked = []
                box_ranked = []
                for prediction in predictions:
                    cl, pl, il = quality(g_left, prediction["left"])
                    cr, pr, ir = quality(g_right, prediction["right"])
                    ranked.append((min(il, ir), min(pl, pr), min(cl, cr), prediction))
                    # Control arm: the same computation on the bounding box, kept so the report can
                    # quantify how much the old convention overstated coverage.
                    bcl, bpl, bil = quality(g_left, prediction["left_box"])
                    bcr, bpr, bir = quality(g_right, prediction["right_box"])
                    box_ranked.append((min(bil, bir), min(bpl, bpr), min(bcl, bcr)))
                box_ranked.sort(key=lambda item: (-item[0], -item[1], -item[2]))
                row["box_iou_min"] = round(box_ranked[0][0], 4)
                row["box_precision_min"] = round(box_ranked[0][1], 4)
                row["box_coverage_min"] = round(box_ranked[0][2], 4)
                row["box_detected"] = box_ranked[0][2] >= args.c_match
                # Boundary quality first, type never consulted; candidate id breaks ties stably.
                ranked.sort(key=lambda item: (-item[0], -item[1], -item[2], item[3]["id"]))
                best_iou, best_precision, best_coverage, best = ranked[0]
                row["coverage_min"] = round(best_coverage, 4)
                row["precision_min"] = round(best_precision, 4)
                row["iou_min"] = round(best_iou, 4)
                if best_coverage >= args.c_match:
                    row["detected"] = True
                    row["primary_type"] = best["type"]
                    row["type_correct"] = best["type"] == ref["clone_type"]
            scored_rows.append(row)

    fields = list(scored_rows[0].keys()) if scored_rows else []
    with (args.out / "scored_regions.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        writer.writerows(scored_rows)

    lines = ["# Region-level scoring summary", "",
             f"c-match threshold: {args.c_match:.2f} minimum-side reference coverage.",
             "Primary matched prediction chosen by boundary quality without consulting its type.",
             "Regions from pairs with no result stay in the denominator.", "",
             "| Expected | N | Detection | Typed recall | Conditional type accuracy "
             "| Median min IoU | Median min boundary precision |",
             "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"]

    control: list[str] = []
    for expected in sorted({row["expected"] for row in scored_rows}):
        group = [row for row in scored_rows if row["expected"] == expected]
        detected = [row for row in group if row["detected"]]
        correct = [row for row in detected if row["type_correct"]]
        ious = sorted(row["iou_min"] for row in detected) or [0.0]
        precisions = sorted(row["precision_min"] for row in detected) or [0.0]
        box_detected = [row for row in group if row["box_detected"]]
        box_ious = sorted(row["box_iou_min"] for row in box_detected) or [0.0]
        control.append(
            f"| {expected} | {len(group)} | {len(box_detected) / len(group) * 100:.2f}% "
            f"| {statistics.median(box_ious):.3f} |")
        lines.append(
            f"| {expected} | {len(group)} | {len(detected) / len(group) * 100:.2f}% "
            f"| {len(correct) / len(group) * 100:.2f}% "
            f"| {(len(correct) / len(detected) * 100 if detected else 0.0):.2f}% "
            f"| {statistics.median(ious):.3f} | {statistics.median(precisions):.3f} |")

    lines += ["", "## Control: same scoring on the bounding box (begin..end)", "",
              "Reported because the difference is the point: a prediction that merely ENCLOSES the",
              "reference scores full coverage here while covering almost none of it.", "",
              "| Expected | N | Detection | Median min IoU |", "| --- | ---: | ---: | ---: |"]
    lines += control

    confusion = Counter((row["expected"], row["primary_type"] or "NOT_DETECTED") for row in scored_rows)
    lines += ["", "## Type confusion (expected -> primary matched prediction)", "",
              "| Expected | Predicted | N |", "| --- | --- | ---: |"]
    for (expected, predicted), count in sorted(confusion.items()):
        lines.append(f"| {expected} | {predicted} | {count} |")

    modes = Counter(row["analysis_mode"] or "MISSING" for row in scored_rows)
    lines += ["", "## Execution provenance", "", "| Analysis mode | Regions |", "| --- | ---: |"]
    for mode, count in sorted(modes.items()):
        lines.append(f"| {mode} | {count} |")

    summary = "\n".join(lines) + "\n"
    (args.out / "summary.md").write_text(summary, encoding="utf-8")
    print(summary)


if __name__ == "__main__":
    main()
