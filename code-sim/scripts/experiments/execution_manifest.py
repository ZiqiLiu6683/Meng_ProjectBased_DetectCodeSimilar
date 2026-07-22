#!/usr/bin/env python3
"""Validation helpers for label-free, provenance-locked execution manifests."""

from __future__ import annotations

import csv
import hashlib
from pathlib import Path


SCHEMA_VERSION = "execution-1.0"
FIELDS = [
    "schema_version",
    "dataset_id",
    "pair_id",
    "left_path",
    "right_path",
    "left_sha256",
    "right_sha256",
]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames is None:
            raise ValueError(f"CSV has no header: {path}")
        missing = [field for field in FIELDS if field not in reader.fieldnames]
        if missing:
            raise ValueError(f"execution manifest missing fields: {', '.join(missing)}")
        return [
            {key: (value or "").strip() for key, value in row.items()}
            for row in reader
        ]


def resolve_path(manifest: Path, value: str) -> Path:
    path = Path(value)
    return path.resolve() if path.is_absolute() else (manifest.parent / path).resolve()


def validate_manifest(manifest: Path, expected_dataset_id: str | None = None
                      ) -> tuple[list[dict[str, str]], str]:
    manifest = manifest.resolve()
    rows = read_csv(manifest)
    if not rows:
        raise ValueError(f"execution manifest is empty: {manifest}")

    seen: set[str] = set()
    dataset_ids: set[str] = set()
    hash_cache: dict[Path, str] = {}
    for line_number, row in enumerate(rows, start=2):
        pair_id = row["pair_id"]
        if not pair_id:
            raise ValueError(f"line {line_number}: empty pair_id")
        if pair_id in seen:
            raise ValueError(f"line {line_number}: duplicate pair_id {pair_id}")
        seen.add(pair_id)
        if row["schema_version"] != SCHEMA_VERSION:
            raise ValueError(
                f"{pair_id}: unsupported execution schema {row['schema_version']}")
        dataset_id = row["dataset_id"]
        if not dataset_id:
            raise ValueError(f"{pair_id}: empty dataset_id")
        dataset_ids.add(dataset_id)
        if expected_dataset_id and dataset_id != expected_dataset_id:
            raise ValueError(
                f"{pair_id}: dataset_id {dataset_id} differs from {expected_dataset_id}")

        for side in ("left", "right"):
            path = resolve_path(manifest, row[f"{side}_path"])
            if not path.is_file():
                raise FileNotFoundError(f"{pair_id}: {side} source is missing: {path}")
            expected_hash = row[f"{side}_sha256"]
            if len(expected_hash) != 64:
                raise ValueError(f"{pair_id}: invalid {side} SHA-256")
            if path not in hash_cache:
                hash_cache[path] = sha256_file(path)
            actual_hash = hash_cache[path]
            if actual_hash != expected_hash.lower():
                raise ValueError(f"{pair_id}: {side} SHA-256 mismatch")

    if len(dataset_ids) != 1:
        raise ValueError(f"manifest must contain one dataset_id, found {sorted(dataset_ids)}")
    dataset_id = next(iter(dataset_ids))
    print(f"[execution-manifest] valid: {len(rows)} unique file pairs; dataset={dataset_id}")
    return rows, dataset_id
