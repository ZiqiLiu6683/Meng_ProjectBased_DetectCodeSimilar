#!/usr/bin/env python3
"""Strict, region-aware scorer for the versioned pairwise benchmark schema.

This scorer keeps four questions separate:

1. Did any strict clone prediction c-match the reference pair at 70% coverage?
2. What is the type of the single boundary-best c-matched prediction?
3. How precise are that prediction's boundaries?
4. On negative pairs, did the product emit any clone-sized region even when no
   prediction covered 70% of the full negative reference?

The primary prediction is selected without looking at its type. This avoids the
old optimistic behaviour of scanning all overlapping predictions for whichever
one has the expected label.
"""

from __future__ import annotations

import argparse
import csv
import json
import random
import statistics
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

C_MATCH_THRESHOLD = 0.70
STRICT_CLONE_TYPES = {"T1", "T2", "T3", "T4_CONFIRMED"}
OPERATIONAL_EXTRA_TYPES = {"T4_DYNAMIC_EVIDENCE"}


def stream_json_objects(path: Path):
    """Stream concatenated JSON objects and recover after a truncated top-level row."""
    decoder = json.JSONDecoder()
    markers = ('\n{"schemaVersion"', '\n{"pairId"')
    buffer = ""
    skipped = 0
    with path.open(encoding="utf-8", errors="replace") as source:
        while True:
            chunk = source.read(1 << 20)
            buffer += chunk
            while True:
                stripped = buffer.lstrip()
                if not stripped:
                    buffer = ""
                    break
                try:
                    value, end = decoder.raw_decode(stripped)
                    yield value
                    buffer = stripped[end:]
                except json.JSONDecodeError:
                    cuts = [stripped.find(marker, 1) for marker in markers]
                    cuts = [cut for cut in cuts if cut != -1]
                    if cuts:
                        skipped += 1
                        buffer = stripped[min(cuts) + 1:]
                        continue
                    buffer = stripped
                    break
            if not chunk:
                if buffer.strip():
                    skipped += 1
                break
    if skipped:
        print(f"[score-v2] warning: {skipped} truncated result row(s) skipped")


def fragment_range(java_file: Path) -> tuple[int, int] | None:
    """Reference range of a BCB fragment wrapped inside one generated class."""
    try:
        lines = java_file.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return None
    start = next((index + 2 for index, line in enumerate(lines)
                  if line.startswith("public class ")), None)
    end = next((index for index in range(len(lines), 0, -1)
                if lines[index - 1].strip() == "}"), None)
    if start is None or end is None or end - 1 < start:
        # Non-BCB manifests may contain complete files. Their explicit intervals
        # are preferred; whole-file fallback keeps development manifests usable.
        return (1, len(lines)) if lines else None
    return start, end - 1


def explicit_range(row: dict[str, str], side: str) -> tuple[int, int] | None:
    begin = row.get(f"{side}_begin", "").strip()
    end = row.get(f"{side}_end", "").strip()
    if not begin and not end:
        return None
    try:
        parsed = int(begin), int(end)
    except ValueError:
        return None
    return parsed if parsed[0] > 0 and parsed[1] >= parsed[0] else None


def reference_ranges(manifest_row: dict[str, str]) -> tuple[tuple[int, int], tuple[int, int]] | None:
    left = explicit_range(manifest_row, "left")
    right = explicit_range(manifest_row, "right")
    if left is None:
        left = fragment_range(Path(manifest_row["left_path"]))
    if right is None:
        right = fragment_range(Path(manifest_row["right_path"]))
    return (left, right) if left is not None and right is not None else None


def type_family(label: str) -> str:
    if label == "T4_CONFIRMED":
        return "T4"
    if label == "T4_DYNAMIC_EVIDENCE":
        return "T4_DYNAMIC"
    if label.startswith("POSSIBLE_T4"):
        return "POSSIBLE_T4"
    if label.startswith("T1"):
        return "T1"
    if label.startswith("T2"):
        return "T2"
    if label.startswith("T3"):
        return "T3"
    return label


def counts_as_detection(label: str, operational: bool) -> bool:
    return label in STRICT_CLONE_TYPES or (operational and label in OPERATIONAL_EXTRA_TYPES)


@dataclass(frozen=True)
class SideMetrics:
    coverage: float
    precision: float
    iou: float
    predicted_lines: int


@dataclass(frozen=True)
class RegionMatch:
    candidate_id: str
    predicted_type: str
    left: SideMetrics
    right: SideMetrics

    @property
    def min_coverage(self) -> float:
        return min(self.left.coverage, self.right.coverage)

    @property
    def min_precision(self) -> float:
        return min(self.left.precision, self.right.precision)

    @property
    def min_iou(self) -> float:
        return min(self.left.iou, self.right.iou)

    @property
    def c_match(self) -> bool:
        return self.min_coverage >= C_MATCH_THRESHOLD

    def selection_key(self):
        # Type deliberately absent from this key. Ascending candidate ID is the
        # final stable tie-break after descending boundary quality.
        return -self.min_iou, -self.min_precision, -self.min_coverage, self.candidate_id


def side_metrics(endpoint: dict, reference: tuple[int, int]) -> SideMetrics:
    try:
        predicted = int(endpoint["beginLine"]), int(endpoint["endLine"])
    except (KeyError, TypeError, ValueError):
        return SideMetrics(0.0, 0.0, 0.0, 0)
    if predicted[0] <= 0 or predicted[1] < predicted[0]:
        return SideMetrics(0.0, 0.0, 0.0, 0)
    intersection = max(0, min(reference[1], predicted[1]) - max(reference[0], predicted[0]) + 1)
    reference_size = reference[1] - reference[0] + 1
    predicted_size = predicted[1] - predicted[0] + 1
    union = reference_size + predicted_size - intersection
    return SideMetrics(
        intersection / reference_size,
        intersection / predicted_size,
        intersection / union if union else 0.0,
        predicted_size,
    )


def region_matches(result: dict, references, operational: bool) -> list[RegionMatch]:
    matches = []
    for index, region in enumerate((result.get("report") or {}).get("regions", [])):
        predicted_type = region.get("type", "")
        if not counts_as_detection(predicted_type, operational):
            continue
        matches.append(RegionMatch(
            str(region.get("candidateId", f"region-{index}")),
            predicted_type,
            side_metrics(region.get("left", {}), references[0]),
            side_metrics(region.get("right", {}), references[1]),
        ))
    return matches


def select_result_rows(path: Path, policy: str) -> tuple[dict[str, dict], Counter]:
    selected: dict[str, dict] = {}
    duplicates = Counter()
    for row in stream_json_objects(path):
        pair_id = row.get("pairId", "")
        if not pair_id:
            continue
        if pair_id in selected:
            duplicates[pair_id] += 1
            if policy == "first":
                continue
        selected[pair_id] = row
    return selected, duplicates


def score_pair(label: dict[str, str], manifest: dict[str, str], result: dict | None,
               operational: bool, min_region_lines: int) -> dict:
    pair_id = label["pair_id"]
    expected = label["expected_type"]
    base = {
        "pair_id": pair_id,
        "expected": expected,
        "band": label.get("band", ""),
        "cluster_id": label.get("cluster_id") or label.get("functionality_id") or pair_id,
        "status": "missing_result" if result is None else result.get("status", "unknown"),
        "analysis_mode": "" if result is None else result.get("analysisMode", "LEGACY_UNKNOWN"),
        "fallback_stage": "" if result is None else result.get("fallbackStage", ""),
        "fallback_reason": "" if result is None else result.get("fallbackReason", ""),
        "detected": False,
        "primary_type": "NO_DETECTION",
        "type_correct": False,
        "any_correct_overlap": False,
        "reference_range_fp": False,
        "strict_product_fp": False,
        "candidate_id": "",
        "coverage_left": 0.0,
        "coverage_right": 0.0,
        "boundary_precision_left": 0.0,
        "boundary_precision_right": 0.0,
        "iou_left": 0.0,
        "iou_right": 0.0,
        "wall_ms": 0 if result is None else result.get("wallMs", 0),
    }
    references = reference_ranges(manifest)
    if references is None:
        base["status"] = "invalid_reference"
        return base
    if result is None or result.get("status") != "ok":
        base["primary_type"] = "ERROR_OR_MISSING"
        return base

    matches = region_matches(result, references, operational)
    covering = [match for match in matches if match.c_match]
    primary = min(covering, key=lambda match: match.selection_key(), default=None)
    expected_family = type_family(expected)
    base["strict_product_fp"] = any(
        match.left.predicted_lines >= min_region_lines
        and match.right.predicted_lines >= min_region_lines
        for match in matches
    )
    base["reference_range_fp"] = bool(covering)
    base["any_correct_overlap"] = any(
        type_family(match.predicted_type) == expected_family for match in covering
    )
    if primary is None:
        return base

    base.update({
        "detected": True,
        "primary_type": primary.predicted_type,
        "type_correct": type_family(primary.predicted_type) == expected_family,
        "candidate_id": primary.candidate_id,
        "coverage_left": primary.left.coverage,
        "coverage_right": primary.right.coverage,
        "boundary_precision_left": primary.left.precision,
        "boundary_precision_right": primary.right.precision,
        "iou_left": primary.left.iou,
        "iou_right": primary.right.iou,
    })
    return base


def percentile(values: list[float], probability: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = round((len(ordered) - 1) * probability)
    return ordered[max(0, min(len(ordered) - 1, index))]


def cluster_bootstrap(records: list[dict], numerator, denominator,
                      iterations: int, seed: int) -> tuple[float, float]:
    by_cluster = defaultdict(list)
    for record in records:
        by_cluster[record["cluster_id"]].append(record)
    clusters = list(by_cluster)
    if not clusters:
        return 0.0, 0.0
    rng = random.Random(seed)
    estimates = []
    for _ in range(iterations):
        chosen = [rng.choice(clusters) for _ in clusters]
        sample = [record for cluster in chosen for record in by_cluster[cluster]]
        den = sum(1 for record in sample if denominator(record))
        if den:
            estimates.append(sum(1 for record in sample if numerator(record)) / den)
    return percentile(estimates, 0.025), percentile(estimates, 0.975)


def bool_text(value) -> str:
    return "true" if value else "false"


def format_float(value) -> str:
    return f"{float(value):.6f}"


PAIR_FIELDS = [
    "pair_id", "expected", "band", "cluster_id", "status", "analysis_mode",
    "fallback_stage", "fallback_reason", "detected", "primary_type", "type_correct",
    "any_correct_overlap", "reference_range_fp", "strict_product_fp", "candidate_id",
    "coverage_left", "coverage_right", "boundary_precision_left", "boundary_precision_right",
    "iou_left", "iou_right", "wall_ms",
]


def write_pair_scores(path: Path, records: list[dict]) -> None:
    with path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=PAIR_FIELDS)
        writer.writeheader()
        for record in records:
            row = dict(record)
            for key in ("detected", "type_correct", "any_correct_overlap",
                        "reference_range_fp", "strict_product_fp"):
                row[key] = bool_text(row[key])
            for key in ("coverage_left", "coverage_right", "boundary_precision_left",
                        "boundary_precision_right", "iou_left", "iou_right"):
                row[key] = format_float(row[key])
            writer.writerow({key: row.get(key, "") for key in PAIR_FIELDS})


def build_summary(records: list[dict], bootstrap_iterations: int, seed: int) -> str:
    groups = defaultdict(list)
    for record in records:
        groups[record["band"] or record["expected"]].append(record)
    lines = [
        "# Strict region-aware scoring summary",
        "",
        f"Primary c-match threshold: {C_MATCH_THRESHOLD:.2f} on both sides.",
        "Primary matched prediction is chosen by boundary quality without consulting its type.",
        "Errors and missing results remain in the denominator.",
        "",
        "| Group | N | Detection / correct rejection | 95% cluster CI | Typed recall | Conditional type accuracy | Median min IoU | Errors/missing | Strict product FP |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for group in sorted(groups):
        rows = groups[group]
        negative = all(row["expected"] == "NON_CLONE" for row in rows)
        if negative:
            successes = sum(row["status"] == "ok" and not row["reference_range_fp"]
                            for row in rows)
            typed = successes
            cond_den = sum(row["status"] == "ok" for row in rows)
            cond = successes / cond_den if cond_den else 0.0
            lo, hi = cluster_bootstrap(
                rows,
                lambda row: row["status"] == "ok" and not row["reference_range_fp"],
                lambda row: True,
                bootstrap_iterations, seed)
        else:
            successes = sum(row["detected"] for row in rows)
            typed = sum(row["type_correct"] for row in rows)
            detected_rows = [row for row in rows if row["detected"]]
            cond = typed / len(detected_rows) if detected_rows else 0.0
            lo, hi = cluster_bootstrap(
                rows, lambda row: row["detected"], lambda row: True,
                bootstrap_iterations, seed)
        min_ious = [min(row["iou_left"], row["iou_right"])
                    for row in rows if row["detected"]]
        errors = sum(row["status"] != "ok" for row in rows)
        strict_fp = sum(row["strict_product_fp"] for row in rows) if negative else 0
        lines.append(
            f"| {group} | {len(rows)} | {successes / len(rows):.2%} | "
            f"[{lo:.2%}, {hi:.2%}] | {typed / len(rows):.2%} | {cond:.2%} | "
            f"{statistics.median(min_ious) if min_ious else 0.0:.3f} | {errors} | {strict_fp} |"
        )
    lines.extend([
        "",
        "## Execution provenance",
        "",
        "| Analysis mode | N | Share | Successful | Errors/missing | Median wall ms |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ])
    modes = defaultdict(list)
    for record in records:
        modes[record["analysis_mode"] or "MISSING"].append(record)
    for mode in sorted(modes):
        rows = modes[mode]
        successful = sum(row["status"] == "ok" for row in rows)
        wall_times = [float(row["wall_ms"]) for row in rows if row["status"] == "ok"]
        lines.append(
            f"| {mode} | {len(rows)} | {len(rows) / len(records):.2%} | "
            f"{successful} | {len(rows) - successful} | "
            f"{statistics.median(wall_times) if wall_times else 0.0:.0f} |"
        )

    fallback_causes = Counter(
        (record["fallback_stage"] or "UNSPECIFIED",
         record["fallback_reason"] or "UNSPECIFIED")
        for record in records
        if record["analysis_mode"] == "SOURCE_ONLY_FALLBACK"
    )
    if fallback_causes:
        lines.extend([
            "",
            "### Source-only fallback causes",
            "",
            "| Stage | Reason | N |",
            "| --- | --- | ---: |",
        ])
        for (stage, reason), count in sorted(fallback_causes.items()):
            lines.append(f"| {stage} | {reason} | {count} |")
    return "\n".join(lines) + "\n"


def write_confusion(path: Path, records: list[dict]) -> None:
    expected_values = sorted({type_family(row["expected"]) for row in records})
    predicted_values = sorted({type_family(row["primary_type"]) for row in records})
    counts = Counter((type_family(row["expected"]), type_family(row["primary_type"]))
                     for row in records)
    with path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.writer(target)
        writer.writerow(["expected\\predicted", *predicted_values])
        for expected in expected_values:
            writer.writerow([expected, *[counts[(expected, predicted)]
                                         for predicted in predicted_values]])


def write_per_mode(path: Path, records: list[dict]) -> None:
    groups = defaultdict(list)
    for record in records:
        groups[(record["band"] or record["expected"], record["analysis_mode"] or "MISSING")].append(record)
    with path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.writer(target)
        writer.writerow(["group", "analysis_mode", "n", "detected", "typed", "errors"])
        for (group, mode), rows in sorted(groups.items()):
            writer.writerow([
                group, mode, len(rows), sum(row["detected"] for row in rows),
                sum(row["type_correct"] for row in rows),
                sum(row["status"] != "ok" for row in rows),
            ])


def run(args) -> list[dict]:
    args.out.mkdir(parents=True, exist_ok=True)
    with args.manifest.open(newline="", encoding="utf-8-sig") as source:
        manifest = {row["pair_id"]: row for row in csv.DictReader(source)}
    for row in manifest.values():
        for field in ("left_path", "right_path"):
            path = Path(row[field])
            if not path.is_absolute():
                row[field] = str((args.manifest.parent / path).resolve())

    if args.labels is None:
        labels = manifest
        missing_embedded = [pair_id for pair_id, row in manifest.items()
                            if not row.get("expected_type")]
        if missing_embedded:
            raise SystemExit("--labels is required because the manifest has no embedded label; "
                             f"first missing pair: {missing_embedded[0]}")
    else:
        with args.labels.open(newline="", encoding="utf-8-sig") as source:
            all_labels = {row["pair_id"]: row for row in csv.DictReader(source)}
        missing_labels = sorted(set(manifest) - set(all_labels))
        if missing_labels:
            raise SystemExit(f"manifest contains {len(missing_labels)} unlabeled pair(s); "
                             f"first: {missing_labels[0]}")
        labels = {pair_id: all_labels[pair_id] for pair_id in manifest}
        for pair_id, label in labels.items():
            embedded = manifest[pair_id].get("expected_type", "")
            if embedded and embedded != label.get("expected_type", ""):
                raise SystemExit(f"label mismatch for {pair_id}: manifest={embedded}, "
                                 f"labels={label.get('expected_type', '')}")
    result_rows, duplicates = select_result_rows(args.results, args.attempt_policy)
    operational = args.detection_policy == "operational"

    scores = [score_pair(label, manifest[pair_id], result_rows.get(pair_id),
                         operational, args.min_region_lines)
              for pair_id, label in labels.items()]
    write_pair_scores(args.out / "scored_pairs.csv", scores)
    write_confusion(args.out / "confusion_matrix.csv", scores)
    write_per_mode(args.out / "per_analysis_mode.csv", scores)
    summary = build_summary(scores, args.bootstrap_iterations, args.seed)
    if duplicates:
        summary += f"\nDuplicate/retry rows observed: {sum(duplicates.values())}; " \
                   f"attempt policy: {args.attempt_policy}.\n"
    (args.out / "summary.md").write_text(summary, encoding="utf-8")
    print(summary)
    return scores


def parse_args(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--results", required=True, type=Path)
    parser.add_argument("--labels", type=Path,
                        help="optional legacy sidecar; v2 manifests embed their labels")
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--detection-policy", choices=("strict", "operational"), default="strict")
    parser.add_argument("--attempt-policy", choices=("first", "last"), default="first")
    parser.add_argument("--min-region-lines", type=int, default=6)
    parser.add_argument("--bootstrap-iterations", type=int, default=2000)
    parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args(argv)


if __name__ == "__main__":
    run(parse_args())
