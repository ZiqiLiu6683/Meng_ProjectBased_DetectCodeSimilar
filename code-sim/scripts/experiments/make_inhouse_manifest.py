#!/usr/bin/env python3
"""
Builds BatchPairMain manifests from the in-house labeled corpora
(evaluation/pairs.csv and evaluation/long_pairs.csv).

Outputs, per corpus:
    <out>/manifest.csv   pair_id,left_path,right_path        (absolute paths, no commas)
    <out>/labels.csv     pair_id,expected_type,expected_scope,transformation_tag,
                         difficulty,limitation_tag           (join key for scoring)

Usage (from code-sim/):
    python3 scripts/experiments/make_inhouse_manifest.py \
        --pairs evaluation/pairs.csv --out results/experiments/inhouse55
    python3 scripts/experiments/make_inhouse_manifest.py \
        --pairs evaluation/long_pairs.csv --out results/experiments/long36
"""

import argparse
import csv
import sys
from pathlib import Path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pairs", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    args = ap.parse_args()

    eval_root = args.pairs.resolve().parent
    args.out.mkdir(parents=True, exist_ok=True)

    rows = list(csv.DictReader(open(args.pairs, encoding="utf-8")))
    if not rows:
        sys.exit(f"no rows in {args.pairs}")

    kept, missing = 0, 0
    with open(args.out / "manifest.csv", "w", newline="", encoding="utf-8") as mf, \
         open(args.out / "labels.csv", "w", newline="", encoding="utf-8") as lf:
        mw = csv.writer(mf)
        lw = csv.writer(lf)
        mw.writerow(["pair_id", "left_path", "right_path"])
        lw.writerow(["pair_id", "expected_type", "expected_scope",
                     "transformation_tag", "difficulty", "limitation_tag"])
        for r in rows:
            a = (eval_root / r["file_a"]).resolve()
            b = (eval_root / r["file_b"]).resolve()
            if not a.is_file() or not b.is_file():
                print(f"[manifest] MISSING sample, skipped: {r['pair_id']} ({a} / {b})")
                missing += 1
                continue
            if "," in str(a) or "," in str(b):
                sys.exit(f"path contains a comma, unsupported by manifest: {a} {b}")
            mw.writerow([r["pair_id"], str(a), str(b)])
            lw.writerow([r["pair_id"], r.get("expected_type", ""), r.get("expected_scope", ""),
                         r.get("transformation_tag", ""), r.get("difficulty", ""),
                         r.get("limitation_tag", "")])
            kept += 1

    print(f"[manifest] {kept} pairs written, {missing} skipped -> {args.out}")


if __name__ == "__main__":
    main()
