#!/usr/bin/env python3
"""Inject generated clone fragments into unrelated host files.

`gen_mutants.py` alone produces pairs whose two sides are near-identical whole files, so the
"clone region" is the entire file: detection is trivial and there is no boundary to localise.  The
Mutation and Injection Framework's second phase exists for exactly this reason -- a mutant is
injected into a real subject system, so the clone becomes a FRAGMENT surrounded by unrelated code
and a detector has to find its boundaries.  This script is that phase.

Each output pair is:

    HostA.java = host A  +  the ORIGINAL fragment
    HostB.java = host B  +  the MUTANT fragment      (host A != host B)

so the only thing the two files have in common is the injected fragment, whose line range on each
side is recorded as the region-level ground truth.

The fragment is injected as a NESTED STATIC CLASS.  That keeps the fragment's own fields and helper
methods with it, gives it a private namespace so nothing collides with the host, and needs no
renaming of the fragment's internals.  Every result is compile-checked; a pair that does not
compile on both sides is dropped and counted, since it could only ever reach the source-only
fallback.

Usage:
  py scripts/experiments/inject_mutants.py \
     --pairs   results/mutation-v1/pairs \
     --labels  results/mutation-v1/labels.csv \
     --hosts   /path/to/host/corpus \
     --out     results/mutation-injected
"""

from __future__ import annotations

import argparse
import collections
import csv
import hashlib
import random
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

PACKAGE = re.compile(r"(?m)^\s*package\s+[\w.$]+\s*;")
IMPORT = re.compile(r"(?m)^\s*import\s+(?:static\s+)?[\w.$]+(?:\.\*)?\s*;")
TOP_TYPE = re.compile(
    r"(?m)^\s*(?:public\s+|final\s+|abstract\s+|strictfp\s+)*"
    r"(class|interface|enum)\s+([A-Za-z_$][\w$]*)")
LABEL_FIELDS = [
    "pair_id", "operator", "clone_type", "seed_file", "host_left", "host_right",
    "left_clone_begin", "left_clone_end", "right_clone_begin", "right_clone_end",
    "left_mutation_begin", "left_mutation_end", "changed_lines",
]


def class_body(text: str) -> tuple[str, str] | None:
    """Return (type name, body between the top-level type's braces), or None if unparsable."""
    match = TOP_TYPE.search(text)
    if not match:
        return None
    open_brace = text.find("{", match.end())
    if open_brace < 0:
        return None
    depth = 0
    in_line_comment = in_block_comment = in_string = in_char = False
    index = open_brace
    while index < len(text):
        char = text[index]
        pair = text[index:index + 2]
        if in_line_comment:
            if char == "\n":
                in_line_comment = False
        elif in_block_comment:
            if pair == "*/":
                in_block_comment = False
                index += 1
        elif in_string:
            if char == "\\":
                index += 1
            elif char == '"':
                in_string = False
        elif in_char:
            if char == "\\":
                index += 1
            elif char == "'":
                in_char = False
        elif pair == "//":
            in_line_comment = True
            index += 1
        elif pair == "/*":
            in_block_comment = True
            index += 1
        elif char == '"':
            in_string = True
        elif char == "'":
            in_char = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return match.group(2), text[open_brace + 1:index]
        index += 1
    return None


def imports_of(text: str) -> list[str]:
    return [line.strip() for line in IMPORT.findall(text)]


def inject(host_text: str, fragment_text: str, nested_name: str,
           rng: random.Random) -> tuple[str, int, int] | None:
    """Splice the fragment into the host as a nested static class.

    Returns (new host source, 1-based first line of the injected block, last line), or None when
    either side cannot be parsed.
    """
    host = class_body(host_text)
    fragment = class_body(fragment_text)
    if host is None or fragment is None:
        return None
    _, host_body = host
    _, fragment_body = fragment

    nested = (f"    static class {nested_name} {{\n"
              f"{fragment_body}\n"
              f"    }}\n")

    # Choose a splice point at a top-level boundary inside the host body: after a line whose brace
    # depth is back to 0, so the fragment never lands inside one of the host's own methods.
    host_lines = host_body.splitlines()
    depth = 0
    boundaries = [0]
    for index, line in enumerate(host_lines):
        depth += line.count("{") - line.count("}")
        if depth == 0:
            boundaries.append(index + 1)
    cut = rng.choice(boundaries)

    merged_imports = list(dict.fromkeys(imports_of(host_text) + imports_of(fragment_text)))
    package = PACKAGE.search(host_text)
    header: list[str] = []
    if package:
        header.append(package.group(0).strip())
        header.append("")
    header += merged_imports
    if merged_imports:
        header.append("")

    host_name = TOP_TYPE.search(host_text).group(2)
    prefix = header + [f"public class {host_name} {{"] + host_lines[:cut]
    nested_lines = nested.splitlines()
    suffix = host_lines[cut:] + ["}"]
    out = prefix + nested_lines + suffix
    # Locate the block in the FINAL text rather than deriving it from prefix length. The header and
    # body slices contain blank entries whose join behaviour is easy to get wrong by a line or two,
    # and a ground truth that is silently off by two lines would corrupt every localisation score.
    marker = nested_lines[0]
    begin = out.index(marker, len(header)) + 1
    end = begin + len(nested_lines) - 1
    if out[begin - 1] != marker or "static class" not in out[begin - 1]:
        return None
    return "\n".join(out) + "\n", begin, end


def compiles(text: str, class_name: str, javac: str, release: str, timeout: int) -> bool:
    with tempfile.TemporaryDirectory(prefix="inject-") as directory:
        root = Path(directory)
        source = root / f"{class_name}.java"
        classes = root / "classes"
        classes.mkdir()
        source.write_text(text, encoding="utf-8")
        try:
            done = subprocess.run(
                [javac, "--release", release, "-proc:none", "-nowarn", "-d", str(classes),
                 str(source)],
                capture_output=True, text=True, timeout=timeout)
        except (subprocess.TimeoutExpired, OSError):
            return False
        return done.returncode == 0


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pairs", required=True, type=Path)
    parser.add_argument("--labels", required=True, type=Path)
    parser.add_argument("--hosts", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--host-min-lines", type=int, default=40)
    parser.add_argument("--host-max-lines", type=int, default=400)
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--release", default="17")
    parser.add_argument("--timeout", type=int, default=60)
    args = parser.parse_args()

    rng = random.Random(args.seed)
    hosts = [p for p in sorted(args.hosts.rglob("*.java"))
             if args.host_min_lines <= len(p.read_text(encoding="utf-8", errors="replace")
                                           .splitlines()) <= args.host_max_lines]
    if len(hosts) < 2:
        raise SystemExit("need at least two host files")
    rng.shuffle(hosts)
    print(f"[inject] {len(hosts)} candidate hosts")

    labels = {row["pair_id"]: row for row in csv.DictReader(args.labels.open(encoding="utf-8"))}
    out_pairs = args.out / "pairs"
    out_pairs.mkdir(parents=True, exist_ok=True)
    manifest_rows: list[dict[str, str]] = []
    label_rows: list[dict[str, str]] = []
    dropped: collections.Counter[str] = collections.Counter()

    for index, pair_id in enumerate(sorted(labels)):
        pair_dir = args.pairs / pair_id
        original_file, mutant_file = pair_dir / "Original.java", pair_dir / "Mutant.java"
        if not original_file.is_file() or not mutant_file.is_file():
            dropped["source pair missing"] += 1
            continue
        # Different hosts on the two sides, so the ONLY thing the files share is the fragment.
        host_left = hosts[(2 * index) % len(hosts)]
        host_right = hosts[(2 * index + 1) % len(hosts)]
        if host_left == host_right:
            dropped["hosts collided"] += 1
            continue

        nested = f"Injected_{pair_id}"
        left = inject(host_left.read_text(encoding="utf-8", errors="replace"),
                      original_file.read_text(encoding="utf-8", errors="replace"), nested, rng)
        right = inject(host_right.read_text(encoding="utf-8", errors="replace"),
                       mutant_file.read_text(encoding="utf-8", errors="replace"), nested, rng)
        if left is None or right is None:
            dropped["unparsable host or fragment"] += 1
            continue

        left_text, left_begin, left_end = left
        right_text, right_begin, right_end = right
        left_name = TOP_TYPE.search(host_left.read_text(encoding="utf-8", errors="replace")).group(2)
        right_name = TOP_TYPE.search(host_right.read_text(encoding="utf-8", errors="replace")).group(2)
        if not compiles(left_text, left_name, args.javac, args.release, args.timeout):
            dropped["injected left does not compile"] += 1
            continue
        if not compiles(right_text, right_name, args.javac, args.release, args.timeout):
            dropped["injected right does not compile"] += 1
            continue

        # Separate directories per side. Hosts drawn from one corpus routinely share a public type
        # name (every CodeNet submission is `Main`), and writing both sides into one directory made
        # the right file silently overwrite the left -- which then read back as a ground truth that
        # did not match its own file. The public type name must stay unchanged for javac, so the
        # directory, not the file name, has to carry the distinction.
        target = out_pairs / pair_id
        left_path = target / "left" / f"{left_name}.java"
        right_path = target / "right" / f"{right_name}.java"
        left_path.parent.mkdir(parents=True, exist_ok=True)
        right_path.parent.mkdir(parents=True, exist_ok=True)
        left_path.write_text(left_text, encoding="utf-8")
        right_path.write_text(right_text, encoding="utf-8")

        source_label = labels[pair_id]
        manifest_rows.append({"pair_id": pair_id, "left_path": str(left_path.resolve()),
                              "right_path": str(right_path.resolve())})
        label_rows.append({
            "pair_id": pair_id,
            "operator": source_label.get("operator", ""),
            "clone_type": source_label.get("clone_type", ""),
            "seed_file": source_label.get("seed_file", ""),
            "host_left": host_left.name, "host_right": host_right.name,
            "left_clone_begin": left_begin, "left_clone_end": left_end,
            "right_clone_begin": right_begin, "right_clone_end": right_end,
            # Mutation offset inside the fragment, shifted onto the injected block.
            "left_mutation_begin": left_begin + int(source_label.get("left_begin", 0) or 0),
            "left_mutation_end": left_begin + int(source_label.get("left_end", 0) or 0),
            "changed_lines": source_label.get("changed_lines", ""),
        })

    for path, fields, rows in (
            (args.out / "manifest.csv", ["pair_id", "left_path", "right_path"], manifest_rows),
            (args.out / "labels.csv", LABEL_FIELDS, label_rows)):
        with path.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
            writer.writeheader()
            writer.writerows(rows)

    by_type = collections.Counter(row["clone_type"] for row in label_rows)
    digest = hashlib.sha256((args.out / "manifest.csv").read_bytes()).hexdigest()
    print(f"[inject] wrote {len(manifest_rows)} injected pairs {dict(by_type)} -> {args.out}")
    print(f"[inject] manifest sha256={digest}")
    if dropped:
        print(f"[inject] dropped {sum(dropped.values())}:")
        for reason, count in dropped.most_common():
            print(f"[inject]   {count:5d}  {reason}")
    if not manifest_rows:
        sys.exit(1)


if __name__ == "__main__":
    main()
