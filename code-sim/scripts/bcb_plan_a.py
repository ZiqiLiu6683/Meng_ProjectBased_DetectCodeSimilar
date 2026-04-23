#!/usr/bin/env python3
"""
BCB Plan-A sampler
Picks clone pairs (same functionality) and non-clone pairs (different
functionalities) from BigCloneBench's sample/ folders, runs AstMain on
each pair, and prints a CSV-style result table.

Usage:
    python3 scripts/bcb_plan_a.py \
        --bcb  /path/to/era_bcb_sample \
        --tool /path/to/code-sim          # pom.xml directory
        --out  results/bcb_plan_a.csv
"""

import argparse
import csv
import os
import random
import re
import subprocess
import sys
from pathlib import Path

# ── configuration ────────────────────────────────────────────────────────────
CLONE_PAIRS_PER_FUNC   = 3   # how many clone pairs to pick per functionality
NON_CLONE_PAIRS        = 10  # total non-clone pairs (across random func pairs)
RANDOM_SEED            = 42
MAIN_CLASS             = "com.ziqi.codesim.ast.AstMain"
# ─────────────────────────────────────────────────────────────────────────────


def list_java_files(folder: Path):
    return sorted(folder.glob("*.java"))


def run_tool(tool_dir: Path, file_a: Path, file_b: Path):
    """Run AstMain and parse the Summary block from stdout."""
    cmd = [
        "mvn", "-q", "exec:java",
        f"-Dexec.mainClass={MAIN_CLASS}",
        f"-Dexec.args={file_a} {file_b}",
    ]
    try:
        result = subprocess.run(
            cmd, cwd=tool_dir,
            capture_output=True, text=True, timeout=120
        )
        return parse_output(result.stdout)
    except subprocess.TimeoutExpired:
        return None
    except Exception as e:
        print(f"  [error] {e}", file=sys.stderr)
        return None


def parse_output(stdout: str):
    """Extract S1-S5 and Combined from AstMain output."""
    scores = {}
    patterns = {
        "S1": r"S1 AST-token Winnowing.*?:\s*([\d.]+)%",
        "S2": r"S2 Exact Subtree.*?:\s*([\d.]+)%",
        "S3": r"S3 Method-level.*?:\s*([\d.]+)%",
        "S4": r"S4 Method-level TED.*?:\s*([\d.]+)%",
        "S5": r"S5 API Call.*?:\s*([\d.]+|N/A[^%\n]*)",
        "Combined": r"Combined Score.*?:\s*([\d.]+)%",
    }
    for key, pat in patterns.items():
        m = re.search(pat, stdout)
        if m:
            raw = m.group(1).strip()
            try:
                scores[key] = float(raw)
            except ValueError:
                scores[key] = None   # N/A
        else:
            scores[key] = None
    return scores


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--bcb",  required=True, help="Path to era_bcb_sample/")
    parser.add_argument("--tool", required=True, help="Path to code-sim/ (contains pom.xml)")
    parser.add_argument("--out",  default="bcb_plan_a.csv", help="Output CSV path")
    args = parser.parse_args()

    bcb_root  = Path(args.bcb).expanduser().resolve()
    tool_dir  = Path(args.tool).expanduser().resolve()
    out_path  = Path(args.out).expanduser()
    out_path.parent.mkdir(parents=True, exist_ok=True)

    random.seed(RANDOM_SEED)

    # Collect all functionality folders that have a non-empty sample/ dir
    func_dirs = sorted(
        [d for d in bcb_root.iterdir()
         if d.is_dir() and list_java_files(d / "sample")],
        key=lambda d: int(d.name)
    )
    print(f"Found {len(func_dirs)} functionality folders with sample files.")

    pairs = []   # list of (file_a, file_b, label, func_a, func_b)

    # ── Clone pairs (same functionality) ─────────────────────────────────────
    for fd in func_dirs:
        files = list_java_files(fd / "sample")
        if len(files) < 2:
            continue
        sample = random.sample(files, min(len(files), CLONE_PAIRS_PER_FUNC + 2))
        count = 0
        for i in range(len(sample)):
            for j in range(i + 1, len(sample)):
                if count >= CLONE_PAIRS_PER_FUNC:
                    break
                pairs.append((sample[i], sample[j], "clone", fd.name, fd.name))
                count += 1

    # ── Non-clone pairs (different functionalities) ───────────────────────────
    func_list = [fd for fd in func_dirs if list_java_files(fd / "sample")]
    nc = 0
    attempts = 0
    while nc < NON_CLONE_PAIRS and attempts < 200:
        fa, fb = random.sample(func_list, 2)
        files_a = list_java_files(fa / "sample")
        files_b = list_java_files(fb / "sample")
        if files_a and files_b:
            pairs.append((
                random.choice(files_a),
                random.choice(files_b),
                "non-clone", fa.name, fb.name
            ))
            nc += 1
        attempts += 1

    print(f"Total pairs to evaluate: {len(pairs)} "
          f"({len(pairs) - NON_CLONE_PAIRS} clone, {NON_CLONE_PAIRS} non-clone)\n")

    # ── Run tool on each pair ─────────────────────────────────────────────────
    fieldnames = ["label", "func_a", "func_b", "file_a", "file_b",
                  "S1", "S2", "S3", "S4", "S5", "Combined"]
    rows = []

    for idx, (fa, fb, label, fna, fnb) in enumerate(pairs, 1):
        print(f"[{idx:3}/{len(pairs)}] {label:9} func{fna} vs func{fnb}  "
              f"{fa.name} vs {fb.name} ...", end=" ", flush=True)
        scores = run_tool(tool_dir, fa, fb)
        if scores is None:
            print("FAILED")
            continue
        row = {
            "label":    label,
            "func_a":   fna,
            "func_b":   fnb,
            "file_a":   fa.name,
            "file_b":   fb.name,
            **{k: (f"{v:.2f}" if v is not None else "N/A")
               for k, v in scores.items()},
        }
        rows.append(row)
        comb = scores.get("Combined")
        print(f"Combined={comb:.2f}%" if comb is not None else "Combined=N/A")

    # ── Write CSV ─────────────────────────────────────────────────────────────
    with open(out_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    print(f"\nResults saved to: {out_path}")

    # ── Quick summary ─────────────────────────────────────────────────────────
    clone_scores = [float(r["Combined"]) for r in rows
                    if r["label"] == "clone" and r["Combined"] != "N/A"]
    nc_scores    = [float(r["Combined"]) for r in rows
                    if r["label"] == "non-clone" and r["Combined"] != "N/A"]

    if clone_scores:
        print(f"\nClone pairs    — avg Combined: {sum(clone_scores)/len(clone_scores):.2f}%"
              f"  min: {min(clone_scores):.2f}%  max: {max(clone_scores):.2f}%")
    if nc_scores:
        print(f"Non-clone pairs — avg Combined: {sum(nc_scores)/len(nc_scores):.2f}%"
              f"  min: {min(nc_scores):.2f}%  max: {max(nc_scores):.2f}%")


if __name__ == "__main__":
    main()
