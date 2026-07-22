#!/usr/bin/env python3
"""Build, sample, and validate portable paper-evaluation manifests.

The legacy BCB manifests contain absolute Windows paths.  This tool rebuilds
paths from pair IDs, writes paths relative to the new manifest, locks the input
files with SHA-256, and validates every row before an expensive run.

Examples:
    python3 scripts/experiments/manifest_v2.py build \
        --pairs-root results/bcb_smoke/pairs \
        --labels results/bcb_smoke/labels.csv \
        --out results/bcb_smoke/manifest_v2.csv \
        --dataset-id bcb-smoke-20260721

    python3 scripts/experiments/manifest_v2.py validate \
        --manifest results/bcb_smoke/manifest_v2.csv
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import os
import sys
from collections import Counter, defaultdict
from pathlib import Path


SCHEMA_VERSION = "2.0"
ALLOWED_TYPES = {"T1", "T2", "T3", "T4", "T4_CONFIRMED", "NON_CLONE"}
T3_BANDS = {"VST3", "ST3", "MT3"}
FIELDS = [
    "schema_version",
    "dataset_id",
    "pair_id",
    "left_path",
    "right_path",
    "left_sha256",
    "right_sha256",
    "expected_type",
    "band",
    "cluster_id",
    "functionality_id",
    "sim_both",
    "bcb_f1",
    "bcb_f2",
    "left_begin",
    "left_end",
    "right_begin",
    "right_end",
]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames is None:
            raise ValueError(f"CSV has no header: {path}")
        return [{key: (value or "").strip() for key, value in row.items()}
                for row in reader]


def stratum(row: dict[str, str]) -> str:
    if row.get("expected_type") == "T3":
        return row.get("band", "")
    if row.get("expected_type") == "NON_CLONE":
        return "NEG"
    return row.get("expected_type", "")


def stable_sample(rows: list[dict[str, str]], fraction: float, seed: int,
                  minimum_per_stratum: int) -> list[dict[str, str]]:
    if not 0 < fraction <= 1:
        raise ValueError("--fraction must be in (0, 1]")
    groups: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        groups[stratum(row)].append(row)

    selected: list[dict[str, str]] = []
    for name in sorted(groups):
        group = groups[name]
        wanted = len(group) if fraction == 1 else max(
            minimum_per_stratum, math.ceil(len(group) * fraction))
        wanted = min(wanted, len(group))
        ranked = sorted(group, key=lambda row: (
            sha256_text(f"{seed}:{name}:{row['pair_id']}"), row["pair_id"]))
        selected.extend(ranked[:wanted])
    return sorted(selected, key=lambda row: (stratum(row), row["pair_id"]))


def locate_pair(pairs_root: Path, row: dict[str, str]) -> tuple[Path, Path]:
    pair_id = row["pair_id"]
    candidates = [pairs_root / pair_id]
    category = stratum(row)
    if category:
        candidates.append(pairs_root / category / pair_id)
    for directory in candidates:
        left = directory / "LeftInput.java"
        right = directory / "RightInput.java"
        if left.is_file() and right.is_file():
            return left.resolve(), right.resolve()
    searched = ", ".join(str(path) for path in candidates)
    raise FileNotFoundError(f"pair files not found for {pair_id}; searched: {searched}")


def wrapped_reference_range(path: Path) -> tuple[str, str]:
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    class_lines = [index for index, line in enumerate(lines, start=1)
                   if line.lstrip().startswith("public class ") and line.rstrip().endswith("{")]
    if len(class_lines) != 1 or len(lines) <= class_lines[0]:
        raise ValueError(f"cannot infer wrapped reference range: {path}")
    begin = class_lines[0] + 1
    end = len(lines) - 1
    if begin > end:
        raise ValueError(f"empty wrapped reference range: {path}")
    return str(begin), str(end)


def portable_path(path: Path, manifest: Path) -> str:
    return Path(os.path.relpath(path, manifest.parent.resolve())).as_posix()


def build(args: argparse.Namespace) -> None:
    labels_path = args.labels.resolve()
    pairs_root = args.pairs_root.resolve()
    out = args.out.resolve()
    labels = read_csv(labels_path)
    if not labels:
        raise ValueError(f"labels CSV is empty: {labels_path}")
    if any(not row.get("pair_id") for row in labels):
        raise ValueError("every label row must have pair_id")
    if len({row["pair_id"] for row in labels}) != len(labels):
        raise ValueError("labels CSV contains duplicate pair_id values")

    selected = stable_sample(labels, args.fraction, args.seed,
                             args.minimum_per_stratum)
    out.parent.mkdir(parents=True, exist_ok=True)
    built: list[dict[str, str]] = []
    for label in selected:
        left, right = locate_pair(pairs_root, label)
        expected = label.get("expected_type", "")
        left_begin, left_end = wrapped_reference_range(left)
        right_begin, right_end = wrapped_reference_range(right)
        built.append({
            "schema_version": SCHEMA_VERSION,
            "dataset_id": args.dataset_id,
            "pair_id": label["pair_id"],
            "left_path": portable_path(left, out),
            "right_path": portable_path(right, out),
            "left_sha256": sha256_file(left),
            "right_sha256": sha256_file(right),
            "expected_type": expected,
            "band": label.get("band", ""),
            "cluster_id": label.get("cluster_id") or label.get("functionality_id", ""),
            "functionality_id": label.get("functionality_id", ""),
            "sim_both": label.get("sim_both", ""),
            "bcb_f1": label.get("bcb_f1", ""),
            "bcb_f2": label.get("bcb_f2", ""),
            "left_begin": left_begin,
            "left_end": left_end,
            "right_begin": right_begin,
            "right_end": right_end,
        })

    with out.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=FIELDS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(built)

    validate_manifest(out, expected_dataset_id=args.dataset_id)
    meta_path = out.with_suffix(out.suffix + ".meta.json")
    metadata = {
        "schema_version": SCHEMA_VERSION,
        "dataset_id": args.dataset_id,
        "seed": args.seed,
        "fraction": args.fraction,
        "minimum_per_stratum": args.minimum_per_stratum,
        "source_labels": portable_path(labels_path, meta_path),
        "source_labels_sha256": sha256_file(labels_path),
        "manifest_sha256": sha256_file(out),
        "row_count": len(built),
        "strata": dict(sorted(Counter(stratum(row) for row in built).items())),
    }
    meta_path.write_text(json.dumps(metadata, indent=2, sort_keys=True) + "\n",
                         encoding="utf-8")
    print(f"[manifest] wrote {len(built)} rows -> {out}")
    print(f"[manifest] sha256={metadata['manifest_sha256']}")
    print(f"[manifest] strata={metadata['strata']}")


def parse_range(row: dict[str, str], prefix: str, line_count: int) -> None:
    begin_text = row.get(f"{prefix}_begin", "")
    end_text = row.get(f"{prefix}_end", "")
    if not begin_text or not end_text:
        raise ValueError(f"{row['pair_id']}: missing {prefix} reference range")
    begin, end = int(begin_text), int(end_text)
    if begin < 1 or end < begin or end > line_count:
        raise ValueError(
            f"{row['pair_id']}: invalid {prefix} range {begin}..{end} for {line_count} lines")


def resolve_manifest_path(manifest: Path, value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else (manifest.parent / path).resolve()


def validate_manifest(manifest: Path, expected_dataset_id: str | None = None) -> None:
    manifest = manifest.resolve()
    rows = read_csv(manifest)
    if not rows:
        raise ValueError(f"manifest is empty: {manifest}")
    missing = [field for field in FIELDS if field not in rows[0]]
    if missing:
        raise ValueError(f"manifest missing fields: {', '.join(missing)}")
    seen: set[str] = set()
    counts: Counter[str] = Counter()
    for index, row in enumerate(rows, start=2):
        pair_id = row["pair_id"]
        if not pair_id:
            raise ValueError(f"line {index}: empty pair_id")
        if pair_id in seen:
            raise ValueError(f"line {index}: duplicate pair_id {pair_id}")
        seen.add(pair_id)
        if row["schema_version"] != SCHEMA_VERSION:
            raise ValueError(f"{pair_id}: unsupported schema {row['schema_version']}")
        if expected_dataset_id and row["dataset_id"] != expected_dataset_id:
            raise ValueError(f"{pair_id}: dataset_id differs from {expected_dataset_id}")
        if row["expected_type"] not in ALLOWED_TYPES:
            raise ValueError(f"{pair_id}: invalid expected_type {row['expected_type']}")
        if row["expected_type"] == "T3" and row["band"] not in T3_BANDS:
            raise ValueError(f"{pair_id}: T3 requires VST3/ST3/MT3 band")
        if row["expected_type"] != "T3" and row["band"]:
            raise ValueError(f"{pair_id}: band is only valid for T3")
        left = resolve_manifest_path(manifest, row["left_path"])
        right = resolve_manifest_path(manifest, row["right_path"])
        for side, path in (("left", left), ("right", right)):
            if not path.is_file():
                raise FileNotFoundError(f"{pair_id}: {side} file missing: {path}")
            actual = sha256_file(path)
            if actual != row[f"{side}_sha256"]:
                raise ValueError(f"{pair_id}: {side} SHA-256 mismatch")
            line_count = len(path.read_text(encoding="utf-8", errors="replace").splitlines())
            parse_range(row, side, line_count)
        counts[stratum(row)] += 1
    print(f"[manifest] valid: {len(rows)} rows; strata={dict(sorted(counts.items()))}")


def validate(args: argparse.Namespace) -> None:
    validate_manifest(args.manifest, args.dataset_id)
    print(f"[manifest] sha256={sha256_file(args.manifest.resolve())}")


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser()
    commands = root.add_subparsers(dest="command", required=True)

    build_parser = commands.add_parser("build")
    build_parser.add_argument("--pairs-root", required=True, type=Path)
    build_parser.add_argument("--labels", required=True, type=Path)
    build_parser.add_argument("--out", required=True, type=Path)
    build_parser.add_argument("--dataset-id", required=True)
    build_parser.add_argument("--fraction", type=float, default=1.0)
    build_parser.add_argument("--seed", type=int, default=20260721)
    build_parser.add_argument("--minimum-per-stratum", type=int, default=1)
    build_parser.set_defaults(handler=build)

    validate_parser = commands.add_parser("validate")
    validate_parser.add_argument("--manifest", required=True, type=Path)
    validate_parser.add_argument("--dataset-id")
    validate_parser.set_defaults(handler=validate)
    return root


def main() -> None:
    args = parser().parse_args()
    try:
        args.handler(args)
    except (OSError, ValueError) as error:
        sys.exit(f"[manifest] ERROR: {error}")


if __name__ == "__main__":
    main()
