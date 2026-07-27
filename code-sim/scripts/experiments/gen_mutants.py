#!/usr/bin/env python3
"""Synthesise (original, mutant) clone pairs of KNOWN type using the official Mutation and
Injection Framework operators.

Replaces gen_mutants.sh, which depends on shuf/mapfile/timeout and therefore only runs on GNU
userlands (the WSL setup).  This version is pure Python 3 and runs anywhere TXL does.

Beyond the clone type, it records the MUTATED LINE RANGE on both sides, recovered by diffing the
original against the mutant.  That gives every pair a localisation ground truth, which neither
BigCloneBench (pair-level ranges only) nor the in-house corpus (no ranges at all) provides, so a
detector's region boundaries can finally be scored rather than assumed.

Operator -> clone type follows the Roy & Cordy editing taxonomy, as in the framework:
  T1 layout/comments   mCW_A mCW_R mCC_BT mCC_EOL mCF_A mCF_R
  T2 rename/literals   mSRI mARI mRL_N mRL_S
  T3 statement edits   mIL mDL mML mSIL mSDL

Usage:
  py scripts/experiments/gen_mutants.py \
     --mutators ~/codesim-mif/mutators-java \
     --seeds    /path/to/corpus/src/main/java \
     --out      results/mutation-v1 \
     [--per-operator 400] [--seed 42] [--min-lines 20] [--max-lines 400]
     [--project-root /path/to/corpus] [--timeout 60] [--limit-seeds 0]
"""

from __future__ import annotations

import argparse
import collections
import csv
import difflib
import hashlib
import random
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

OPERATORS: dict[str, list[str]] = {
    "T1": ["mCW_A", "mCW_R", "mCC_BT", "mCC_EOL", "mCF_A", "mCF_R"],
    "T2": ["mSRI", "mARI", "mRL_N", "mRL_S"],
    "T3": ["mIL", "mDL", "mML", "mSIL", "mSDL"],
}
MANIFEST_FIELDS = ["pair_id", "left_path", "right_path", "left_project_root", "right_project_root"]
LABEL_FIELDS = [
    "pair_id", "operator", "clone_type", "seed_file",
    "left_begin", "left_end", "right_begin", "right_end", "changed_lines",
]


def clone_type_of(operator: str) -> str:
    for clone_type, operators in OPERATORS.items():
        if operator in operators:
            return clone_type
    return "UNKNOWN"


def mutated_range(original: str, mutant: str) -> tuple[int, int, int, int, int]:
    """Line span each side's edits occupy, 1-based inclusive; (0,0,0,0,0) when identical.

    T1 operators change only layout or comments, so a diff can report the whole file; that is
    still the honest answer -- the transformation really did touch those lines.
    """
    left_lines = original.splitlines()
    right_lines = mutant.splitlines()
    matcher = difflib.SequenceMatcher(a=left_lines, b=right_lines, autojunk=False)
    left_hits: list[int] = []
    right_hits: list[int] = []
    changed = 0
    for tag, i1, i2, j1, j2 in matcher.get_opcodes():
        if tag == "equal":
            continue
        changed += max(i2 - i1, j2 - j1)
        if i2 > i1:
            left_hits += [i1 + 1, i2]
        if j2 > j1:
            right_hits += [j1 + 1, j2]
    if not left_hits and not right_hits:
        return 0, 0, 0, 0, 0
    # An insertion has no left extent (and a deletion no right extent); anchor the empty side on
    # the other side's start so the range stays usable instead of collapsing to zero.
    if not left_hits:
        left_hits = [min(right_hits), min(right_hits)]
    if not right_hits:
        right_hits = [min(left_hits), min(left_hits)]
    return min(left_hits), max(left_hits), min(right_hits), max(right_hits), changed


def collect_seeds(seed_dir: Path, min_lines: int, max_lines: int, rng: random.Random,
                  limit: int) -> list[Path]:
    seeds = []
    for path in sorted(seed_dir.rglob("*.java")):
        try:
            lines = len(path.read_text(encoding="utf-8", errors="replace").splitlines())
        except OSError:
            continue
        if min_lines <= lines <= max_lines:
            seeds.append(path)
    rng.shuffle(seeds)
    return seeds[:limit] if limit > 0 else seeds


def run_txl(txl: str, mutator: Path, source: Path, target: Path, timeout: int) -> bool:
    try:
        completed = subprocess.run(
            [txl, "-o", str(target), str(source), str(mutator)],
            capture_output=True, text=True, timeout=timeout)
    except (subprocess.TimeoutExpired, OSError):
        return False
    return completed.returncode == 0 and target.is_file() and target.stat().st_size > 0


PUBLIC_TYPE = re.compile(
    r"public\s+(?:final\s+|abstract\s+|sealed\s+|non-sealed\s+|strictfp\s+)*"
    r"(?:class|interface|enum|record)\s+([A-Za-z_$][A-Za-z0-9_$]*)")


def compiles(text: str, javac: str, release: str, timeout: int) -> bool:
    """Does this source compile on its own under the detector's Java contract?

    The mutation operators are purely syntactic: they rewrite the parse tree without checking that
    the result still type-checks.  Measured on a 30-pair sample, that left only 67% of pairs
    analysable -- e.g. mSRI renames a variable at its declaration but also inside an import, mDL
    deletes a declaration whose uses remain, mSIL inserts a statement that breaks the syntax.  A
    pair whose sides do not compile cannot reach the graph pipeline at all, which is exactly what
    this corpus exists to exercise, so both sides are checked here and the pair is dropped
    otherwise.  Report generated vs kept counts: the drop rate is a property of the operators, not
    a silent filter.

    The file is named after its public type, mirroring what the detector itself does, so a mutant
    that renamed the class is not failed for the filename alone.
    """
    match = PUBLIC_TYPE.search(text)
    name = f"{match.group(1)}.java" if match else "Input.java"
    with tempfile.TemporaryDirectory(prefix="genmut-") as directory:
        root = Path(directory)
        source = root / name
        classes = root / "classes"
        classes.mkdir()
        try:
            source.write_text(text, encoding="utf-8")
        except (OSError, ValueError):
            return False
        try:
            completed = subprocess.run(
                [javac, "--release", release, "-proc:none", "-nowarn",
                 "-d", str(classes), str(source)],
                capture_output=True, text=True, timeout=timeout)
        except (subprocess.TimeoutExpired, OSError):
            return False
        return completed.returncode == 0


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mutators", required=True, type=Path,
                        help="directory holding the framework's java *.txl operators")
    parser.add_argument("--seeds", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--per-operator", type=int, default=400)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--min-lines", type=int, default=20)
    parser.add_argument("--max-lines", type=int, default=400)
    parser.add_argument("--limit-seeds", type=int, default=0)
    parser.add_argument("--timeout", type=int, default=60)
    parser.add_argument("--txl", default="txl")
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--release", default="17",
                        help="must match the detector's frozen Java release")
    parser.add_argument("--no-compile-check", action="store_true",
                        help="keep pairs whose sides do not compile; they will only ever "
                             "reach the source-only fallback, never the graph pipeline")
    parser.add_argument("--project-root", type=Path, default=None,
                        help="recorded per side so the runner can resolve the seed's real sibling "
                             "sources via -sourcepath instead of generating stubs")
    args = parser.parse_args()

    if shutil.which(args.txl) is None:
        raise SystemExit(f"TXL not found on PATH as {args.txl!r}; install it from https://txl.ca/")
    for directory in (args.mutators, args.seeds):
        if not directory.is_dir():
            raise SystemExit(f"not a directory: {directory}")

    pairs_dir = args.out / "pairs"
    pairs_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = args.out / "manifest.csv"
    labels_path = args.out / "labels.csv"

    rng = random.Random(args.seed)
    seeds = collect_seeds(args.seeds, args.min_lines, args.max_lines, rng, args.limit_seeds)
    if not seeds:
        raise SystemExit("no seed files matched the line-count filter")
    print(f"[gen] {len(seeds)} candidate seeds; target {args.per_operator} pairs/operator")

    project_root = str(args.project_root.resolve()) if args.project_root else ""
    dropped: collections.Counter[str] = collections.Counter()
    seed_compiles: dict[Path, bool] = {}
    manifest_rows: list[dict[str, str]] = []
    label_rows: list[dict[str, str]] = []
    work = args.out / ".work"
    work.mkdir(parents=True, exist_ok=True)

    for clone_type, operators in OPERATORS.items():
        for operator in operators:
            mutator = args.mutators / f"{operator}.txl"
            if not mutator.is_file():
                print(f"[gen] {operator}: operator file missing, skipped")
                continue
            made = tried = 0
            for source in seeds:
                if made >= args.per_operator:
                    break
                tried += 1
                pair_id = f"{operator}_{made:05d}"
                pair_dir = pairs_dir / pair_id
                original = pair_dir / "Original.java"
                mutant = pair_dir / "Mutant.java"
                if original.is_file() and mutant.is_file():
                    made += 1  # resume: keep an already generated pair
                    continue
                pair_dir.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, original)
                scratch = work / f"{pair_id}.java"
                if not run_txl(args.txl, mutator, original, scratch, args.timeout):
                    shutil.rmtree(pair_dir, ignore_errors=True)
                    continue
                original_text = original.read_text(encoding="utf-8", errors="replace")
                mutant_text = scratch.read_text(encoding="utf-8", errors="replace")
                if original_text == mutant_text:
                    shutil.rmtree(pair_dir, ignore_errors=True)  # operator did not apply
                    continue
                if not args.no_compile_check:
                    if seed_compiles.get(source) is None:
                        seed_compiles[source] = compiles(
                            original_text, args.javac, args.release, args.timeout)
                    if not seed_compiles[source]:
                        dropped["seed does not compile"] += 1
                        shutil.rmtree(pair_dir, ignore_errors=True)
                        scratch.unlink(missing_ok=True)
                        continue
                    if not compiles(mutant_text, args.javac, args.release, args.timeout):
                        dropped[f"mutant does not compile ({operator})"] += 1
                        shutil.rmtree(pair_dir, ignore_errors=True)
                        scratch.unlink(missing_ok=True)
                        continue
                shutil.move(str(scratch), str(mutant))
                lb, le, rb, re_, changed = mutated_range(original_text, mutant_text)
                manifest_rows.append({
                    "pair_id": pair_id,
                    "left_path": str(original.resolve()),
                    "right_path": str(mutant.resolve()),
                    "left_project_root": project_root,
                    "right_project_root": project_root,
                })
                label_rows.append({
                    "pair_id": pair_id, "operator": operator, "clone_type": clone_type,
                    "seed_file": str(source.relative_to(args.seeds)),
                    "left_begin": lb, "left_end": le,
                    "right_begin": rb, "right_end": re_, "changed_lines": changed,
                })
                made += 1
            print(f"[gen] {operator} ({clone_type}): {made} pairs from {tried} seeds tried")

    shutil.rmtree(work, ignore_errors=True)
    for path, fields, rows in ((manifest_path, MANIFEST_FIELDS, manifest_rows),
                               (labels_path, LABEL_FIELDS, label_rows)):
        with path.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
            writer.writeheader()
            writer.writerows(rows)
    digest = hashlib.sha256(manifest_path.read_bytes()).hexdigest()
    by_type: dict[str, int] = {}
    for row in label_rows:
        by_type[row["clone_type"]] = by_type.get(row["clone_type"], 0) + 1
    print(f"[gen] wrote {len(manifest_rows)} pairs {by_type} -> {args.out}")
    print(f"[gen] manifest sha256={digest}")
    if dropped:
        total = sum(dropped.values())
        print(f"[gen] dropped {total} generated pairs that would never reach the graph "
              f"pipeline:")
        for reason, count in dropped.most_common():
            print(f"[gen]   {count:5d}  {reason}")
    if not manifest_rows:
        sys.exit(1)


if __name__ == "__main__":
    main()
