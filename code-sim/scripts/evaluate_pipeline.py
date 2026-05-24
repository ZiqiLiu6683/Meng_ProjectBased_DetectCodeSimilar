#!/usr/bin/env python3
"""Batch evaluation runner for the staged code similarity pipeline."""

import argparse
import csv
import json
import subprocess
import time
from pathlib import Path


def parse_args():
    parser = argparse.ArgumentParser(description="Evaluate PipelineMain on labeled pairs.")
    parser.add_argument(
        "--pairs",
        default="evaluation/pairs.csv",
        help="CSV file with labeled evaluation pairs, relative to code-sim.",
    )
    parser.add_argument(
        "--output",
        default="evaluation/results/evaluation_results.csv",
        help="Output CSV path, relative to code-sim.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Optional maximum number of rows to evaluate.",
    )
    return parser.parse_args()


def run_pair(file_a, file_b):
    start = time.perf_counter()
    cmd = [
        "mvn",
        "-q",
        "exec:java",
        "-Dexec.mainClass=com.ziqi.codesim.pipeline.PipelineMain",
        f"-Dexec.args=--json {file_a} {file_b}",
    ]
    completed = subprocess.run(
        cmd,
        check=False,
        capture_output=True,
        text=True,
    )
    runtime_ms = round((time.perf_counter() - start) * 1000)
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip())
    return json.loads(completed.stdout), runtime_ms


def warning_list(result):
    warnings = result.get("stage4", {}).get("warnings", [])
    stage0_flags = result.get("stage0", {}).get("flags", [])
    stage3_flags = result.get("stage3", {}).get("flags", [])
    return "|".join(str(item) for item in warnings + stage0_flags + stage3_flags)


def main():
    args = parse_args()
    root = Path.cwd()
    pairs_path = root / args.pairs
    output_path = root / args.output
    output_path.parent.mkdir(parents=True, exist_ok=True)

    rows = []
    with pairs_path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for idx, row in enumerate(reader):
            if args.limit and idx >= args.limit:
                break
            rows.append(row)

    fieldnames = [
        "project",
        "pair_id",
        "language",
        "expected_type",
        "predicted_type",
        "type_pass",
        "expected_scope",
        "predicted_scope",
        "scope_pass",
        "transformation_tag",
        "difficulty",
        "confidence",
        "runtime_ms",
        "method_count_a",
        "method_count_b",
        "pair_matrix_size",
        "stage0_mode",
        "warnings",
        "limitation_tag",
        "notes",
    ]

    with output_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()

        for row in rows:
            file_a = str(Path("evaluation") / row["file_a"])
            file_b = str(Path("evaluation") / row["file_b"])
            result, runtime_ms = run_pair(file_a, file_b)

            predicted_type = result["summary"]["cloneType"]
            predicted_scope = result["summary"]["scopeType"]
            method_count_a = result["stage0"]["methodCountA"]
            method_count_b = result["stage0"]["methodCountB"]

            writer.writerow({
                "project": row["project"],
                "pair_id": row["pair_id"],
                "language": row["language"],
                "expected_type": row["expected_type"],
                "predicted_type": predicted_type,
                "type_pass": predicted_type == row["expected_type"],
                "expected_scope": row["expected_scope"],
                "predicted_scope": predicted_scope,
                "scope_pass": predicted_scope == row["expected_scope"],
                "transformation_tag": row["transformation_tag"],
                "difficulty": row["difficulty"],
                "confidence": result["summary"]["confidence"],
                "runtime_ms": runtime_ms,
                "method_count_a": method_count_a,
                "method_count_b": method_count_b,
                "pair_matrix_size": method_count_a * method_count_b,
                "stage0_mode": result["summary"]["mode"],
                "warnings": warning_list(result),
                "limitation_tag": row["limitation_tag"],
                "notes": row["notes"],
            })

    print(f"Wrote {len(rows)} evaluation rows to {output_path}")


if __name__ == "__main__":
    main()
