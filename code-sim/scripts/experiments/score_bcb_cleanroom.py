#!/usr/bin/env python3
"""Strict scorer for clean-room BCB original-file executions."""

from __future__ import annotations

import argparse
import csv
import json
import random
import statistics
import subprocess
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))
import bcb_cleanroom
import execution_manifest


RESULT_SCHEMA_VERSION = "4.0"
OK_MODES = {
    "SOURCE_PLUS_WALA_SMT",
    "SOURCE_PLUS_PROJECT_CONTEXT_WALA_SMT",
    "SOURCE_PLUS_STUBBED_WALA_SMT",
    "SOURCE_ONLY_FALLBACK",
}
C_MATCH_THRESHOLD = 0.70
STRICT_CLONE_TYPES = {"T1", "T2", "T3", "T4_CONFIRMED"}
PAIR_FIELDS = [
    "pair_id", "expected", "band", "cluster_id", "status", "analysis_mode",
    "fallback_stage", "fallback_reason", "detected", "primary_type", "type_correct",
    "any_correct_overlap", "reference_range_fp", "strict_product_fp", "candidate_id",
    "coverage_left", "coverage_right", "boundary_precision_left", "boundary_precision_right",
    "iou_left", "iou_right", "wall_ms",
]


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
    def min_iou(self) -> float:
        return min(self.left.iou, self.right.iou)

    @property
    def min_precision(self) -> float:
        return min(self.left.precision, self.right.precision)

    def selection_key(self):
        return (-self.min_iou, -self.min_precision, -self.min_coverage, self.candidate_id)


def type_family(label: str) -> str:
    if label.startswith("T1"):
        return "T1"
    if label.startswith("T2"):
        return "T2"
    if label.startswith("T3"):
        return "T3"
    return label


def side_metrics(endpoint: dict, begin: int, end: int) -> SideMetrics:
    try:
        predicted_begin = int(endpoint["beginLine"])
        predicted_end = int(endpoint["endLine"])
    except (KeyError, TypeError, ValueError):
        return SideMetrics(0.0, 0.0, 0.0, 0)
    if predicted_begin <= 0 or predicted_end < predicted_begin:
        return SideMetrics(0.0, 0.0, 0.0, 0)
    intersection = max(0, min(end, predicted_end) - max(begin, predicted_begin) + 1)
    reference_size = end - begin + 1
    predicted_size = predicted_end - predicted_begin + 1
    union = reference_size + predicted_size - intersection
    return SideMetrics(
        intersection / reference_size,
        intersection / predicted_size,
        intersection / union if union else 0.0,
        predicted_size,
    )


def strict_region_matches(result: dict, reference: dict[str, str]) -> list[RegionMatch]:
    left_begin, left_end = int(reference["left_begin"]), int(reference["left_end"])
    right_begin, right_end = int(reference["right_begin"]), int(reference["right_end"])
    matches = []
    for index, region in enumerate((result.get("report") or {}).get("regions", [])):
        predicted_type = str(region.get("type", ""))
        if predicted_type not in STRICT_CLONE_TYPES:
            continue
        matches.append(RegionMatch(
            str(region.get("candidateId", f"region-{index}")),
            predicted_type,
            side_metrics(region.get("left", {}), left_begin, left_end),
            side_metrics(region.get("right", {}), right_begin, right_end),
        ))
    return matches


def score_reference(reference: dict[str, str], result: dict | None,
                    min_region_lines: int) -> dict:
    expected = reference["expected_type"]
    base = {
        "pair_id": reference["reference_id"],
        "expected": expected,
        "band": reference.get("band", ""),
        "cluster_id": reference.get("cluster_id") or reference["reference_id"],
        "status": "missing_result" if result is None else result.get("status", "unknown"),
        "analysis_mode": "" if result is None else result.get("analysisMode", ""),
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
    if result is None or result.get("status") != "ok":
        base["primary_type"] = "ERROR_OR_MISSING"
        return base

    matches = strict_region_matches(result, reference)
    covering = [match for match in matches if match.min_coverage >= C_MATCH_THRESHOLD]
    primary = min(covering, key=lambda match: match.selection_key(), default=None)
    expected_family = type_family(expected)
    base["strict_product_fp"] = any(
        match.left.predicted_lines >= min_region_lines
        and match.right.predicted_lines >= min_region_lines
        for match in matches)
    base["reference_range_fp"] = bool(covering)
    base["any_correct_overlap"] = any(
        type_family(match.predicted_type) == expected_family for match in covering)
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


def cluster_interval(records: list[dict], success, iterations: int,
                     seed: int) -> tuple[float, float]:
    clusters: dict[str, list[dict]] = defaultdict(list)
    for record in records:
        clusters[record["cluster_id"]].append(record)
    names = sorted(clusters)
    if not names:
        return 0.0, 0.0
    rng = random.Random(seed)
    estimates = []
    for _ in range(iterations):
        sample = [row for _ in names for row in clusters[rng.choice(names)]]
        estimates.append(sum(1 for row in sample if success(row)) / len(sample))
    return percentile(estimates, 0.025), percentile(estimates, 0.975)


def build_metric_summary(records: list[dict], iterations: int, seed: int) -> str:
    groups: dict[str, list[dict]] = defaultdict(list)
    for record in records:
        groups[record["band"] or record["expected"]].append(record)
    lines = [
        "# Clean-room BCB region scoring summary", "",
        f"Primary c-match threshold: {C_MATCH_THRESHOLD:.2f} on both sides.",
        "The primary prediction is selected by boundary quality without consulting its type.",
        "Errors and missing executions remain in the denominator.", "",
        "| Group | N | Detection / correct rejection | 95% functionality-cluster CI | Typed recall | Conditional type accuracy | Median min IoU | Errors/missing | Strict product FP |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for group in sorted(groups):
        rows = groups[group]
        negative = all(row["expected"] == "NON_CLONE" for row in rows)
        if negative:
            success = lambda row: row["status"] == "ok" and not row["reference_range_fp"]
            successes = sum(1 for row in rows if success(row))
            typed = successes
            ok_rows = [row for row in rows if row["status"] == "ok"]
            conditional = successes / len(ok_rows) if ok_rows else 0.0
        else:
            success = lambda row: row["detected"]
            successes = sum(1 for row in rows if row["detected"])
            typed = sum(1 for row in rows if row["type_correct"])
            detected = [row for row in rows if row["detected"]]
            conditional = typed / len(detected) if detected else 0.0
        low, high = cluster_interval(rows, success, iterations, seed)
        min_ious = [min(row["iou_left"], row["iou_right"])
                    for row in rows if row["detected"]]
        errors = sum(row["status"] != "ok" for row in rows)
        strict_fp = sum(row["strict_product_fp"] for row in rows) if negative else 0
        lines.append(
            f"| {group} | {len(rows)} | {successes / len(rows):.2%} | "
            f"[{low:.2%}, {high:.2%}] | {typed / len(rows):.2%} | "
            f"{conditional:.2%} | "
            f"{statistics.median(min_ious) if min_ious else 0.0:.3f} | "
            f"{errors} | {strict_fp} |")
    return "\n".join(lines) + "\n"


def bool_text(value: bool) -> str:
    return "true" if value else "false"


def format_float(value) -> str:
    return f"{float(value):.6f}"


def write_confusion(path: Path, records: list[dict]) -> None:
    expected_values = sorted({type_family(row["expected"]) for row in records})
    predicted_values = sorted({type_family(row["primary_type"]) for row in records})
    counts = Counter((type_family(row["expected"]), type_family(row["primary_type"]))
                     for row in records)
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(["expected\\predicted", *predicted_values])
        for expected in expected_values:
            writer.writerow([expected, *[counts[(expected, predicted)]
                                         for predicted in predicted_values]])


def write_per_mode(path: Path, records: list[dict]) -> None:
    groups: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for record in records:
        groups[(record["band"] or record["expected"],
                record["analysis_mode"] or "MISSING")].append(record)
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(["group", "analysis_mode", "n", "detected", "typed", "errors"])
        for (group, mode), rows in sorted(groups.items()):
            writer.writerow([
                group, mode, len(rows), sum(row["detected"] for row in rows),
                sum(row["type_correct"] for row in rows),
                sum(row["status"] != "ok" for row in rows),
            ])


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames is None:
            raise ValueError(f"CSV has no header: {path}")
        return [
            {key: (value or "").strip() for key, value in row.items()}
            for row in reader
        ]


def load_references(path: Path, executions_path: Path, dataset_id: str,
                    executions: dict[str, dict[str, str]]) -> list[dict[str, str]]:
    rows = read_csv(path)
    if not rows:
        raise ValueError("reference table is empty")
    missing = [field for field in bcb_cleanroom.REFERENCE_FIELDS if field not in rows[0]]
    if missing:
        raise ValueError(f"reference table missing fields: {', '.join(missing)}")
    seen: set[str] = set()
    line_count_cache: dict[Path, int] = {}
    allowed = {"T1", "T2", "T3", "NON_CLONE"}
    for line_number, row in enumerate(rows, start=2):
        reference_id = row["reference_id"]
        if not reference_id or reference_id in seen:
            raise ValueError(f"line {line_number}: empty or duplicate reference_id {reference_id}")
        seen.add(reference_id)
        if row["schema_version"] != bcb_cleanroom.REFERENCE_SCHEMA_VERSION:
            raise ValueError(f"{reference_id}: unsupported reference schema")
        if row["dataset_id"] != dataset_id:
            raise ValueError(f"{reference_id}: dataset ID mismatch")
        if row["execution_id"] not in executions:
            raise ValueError(f"{reference_id}: unknown execution_id {row['execution_id']}")
        if row["expected_type"] not in allowed:
            raise ValueError(f"{reference_id}: invalid expected_type {row['expected_type']}")
        if row["expected_type"] == "T3" and row["band"] not in {"VST3", "ST3", "MT3"}:
            raise ValueError(f"{reference_id}: T3 requires an official similarity band")
        if row["expected_type"] != "T3" and row["band"]:
            raise ValueError(f"{reference_id}: only T3 may have a band")

        execution = executions[row["execution_id"]]
        for side in ("left", "right"):
            source = execution_manifest.resolve_path(
                executions_path, execution[f"{side}_path"])
            begin = int(row[f"{side}_begin"])
            end = int(row[f"{side}_end"])
            if source not in line_count_cache:
                line_count_cache[source] = len(
                    source.read_text(encoding="utf-8", errors="replace").splitlines())
            lines = line_count_cache[source]
            if begin < 1 or end < begin or end > lines:
                raise ValueError(
                    f"{reference_id}: invalid {side} range {begin}..{end} for {lines} lines")
    return rows


def strict_jsonl(path: Path) -> list[dict]:
    rows = []
    with path.open(encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, start=1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as error:
                raise ValueError(f"malformed/truncated JSONL at line {line_number}: {error}") from error
            if not isinstance(row, dict):
                raise ValueError(f"result line {line_number} is not a JSON object")
            rows.append(row)
    return rows


def select_attempts(rows: list[dict], policy: str) -> tuple[dict[str, dict], Counter[str]]:
    by_pair: dict[str, list[dict]] = defaultdict(list)
    seen_attempts: set[tuple[str, int]] = set()
    for row in rows:
        pair_id = str(row.get("pairId", ""))
        attempt = int(row.get("attempt", 0))
        if not pair_id or attempt < 1:
            raise ValueError("every result must have pairId and positive attempt")
        key = pair_id, attempt
        if key in seen_attempts:
            raise ValueError(f"duplicate result attempt {attempt} for {pair_id}")
        seen_attempts.add(key)
        by_pair[pair_id].append(row)
    selected = {}
    duplicates: Counter[str] = Counter()
    for pair_id, attempts in by_pair.items():
        attempts.sort(key=lambda row: int(row["attempt"]))
        if len(attempts) > 1:
            duplicates[pair_id] = len(attempts) - 1
        selected[pair_id] = attempts[0] if policy == "first" else attempts[-1]
    return selected, duplicates


def validate_result(row: dict, execution: dict[str, str], run_config: dict,
                    manifest_sha256: str, stub_policy: str) -> None:
    pair_id = execution["pair_id"]
    exact = {
        "schemaVersion": RESULT_SCHEMA_VERSION,
        "pairId": pair_id,
        "datasetId": run_config["dataset_id"],
        "configId": run_config["config_id"],
        "codeCommit": run_config["code_commit"],
        "dirtyWorktree": "false",
        "manifestSha256": manifest_sha256,
    }
    for field, expected in exact.items():
        if str(row.get(field, "")) != str(expected):
            raise ValueError(
                f"{pair_id}: result {field}={row.get(field)!r}, expected {expected!r}")
    status = row.get("status")
    mode = row.get("analysisMode")
    if status not in {"ok", "error"}:
        raise ValueError(f"{pair_id}: invalid result status {status}")
    if not isinstance(row.get("stages"), dict):
        raise ValueError(f"{pair_id}: missing stage provenance")
    if status == "ok" and not isinstance(row.get("compilations"), dict):
        raise ValueError(f"{pair_id}: missing compilation provenance")
    uses_stubs = any(provenance.get("mode") == "STUBBED"
                     for provenance in (row.get("compilations") or {}).values())
    if stub_policy == "forbid" and uses_stubs:
        raise ValueError(f"{pair_id}: no-stub BCB ablation used generated stubs")
    if status == "ok":
        reports_stubbed_mode = mode == "SOURCE_PLUS_STUBBED_WALA_SMT"
        # A source-only fallback may retain successful provenance for the side that compiled
        # before the other side failed. That partial compilation can legitimately be STUBBED even
        # though the final product analysis mode is fallback.
        if mode != "SOURCE_ONLY_FALLBACK" and reports_stubbed_mode != uses_stubs:
            raise ValueError(
                f"{pair_id}: analysisMode and compilation Stub provenance disagree")
        if uses_stubs:
            region_types = {
                str(region.get("type", ""))
                for region in (row.get("report") or {}).get("regions", [])
            }
            forbidden_t4 = region_types & {"T4_CONFIRMED", "T4_DYNAMIC_EVIDENCE"}
            if forbidden_t4:
                raise ValueError(
                    f"{pair_id}: Stub-assisted result emitted forbidden T4 evidence "
                    f"{sorted(forbidden_t4)}")
    for side in ("left", "right"):
        field = f"{side}Sha256"
        actual = str(row.get(field, ""))
        expected = execution[f"{side}_sha256"]
        if status == "ok" and actual != expected:
            raise ValueError(f"{pair_id}: successful result {field} mismatch")
        if status == "error" and actual and actual != expected:
            raise ValueError(f"{pair_id}: error result {field} mismatch")
    if status == "ok":
        if mode not in OK_MODES:
            raise ValueError(f"{pair_id}: invalid successful analysisMode {mode}")
        if not row["stages"]:
            raise ValueError(f"{pair_id}: successful result has empty stages")
        if mode == "SOURCE_ONLY_FALLBACK":
            if not row.get("fallbackStage") or not row.get("fallbackReason"):
                raise ValueError(f"{pair_id}: fallback result lacks stage/reason")
        if mode == "SOURCE_PLUS_WALA_SMT" and (row.get("fallbackStage") or row.get("fallbackReason")):
            raise ValueError(f"{pair_id}: WALA result unexpectedly declares fallback")
    elif mode != "ERROR":
        raise ValueError(f"{pair_id}: error result must use analysisMode=ERROR")


def validate_provenance(executions_path: Path, lock_path: Path, references_path: Path,
                        run_config_path: Path, results_path: Path, attempt_policy: str,
                        stub_policy: str
                        ) -> tuple[dict, dict[str, dict[str, str]], list[dict[str, str]],
                                   dict[str, dict], Counter[str]]:
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    if lock.get("schema_version") != bcb_cleanroom.LOCK_SCHEMA_VERSION:
        raise ValueError("unsupported dataset lock schema")
    if lock.get("generator_dirty_worktree") is not False:
        raise ValueError("dataset was generated from a dirty worktree")
    if lock.get("generator_commit") in {"", "unknown", None}:
        raise ValueError("dataset lock has no generator commit")
    if execution_manifest.sha256_file(executions_path) != lock.get("executions_sha256"):
        raise ValueError("executions.csv differs from dataset lock")
    if execution_manifest.sha256_file(references_path) != lock.get("references_sha256"):
        raise ValueError("references.csv differs from dataset lock")

    execution_rows, dataset_id = execution_manifest.validate_manifest(
        executions_path, lock.get("dataset_id"))
    executions = {row["pair_id"]: row for row in execution_rows}
    references = load_references(references_path, executions_path, dataset_id, executions)
    run_config = json.loads(run_config_path.read_text(encoding="utf-8"))
    required_config = {
        "dataset_id": dataset_id,
        "manifest_sha256": lock["executions_sha256"],
        "dirty_worktree": "false",
        "execution_manifest_schema": execution_manifest.SCHEMA_VERSION,
    }
    for field, expected in required_config.items():
        if str(run_config.get(field, "")) != str(expected):
            raise ValueError(
                f"run_config {field}={run_config.get(field)!r}, expected {expected!r}")
    if run_config.get("code_commit") in {"", "unknown", None}:
        raise ValueError("run_config has no code commit")
    if run_config.get("skip_dynamic") is not True:
        raise ValueError("BCB T1-T3 run must freeze skip_dynamic=true; T4 is a separate benchmark")
    expected_disable_stubs = stub_policy == "forbid"
    if run_config.get("disable_stubs") is not expected_disable_stubs:
        raise ValueError(
            f"BCB stub policy {stub_policy!r} requires "
            f"disable_stubs={expected_disable_stubs}")

    selected, duplicates = select_attempts(strict_jsonl(results_path), attempt_policy)
    unknown = sorted(set(selected) - set(executions))
    if unknown:
        raise ValueError(f"results contain unknown execution {unknown[0]}")
    for pair_id, row in selected.items():
        validate_result(
            row, executions[pair_id], run_config, lock["executions_sha256"], stub_policy)
    return lock, executions, references, selected, duplicates


def unique_execution_audit(executions: dict[str, dict[str, str]],
                           results: dict[str, dict]) -> str:
    modes: Counter[str] = Counter()
    wall: dict[str, list[float]] = defaultdict(list)
    fallbacks: Counter[tuple[str, str]] = Counter()
    for pair_id in executions:
        row = results.get(pair_id)
        mode = "MISSING" if row is None else str(row.get("analysisMode", "UNKNOWN"))
        modes[mode] += 1
        if row is not None and row.get("status") == "ok":
            wall[mode].append(float(row.get("wallMs", 0)))
        if mode == "SOURCE_ONLY_FALLBACK":
            fallbacks[(str(row.get("fallbackStage", "")),
                       str(row.get("fallbackReason", "")))] += 1
    total = len(executions)
    lines = [
        "## Unique file-pair execution provenance (mandatory)", "",
        "Counts below use unique product executions, not duplicated BCB references.", "",
        "| Analysis mode | Executions | Share | Median wall ms |",
        "| --- | ---: | ---: | ---: |",
    ]
    for mode in sorted(modes):
        values = wall[mode]
        lines.append(
            f"| {mode} | {modes[mode]} | {modes[mode] / total:.2%} | "
            f"{statistics.median(values) if values else 0.0:.0f} |")
    if fallbacks:
        lines.extend(["", "### Unique fallback causes", "",
                      "| Stage | Reason | Executions |", "| --- | --- | ---: |"])
        for (stage, reason), count in sorted(fallbacks.items()):
            lines.append(f"| {stage} | {reason} | {count} |")
    return "\n".join(lines) + "\n"


def write_execution_provenance(path: Path, executions: dict[str, dict[str, str]],
                               results: dict[str, dict]) -> None:
    fields = [
        "execution_id", "status", "analysis_mode", "wall_ms", "fallback_stage",
        "fallback_reason", "fallback_detail", "stages_json",
    ]
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        for execution_id in sorted(executions):
            result = results.get(execution_id)
            if result is None:
                writer.writerow({
                    "execution_id": execution_id, "status": "missing_result",
                    "analysis_mode": "MISSING",
                })
                continue
            fallback_stage = str(result.get("fallbackStage", ""))
            stages = result.get("stages") or {}
            fallback_detail = ""
            if fallback_stage and isinstance(stages.get(fallback_stage), dict):
                fallback_detail = str(stages[fallback_stage].get("detail", ""))
            writer.writerow({
                "execution_id": execution_id,
                "status": result.get("status", ""),
                "analysis_mode": result.get("analysisMode", ""),
                "wall_ms": result.get("wallMs", 0),
                "fallback_stage": fallback_stage,
                "fallback_reason": result.get("fallbackReason", ""),
                "fallback_detail": fallback_detail,
                "stages_json": json.dumps(stages, sort_keys=True, separators=(",", ":")),
            })


def write_scores(path: Path, records: list[dict]) -> None:
    fields = ["reference_id", "execution_id", *PAIR_FIELDS[1:]]
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        for record in records:
            row = dict(record)
            for key in ("detected", "type_correct", "any_correct_overlap",
                        "reference_range_fp", "strict_product_fp"):
                row[key] = bool_text(row[key])
            for key in ("coverage_left", "coverage_right", "boundary_precision_left",
                        "boundary_precision_right", "iou_left", "iou_right"):
                row[key] = format_float(row[key])
            writer.writerow({field: row.get(field, "") for field in fields})


def current_commit(root: Path) -> tuple[str, bool]:
    commit = subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True, check=True
    ).stdout.strip()
    dirty = bool(subprocess.run(
        ["git", "status", "--porcelain"], cwd=root, capture_output=True, text=True,
        check=True).stdout.strip())
    return commit, dirty


def run(args: argparse.Namespace) -> list[dict]:
    args.executions = args.executions.resolve()
    args.references = args.references.resolve()
    args.dataset_lock = args.dataset_lock.resolve()
    args.run_config = args.run_config.resolve()
    args.results = args.results.resolve()
    args.machine = args.machine.resolve()
    args.out = args.out.resolve()
    root = Path(__file__).resolve().parents[3]
    scorer_commit, scorer_dirty = current_commit(root)
    if scorer_dirty:
        raise ValueError("clean-room scoring requires a clean worktree")
    if args.out.exists() and any(args.out.iterdir()):
        raise ValueError(f"score output must be new or empty: {args.out}")
    args.out.mkdir(parents=True, exist_ok=True)

    lock, executions, references, results, duplicates = validate_provenance(
        args.executions, args.dataset_lock, args.references, args.run_config,
        args.results, args.attempt_policy, args.stub_policy)
    run_config_identity = json.loads(args.run_config.read_text(encoding="utf-8"))
    # Dataset construction and product execution must be paired at one frozen code commit. The
    # scorer has its own independently hashed/recorded commit so a scoring-only defect can be fixed
    # without spending hours rerunning immutable raw product outputs.
    if lock["generator_commit"] != run_config_identity["code_commit"]:
        raise ValueError("dataset generator and product execution commits differ")
    machine = json.loads(args.machine.read_text(encoding="utf-8"))
    if not machine.get("logical_cpu_count") or not machine.get("memory_kib"):
        raise ValueError("machine metadata lacks CPU or memory identity")
    records = []
    for reference in references:
        label = dict(reference)
        label["pair_id"] = reference["reference_id"]
        scored = score_reference(
            label, results.get(reference["execution_id"]), args.min_region_lines)
        scored["reference_id"] = reference["reference_id"]
        scored["execution_id"] = reference["execution_id"]
        records.append(scored)

    write_scores(args.out / "scored_references.csv", records)
    write_execution_provenance(
        args.out / "execution_provenance.csv", executions, results)
    write_confusion(args.out / "confusion_matrix.csv", records)
    write_per_mode(args.out / "per_analysis_mode.csv", records)
    summary = build_metric_summary(records, args.bootstrap_iterations, args.seed)
    summary += (
        "\n## Frozen execution policy\n\n"
        f"Stub policy: `{args.stub_policy}`. Dynamic execution is disabled for this "
        "BCB T1--T3 experiment. Stub-assisted rows may use WALA region evidence but "
        "cannot emit strict or dynamic T4 evidence.\n")
    summary += "\n" + unique_execution_audit(executions, results)
    summary += (
        "\n## Interpretation boundary\n\n"
        "This is BCB-derived pairwise region recall on complete original IJaDataset files. "
        "It is not an unqualified BigCloneEval directory-level recall result. Negative rows "
        "measure rejection at the official reference ranges; independent product precision "
        "requires a separately annotated output sample.\n")
    if duplicates:
        summary += (f"\nRetry rows observed: {sum(duplicates.values())}; "
                    f"attempt policy: {args.attempt_policy}.\n")
    (args.out / "summary.md").write_text(summary, encoding="utf-8")

    score_lock = {
        "schema_version": "bcb-cleanroom-score-lock-1.0",
        "created_utc": datetime.now(timezone.utc).isoformat(),
        "dataset_id": lock["dataset_id"],
        "scorer_commit": scorer_commit,
        "scorer_dirty_worktree": scorer_dirty,
        "scorer_script_sha256": execution_manifest.sha256_file(Path(__file__).resolve()),
        "executions_sha256": execution_manifest.sha256_file(args.executions),
        "references_sha256": execution_manifest.sha256_file(args.references),
        "dataset_lock_sha256": execution_manifest.sha256_file(args.dataset_lock),
        "run_config_sha256": execution_manifest.sha256_file(args.run_config),
        "results_sha256": execution_manifest.sha256_file(args.results),
        "machine_sha256": execution_manifest.sha256_file(args.machine),
        "machine": machine,
        "attempt_policy": args.attempt_policy,
        "stub_policy": args.stub_policy,
        "detection_policy": "strict",
        "coverage_threshold": C_MATCH_THRESHOLD,
        "bootstrap_iterations": args.bootstrap_iterations,
        "seed": args.seed,
        "reference_count": len(records),
        "execution_count": len(executions),
        "missing_execution_count": len(set(executions) - set(results)),
    }
    (args.out / "score_lock.json").write_text(
        json.dumps(score_lock, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(summary)
    return records


def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--executions", required=True, type=Path)
    parser.add_argument("--references", required=True, type=Path)
    parser.add_argument("--dataset-lock", required=True, type=Path)
    parser.add_argument("--run-config", required=True, type=Path)
    parser.add_argument("--results", required=True, type=Path)
    parser.add_argument("--machine", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--attempt-policy", choices=("first", "last"), default="first")
    parser.add_argument("--stub-policy", choices=("allow", "forbid"), required=True)
    parser.add_argument("--min-region-lines", type=int, default=6)
    parser.add_argument("--bootstrap-iterations", type=int, default=2000)
    parser.add_argument("--seed", type=int, default=20260721)
    return parser.parse_args(argv)


if __name__ == "__main__":
    try:
        run(parse_args())
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        sys.exit(f"[score-cleanroom] ERROR: {error}")
