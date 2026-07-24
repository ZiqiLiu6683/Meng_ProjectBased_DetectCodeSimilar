#!/usr/bin/env python3
"""Validated, provenance-locked, label-free shard runner for BatchPairMain."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import platform
import re
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))
import execution_manifest


MAIN_CLASS = "com.ziqi.codesim.next.semantic.eval.BatchPairMain"


def code_sim_root() -> Path:
    return Path(__file__).resolve().parent.parent.parent


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def command_output(command: list[str], cwd: Path) -> str:
    completed = subprocess.run(command, cwd=cwd, capture_output=True, text=True)
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise RuntimeError(f"command failed ({' '.join(command)}): {detail}")
    return completed.stdout.strip()


def java_version(java: str, cwd: Path) -> tuple[int, str]:
    completed = subprocess.run([java, "-version"], cwd=cwd, capture_output=True, text=True)
    output = (completed.stderr or completed.stdout).strip()
    if completed.returncode != 0:
        raise RuntimeError(f"cannot run Java: {output}")
    match = re.search(r'version "([0-9]+)(?:\.([0-9]+))?', output)
    if not match:
        raise RuntimeError(f"cannot parse Java version: {output.splitlines()[0] if output else ''}")
    first = int(match.group(1))
    major = int(match.group(2)) if first == 1 and match.group(2) else first
    return major, output.splitlines()[0]


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
        deps = raw.decode("utf-16").strip()
    else:
        deps = raw.decode("utf-8-sig").strip()
    deps = deps.replace("\x00", "")
    separator = ";" if os.name == "nt" else ":"
    return f"{classes}{separator}{deps}"


def split_manifest(manifest: Path, out_dir: Path, shards: int) -> list[Path]:
    manifest = manifest.resolve()
    out_dir = out_dir.resolve()
    with manifest.open(newline="", encoding="utf-8-sig") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames is None:
            raise ValueError("manifest has no header")
        fields = reader.fieldnames
        rows = list(reader)
    for row in rows:
        for field in ("left_path", "right_path"):
            source = Path(row[field])
            absolute = source if source.is_absolute() else (manifest.parent / source).resolve()
            row[field] = Path(os.path.relpath(absolute, out_dir)).as_posix()
        for field in ("left_project_root", "right_project_root"):
            if row.get(field):
                project = Path(row[field])
                absolute = project if project.is_absolute() else (manifest.parent / project).resolve()
                row[field] = Path(os.path.relpath(absolute, out_dir)).as_posix()
        for field in ("left_classpath", "right_classpath"):
            if row.get(field):
                rebased = []
                for value in row[field].split(os.pathsep):
                    entry = Path(value)
                    absolute = entry if entry.is_absolute() else (manifest.parent / entry).resolve()
                    rebased.append(Path(os.path.relpath(absolute, out_dir)).as_posix())
                row[field] = os.pathsep.join(rebased)
    shard_paths: list[Path] = []
    for shard_index in range(shards):
        shard_rows = rows[shard_index::shards]
        path = out_dir / f"shard_{shard_index:03d}.csv"
        with path.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
            writer.writeheader()
            writer.writerows(shard_rows)
        shard_paths.append(path)
        print(f"[shards] {path.name}: {len(shard_rows)} pairs")
    return shard_paths


def read_dataset_id(manifest: Path) -> str:
    rows = execution_manifest.read_csv(manifest)
    dataset_ids = {row["dataset_id"] for row in rows}
    if len(dataset_ids) != 1 or not next(iter(dataset_ids)):
        raise ValueError(f"manifest must contain exactly one non-empty dataset_id: {dataset_ids}")
    return next(iter(dataset_ids))


def machine_metadata() -> dict[str, object]:
    memory_kib = None
    cpu_model = None
    meminfo = Path("/proc/meminfo")
    if meminfo.is_file():
        for line in meminfo.read_text(encoding="utf-8").splitlines():
            if line.startswith("MemTotal:"):
                memory_kib = int(line.split()[1])
                break
    cpuinfo = Path("/proc/cpuinfo")
    if cpuinfo.is_file():
        for line in cpuinfo.read_text(encoding="utf-8", errors="replace").splitlines():
            if line.startswith("model name") and ":" in line:
                cpu_model = line.split(":", 1)[1].strip()
                break
    os_release = {}
    release_path = Path("/etc/os-release")
    if release_path.is_file():
        for line in release_path.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.startswith("#"):
                key, value = line.split("=", 1)
                os_release[key] = value.strip().strip('"')
    product_name_path = Path("/sys/devices/virtual/dmi/id/product_name")
    return {
        "platform": platform.platform(),
        "machine": platform.machine(),
        "logical_cpu_count": os.cpu_count(),
        "cpu_model": cpu_model,
        "memory_kib": memory_kib,
        "os_release": os_release,
        "dmi_product_name": product_name_path.read_text(encoding="utf-8").strip()
        if product_name_path.is_file() else None,
    }


def frozen_config(args: argparse.Namespace, root: Path, dataset_id: str) -> dict[str, object]:
    commit = command_output(["git", "rev-parse", "HEAD"], root)
    dirty = "true" if command_output(["git", "status", "--porcelain"], root) else "false"
    major, version_text = java_version(args.java, root)
    if major != 17:
        raise ValueError(f"exactly Java 17 is required; {args.java} reports: {version_text}")
    return {
        "schema_version": "1.0",
        "execution_manifest_schema": execution_manifest.SCHEMA_VERSION,
        "manifest_sha256": sha256_file(args.manifest),
        "dataset_id": dataset_id,
        "config_id": args.config_id,
        "environment_id": args.environment_id,
        "code_commit": commit,
        "dirty_worktree": dirty,
        "shards": args.shards,
        "workers": args.workers,
        "xmx": args.xmx,
        "java": args.java,
        "java_version": version_text,
        "java_options": args.java_opt,
        "max_attempts": args.max_attempts,
        "limit_per_shard": args.limit,
        "skip_dynamic": args.skip_dynamic,
        "disable_stubs": args.disable_stubs,
    }


def lock_config(path: Path, config: dict[str, object]) -> None:
    if path.is_file():
        previous = json.loads(path.read_text(encoding="utf-8"))
        if previous != config:
            raise ValueError(
                f"run configuration differs from existing {path}; choose a new output directory")
        return
    path.write_text(json.dumps(config, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def append_history(path: Path, event: str, details: dict[str, object]) -> None:
    row = {
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "event": event,
        **details,
    }
    with path.open("a", encoding="utf-8") as stream:
        stream.write(json.dumps(row, sort_keys=True) + "\n")


def run_shard(path: Path, args: argparse.Namespace, root: Path, classpath: str,
              config: dict[str, object]) -> tuple[str, int]:
    out_jsonl = path.with_suffix(".jsonl")
    command = [
        args.java,
        f"-Xmx{args.xmx}",
        f"-Dcodesim.configId={config['config_id']}",
        f"-Dcodesim.datasetId={config['dataset_id']}",
        f"-Dcodesim.codeCommit={config['code_commit']}",
        f"-Dcodesim.dirtyWorktree={config['dirty_worktree']}",
        f"-Dcodesim.manifestSha256={config['manifest_sha256']}",
    ]
    if args.skip_dynamic:
        command.append("-Dcodesim.skipDynamic=true")
    if args.disable_stubs:
        command.append("-Dcodesim.disableStubs=true")
    command.extend(args.java_opt)
    command.extend([
        "-cp", classpath, MAIN_CLASS, str(path), str(out_jsonl),
        "--max-attempts", str(args.max_attempts),
    ])
    if args.limit > 0:
        command.extend(["--limit", str(args.limit)])
    log_path = path.with_suffix(".log")
    with log_path.open("a", encoding="utf-8") as log:
        completed = subprocess.run(command, stdout=log, stderr=log, cwd=root)
    return path.name, completed.returncode


def merge_outputs(shard_paths: list[Path], merged: Path) -> int:
    rows = 0
    with merged.open("w", encoding="utf-8") as output:
        for shard in shard_paths:
            result = shard.with_suffix(".jsonl")
            if not result.is_file():
                continue
            with result.open(encoding="utf-8") as stream:
                for line in stream:
                    if line.strip():
                        output.write(line)
                        rows += 1
    return rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--shards", type=int, default=1)
    parser.add_argument("--workers", type=int, default=1,
                        help="maximum concurrent JVMs; independent from shard count")
    parser.add_argument("--java", default="java")
    parser.add_argument("--xmx", default="4g")
    parser.add_argument("--limit", type=int, default=0, help="per-shard pair limit")
    parser.add_argument("--max-attempts", type=int, default=1)
    parser.add_argument("--config-id", required=True)
    parser.add_argument("--environment-id", required=True,
                        help="frozen hardware/image identifier used in the paper records")
    parser.add_argument("--skip-dynamic", action="store_true",
                        help="disable T4 dynamic execution for a separately labeled run")
    parser.add_argument("--disable-stubs", action="store_true",
                        help="forbid generated dependency context (required for primary BCB)")
    parser.add_argument("--java-opt", action="append", default=[])
    args = parser.parse_args()

    if args.shards < 1 or args.workers < 1 or args.workers > args.shards:
        parser.error("require 1 <= workers <= shards")
    if args.max_attempts < 1:
        parser.error("--max-attempts must be >= 1")

    root = code_sim_root()
    args.manifest = args.manifest.resolve()
    args.out = args.out.resolve()
    execution_manifest.validate_manifest(args.manifest)
    dataset_id = read_dataset_id(args.manifest)
    classpath = build_classpath(root)
    args.out.mkdir(parents=True, exist_ok=True)
    config = frozen_config(args, root, dataset_id)
    lock_config(args.out / "run_config.json", config)
    machine_path = args.out / "machine.json"
    if not machine_path.exists():
        machine_path.write_text(json.dumps(machine_metadata(), indent=2, sort_keys=True) + "\n",
                                encoding="utf-8")
    history = args.out / "run_history.jsonl"
    append_history(history, "start", {"workers": args.workers})

    shards = split_manifest(args.manifest, args.out, args.shards)
    failures = 0
    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = {executor.submit(run_shard, path, args, root, classpath, config): path
                   for path in shards}
        for future in as_completed(futures):
            name, return_code = future.result()
            print(f"[shards] {name} exited rc={return_code}")
            if return_code != 0:
                failures += 1

    merged = args.out / "merged.jsonl"
    result_rows = merge_outputs(shards, merged)
    append_history(history, "finish", {"failed_shards": failures, "result_rows": result_rows})
    print(f"[shards] merged {result_rows} attempt rows -> {merged}")
    if failures:
        sys.exit(f"[shards] {failures} shard(s) failed; inspect .log files and rerun unchanged")


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, ValueError) as error:
        sys.exit(f"[shards] ERROR: {error}")
