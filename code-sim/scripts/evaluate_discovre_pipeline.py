#!/usr/bin/env python3
import argparse
import csv
import re
import shutil
import subprocess
import time
from pathlib import Path


VIEWS = ["DISCOVRE_NUMERIC", "RAW_HASH_BUCKET", "HYBRID_NUMERIC_HASH"]


def parse_args():
    parser = argparse.ArgumentParser(description="Evaluate WALA/discovRE pipeline on labeled pairs.")
    parser.add_argument("--pairs", default="evaluation/pairs.csv")
    parser.add_argument("--output", default="evaluation/results/discovre_evaluation_results.csv")
    parser.add_argument("--work-dir", default="target/discovre-eval")
    parser.add_argument("--top-k", type=int, default=8)
    parser.add_argument("--method-top-k", type=int, default=2)
    parser.add_argument("--limit", type=int, default=0)
    return parser.parse_args()


def compile_source(source, output_dir):
    if output_dir.exists():
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    compile_input = prepare_compile_source(source, output_dir)
    result = subprocess.run(
        ["javac", "-g", "-d", str(output_dir), str(compile_input)],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip())


def prepare_compile_source(source, output_dir):
    text = source.read_text(encoding="utf-8")
    match = re.search(r"\bpublic\s+class\s+([A-Za-z_][A-Za-z0-9_]*)", text)
    if not match:
        return source
    source_dir = output_dir / "__src"
    source_dir.mkdir(parents=True, exist_ok=True)
    renamed = source_dir / f"{match.group(1)}.java"
    renamed.write_text(text, encoding="utf-8")
    return renamed


def run_comparison(left_dir, right_dir, output_csv, view, top_k, method_top_k):
    start = time.perf_counter()
    result = subprocess.run(
        [
            "mvn",
            "-q",
            "-Psemantic-analysis",
            "exec:java",
            "-Dexec.mainClass=com.ziqi.codesim.semantic.backend.wala.raw.WalaDiscovreComparisonMain",
            f"-Dexec.args={left_dir} {right_dir} {output_csv} {view} {top_k} {method_top_k}",
        ],
        capture_output=True,
        text=True,
        check=False,
    )
    runtime_ms = round((time.perf_counter() - start) * 1000)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip())
    return runtime_ms


def summarize_report(path):
    with path.open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    if not rows:
        return {
            "method_count": 0,
            "avg_exhaustive_similarity": 0.0,
            "avg_constrained_similarity": 0.0,
            "avg_similarity_delta": 0.0,
            "avg_candidate_reduction": 0.0,
            "max_exhaustive_similarity": 0.0,
            "max_constrained_similarity": 0.0,
            "max_non_constructor_constrained_similarity": 0.0,
            "best_left_method": "",
            "best_right_method": "",
            "best_non_constructor_left_method": "",
            "best_non_constructor_right_method": "",
            "total_exhaustive_candidate_pairs": 0,
            "total_constrained_candidate_pairs": 0,
        }
    best = max(rows, key=lambda row: float(row["constrainedSimilarity"]))
    non_constructor_rows = [
        row for row in rows
        if not is_constructor(row.get("leftMethodSignature", ""))
        and not is_constructor(row.get("rightMethodSignature", ""))
    ]
    best_non_constructor = max(
        non_constructor_rows,
        key=lambda row: float(row["constrainedSimilarity"]),
        default=None,
    )
    return {
        "method_count": len(rows),
        "avg_exhaustive_similarity": avg(float(row["exhaustiveSimilarity"]) for row in rows),
        "avg_constrained_similarity": avg(float(row["constrainedSimilarity"]) for row in rows),
        "avg_similarity_delta": avg(float(row["similarityDelta"]) for row in rows),
        "max_exhaustive_similarity": max(float(row["exhaustiveSimilarity"]) for row in rows),
        "max_constrained_similarity": float(best["constrainedSimilarity"]),
        "max_non_constructor_constrained_similarity": (
            float(best_non_constructor["constrainedSimilarity"]) if best_non_constructor else 0.0
        ),
        "best_left_method": best.get("leftMethodSignature", ""),
        "best_right_method": best.get("rightMethodSignature", ""),
        "best_non_constructor_left_method": (
            best_non_constructor.get("leftMethodSignature", "") if best_non_constructor else ""
        ),
        "best_non_constructor_right_method": (
            best_non_constructor.get("rightMethodSignature", "") if best_non_constructor else ""
        ),
        "avg_candidate_reduction": avg(float(row["candidateReduction"]) for row in rows),
        "total_exhaustive_candidate_pairs": sum(int(row["exhaustiveCandidatePairs"]) for row in rows),
        "total_constrained_candidate_pairs": sum(int(row["constrainedCandidatePairs"]) for row in rows),
    }


def avg(values):
    values = list(values)
    return sum(values) / len(values) if values else 0.0


def is_constructor(signature):
    return ".<init>(" in signature or ".<clinit>(" in signature


def main():
    args = parse_args()
    root = Path.cwd()
    pairs_path = root / args.pairs
    output_path = root / args.output
    work_dir = root / args.work_dir
    output_path.parent.mkdir(parents=True, exist_ok=True)
    work_dir.mkdir(parents=True, exist_ok=True)

    with pairs_path.open(newline="", encoding="utf-8") as handle:
        pairs = list(csv.DictReader(handle))
    if args.limit:
        pairs = pairs[:args.limit]

    fieldnames = [
        "project",
        "pair_id",
        "expected_type",
        "expected_scope",
        "view",
        "top_k",
        "method_count",
        "avg_exhaustive_similarity",
        "avg_constrained_similarity",
        "avg_similarity_delta",
        "max_exhaustive_similarity",
        "max_constrained_similarity",
        "max_non_constructor_constrained_similarity",
        "best_left_method",
        "best_right_method",
        "best_non_constructor_left_method",
        "best_non_constructor_right_method",
        "avg_candidate_reduction",
        "total_exhaustive_candidate_pairs",
        "total_constrained_candidate_pairs",
        "runtime_ms",
        "status",
        "error",
        "transformation_tag",
        "difficulty",
        "limitation_tag",
        "notes",
    ]

    with output_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for index, row in enumerate(pairs):
            pair_dir = work_dir / row["pair_id"]
            left_dir = pair_dir / "left"
            right_dir = pair_dir / "right"
            try:
                compile_source(root / "evaluation" / row["file_a"], left_dir)
                compile_source(root / "evaluation" / row["file_b"], right_dir)
                for view in VIEWS:
                    report_path = pair_dir / f"{view}.csv"
                    runtime_ms = run_comparison(left_dir, right_dir, report_path, view, args.top_k, args.method_top_k)
                    summary = summarize_report(report_path)
                    writer.writerow(base_output_row(row, view, args.top_k, runtime_ms, "success", "", summary))
            except Exception as exc:
                for view in VIEWS:
                    writer.writerow(base_output_row(row, view, args.top_k, 0, "failed", str(exc), {}))
            handle.flush()
            print(f"[{index + 1}/{len(pairs)}] {row['pair_id']}")
    print(f"Wrote discovRE evaluation results to {output_path}")


def base_output_row(row, view, top_k, runtime_ms, status, error, summary):
    return {
        "project": row["project"],
        "pair_id": row["pair_id"],
        "expected_type": row["expected_type"],
        "expected_scope": row["expected_scope"],
        "view": view,
        "top_k": top_k,
        "method_count": summary.get("method_count", 0),
        "avg_exhaustive_similarity": summary.get("avg_exhaustive_similarity", 0.0),
        "avg_constrained_similarity": summary.get("avg_constrained_similarity", 0.0),
        "avg_similarity_delta": summary.get("avg_similarity_delta", 0.0),
        "max_exhaustive_similarity": summary.get("max_exhaustive_similarity", 0.0),
        "max_constrained_similarity": summary.get("max_constrained_similarity", 0.0),
        "max_non_constructor_constrained_similarity": summary.get("max_non_constructor_constrained_similarity", 0.0),
        "best_left_method": summary.get("best_left_method", ""),
        "best_right_method": summary.get("best_right_method", ""),
        "best_non_constructor_left_method": summary.get("best_non_constructor_left_method", ""),
        "best_non_constructor_right_method": summary.get("best_non_constructor_right_method", ""),
        "avg_candidate_reduction": summary.get("avg_candidate_reduction", 0.0),
        "total_exhaustive_candidate_pairs": summary.get("total_exhaustive_candidate_pairs", 0),
        "total_constrained_candidate_pairs": summary.get("total_constrained_candidate_pairs", 0),
        "runtime_ms": runtime_ms,
        "status": status,
        "error": error,
        "transformation_tag": row["transformation_tag"],
        "difficulty": row["difficulty"],
        "limitation_tag": row["limitation_tag"],
        "notes": row["notes"],
    }


if __name__ == "__main__":
    main()
