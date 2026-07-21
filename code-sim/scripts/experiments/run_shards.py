#!/usr/bin/env python3
"""
Parallel shard runner for BatchPairMain.

Splits a manifest CSV into N shards and runs one BatchPairMain JVM per shard,
then merges the per-shard JSON-lines outputs. Resume-safe: re-running skips
pairs already present in shard outputs (BatchPairMain handles this per shard,
and shard assignment is deterministic).

Prerequisites (run once, from code-sim/):
    mvn -Psemantic-analysis -DskipTests compile
    mvn -Psemantic-analysis dependency:build-classpath -Dmdep.outputFile=target/cp.txt

Usage:
    python3 scripts/experiments/run_shards.py \
        --manifest results/bcb/manifest.csv \
        --out      results/bcb/run \
        --shards   8 \
        [--java java] [--xmx 1g] [--limit N]

Output:
    <out>/shard_00.csv ... shard inputs (deterministic split)
    <out>/shard_00.jsonl ... per-shard results
    <out>/merged.jsonl   ... concatenation of all shards (written at the end)
"""

import argparse
import os
import subprocess
import sys
from pathlib import Path

MAIN_CLASS = "com.ziqi.codesim.next.semantic.eval.BatchPairMain"


def code_sim_root() -> Path:
    # scripts/experiments/run_shards.py -> code-sim/
    return Path(__file__).resolve().parent.parent.parent


def build_classpath(root: Path) -> str:
    classes = root / "target" / "classes"
    cp_file = root / "target" / "cp.txt"
    if not classes.is_dir():
        sys.exit("target/classes missing - run: mvn -Psemantic-analysis -DskipTests compile")
    if not cp_file.is_file():
        sys.exit("target/cp.txt missing - run: mvn -Psemantic-analysis "
                 "dependency:build-classpath -Dmdep.outputFile=target/cp.txt")
    raw = cp_file.read_bytes()
    if raw.startswith(b"\xff\xfe") or raw.startswith(b"\xfe\xff"):
        deps = raw.decode("utf-16").strip()          # PowerShell-written files
    else:
        deps = raw.decode("utf-8-sig").strip()        # tolerates a UTF-8 BOM
    deps = deps.replace("\x00", "")
    sep = ";" if os.name == "nt" else ":"
    return f"{classes}{sep}{deps}"


def split_manifest(manifest: Path, out_dir: Path, shards: int) -> list[Path]:
    lines = manifest.read_text(encoding="utf-8").splitlines()
    header, rows = lines[0], [l for l in lines[1:] if l.strip()]
    shard_paths = []
    for s in range(shards):
        shard_rows = rows[s::shards]  # deterministic round-robin
        p = out_dir / f"shard_{s:02d}.csv"
        p.write_text("\n".join([header] + shard_rows) + "\n", encoding="utf-8")
        shard_paths.append(p)
        print(f"[shards] {p.name}: {len(shard_rows)} pairs")
    return shard_paths


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--shards", type=int, default=8)
    ap.add_argument("--java", default="java")
    ap.add_argument("--xmx", default="1g")
    ap.add_argument("--limit", type=int, default=0, help="per-shard pair limit (smoke tests)")
    ap.add_argument("--java-opt", action="append", default=[],
                    help="extra JVM option, repeatable (e.g. --java-opt -Dcodesim.skipDynamic=true)")
    args = ap.parse_args()

    root = code_sim_root()
    cp = build_classpath(root)
    args.out.mkdir(parents=True, exist_ok=True)
    shard_inputs = split_manifest(args.manifest, args.out, args.shards)

    procs = []
    for p in shard_inputs:
        out_jsonl = p.with_suffix(".jsonl")
        cmd = [args.java, f"-Xmx{args.xmx}", *args.java_opt, "-cp", cp, MAIN_CLASS, str(p), str(out_jsonl)]
        if args.limit > 0:
            cmd += ["--limit", str(args.limit)]
        log = open(p.with_suffix(".log"), "a", encoding="utf-8")
        procs.append((p.name, subprocess.Popen(cmd, stdout=log, stderr=log, cwd=root), log))
        print(f"[shards] launched {p.name} (pid {procs[-1][1].pid})")

    failed = 0
    for name, proc, log in procs:
        rc = proc.wait()
        log.close()
        print(f"[shards] {name} exited rc={rc}")
        failed += 1 if rc != 0 else 0

    import shutil
    merged = args.out / "merged.jsonl"
    with open(merged, "w", encoding="utf-8") as m:
        for p in shard_inputs:
            j = p.with_suffix(".jsonl")
            if j.is_file():
                with open(j, encoding="utf-8") as f:
                    shutil.copyfileobj(f, m, 1 << 20)
    print(f"[shards] merged -> {merged}")
    if failed:
        sys.exit(f"[shards] {failed} shard(s) failed - check the .log files, then re-run "
                 "(resume will skip completed pairs)")


if __name__ == "__main__":
    main()
