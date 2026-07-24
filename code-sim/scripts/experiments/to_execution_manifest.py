#!/usr/bin/env python3
"""
Upgrade a simple 3-column manifest (pair_id,left_path,right_path) into the
7-column provenance-locked execution manifest that run_shards.py / BatchPairMain
require: schema_version,dataset_id,pair_id,left_path,right_path,left_sha256,right_sha256.

Computes the SHA-256 of each side's file contents (the value BatchPairMain
re-verifies at run time). Absolute paths are kept as-is.

Usage:
  py scripts/experiments/to_execution_manifest.py \
     --in results/mut_full/manifest.csv \
     --dataset-id mutation-v1 \
     --out results/mut_full/exec_manifest.csv
"""

import argparse
import csv
import hashlib
from pathlib import Path

SCHEMA_VERSION = "execution-1.0"
OUT_FIELDS = ["schema_version", "dataset_id", "pair_id",
              "left_path", "right_path", "left_sha256", "right_sha256"]


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def resolve(manifest: Path, value: str) -> Path:
    p = Path(value)
    return p if p.is_absolute() else (manifest.parent / p).resolve()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="inp", required=True, type=Path)
    ap.add_argument("--dataset-id", required=True)
    ap.add_argument("--out", required=True, type=Path)
    args = ap.parse_args()

    rows = list(csv.DictReader(open(args.inp, encoding="utf-8-sig")))
    if not rows:
        raise SystemExit(f"empty input manifest: {args.inp}")

    written, missing = 0, 0
    with open(args.out, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=OUT_FIELDS)
        w.writeheader()
        for r in rows:
            lp = resolve(args.inp, r["left_path"])
            rp = resolve(args.inp, r["right_path"])
            if not lp.is_file() or not rp.is_file():
                missing += 1
                continue
            w.writerow({
                "schema_version": SCHEMA_VERSION,
                "dataset_id": args.dataset_id,
                "pair_id": r["pair_id"],
                "left_path": str(lp),
                "right_path": str(rp),
                "left_sha256": sha256_file(lp),
                "right_sha256": sha256_file(rp),
            })
            written += 1

    print(f"[exec-manifest] wrote {written} rows ({missing} skipped) -> {args.out}")


if __name__ == "__main__":
    main()
