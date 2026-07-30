#!/usr/bin/env python3
"""
Bring a batch whose references were split AT GENERATION TIME down to the round-4 rule.

Batches 1-4 were generated with one mutation interval per range and corrected offline:
`correct_wrap_intervals.py` (round 1) then `split_mutation_runs.py` (round 4, rename only).
Batch 5's generator emits `mutationRuns()` directly, which splits EVERY operator -- the round-3
rule that was measured and rejected, because re-indentation is one contiguous edit and fragmenting
it produces pieces that land where the detector correctly sees no change (`t1_reindent` 98.7 % ->
94.8 %). Merging batch 5 as generated would drag that defect into the pooled figure.

So this walks the other way: coalesce the per-opcode runs back into one interval per
(pair_id, operator), for every operator EXCEPT the scattered-effect ones that round 4 keeps split.
The result is reference-definition-identical to batches 1-4, which is the precondition for pooling.

Wrap is left alone in either direction: the generator already emits the added statement's own line
(left side empty), which is what round 1 arrived at offline, so there is nothing to coalesce.

Correctness is not asserted, it is round-tripped. `--selftest` coalesces batch 4's `regions_left_v3`
(split) and requires it to reproduce `regions_left_v2` (unsplit) row for row. If the merge rule were
wrong in any way that matters, that comparison fails.

Usage:
  coalesce_mutation_runs.py --selftest
  coalesce_mutation_runs.py batch5
"""

from __future__ import annotations

import argparse
import csv
from collections import defaultdict
from pathlib import Path

ROOT = Path("results/formal-v1")
FIELDS = ["pair_id", "ref_index", "kind", "clone_type", "operator",
          "left_begin", "left_end", "right_begin", "right_end"]

# Operators whose effect is intrinsically scattered stay split (round 4). Everything else is one
# contiguous edit and must be one interval.
KEEP_SPLIT = ("t2_rename",)


def _span(rows: list[dict], begin: str, end: str) -> tuple[str, str]:
    """Outer extent of one side, ignoring rows that do not touch it.

    A side is absent when its bounds are empty or 0 -- an inserted line has no left counterpart --
    and an absent side must stay absent rather than collapse to 0..0, which would be a claim that
    line zero was mutated.
    """
    lo, hi = None, None
    for row in rows:
        b, e = row[begin].strip(), row[end].strip()
        if not b or b == "0" or not e or e == "0":
            continue
        lo = int(b) if lo is None else min(lo, int(b))
        hi = int(e) if hi is None else max(hi, int(e))
    return ("", "") if lo is None else (str(lo), str(hi))


def assert_single_range(rows: list[dict]) -> None:
    """Refuse to run on a multi-range corpus.

    Grouping by (pair_id, operator) is only sound when a pair has one mutated range. The planned
    two-range stratum can give one pair two ranges under the SAME operator, and merging those would
    invent a single interval spanning the untouched code between them -- silently, and in the
    direction that flatters the boundary numbers. One CLONE_INTERVAL per pair is the marker of a
    single-range batch, so that is the gate.
    """
    per_pair: dict[str, int] = defaultdict(int)
    for row in rows:
        if row["kind"] == "CLONE_INTERVAL":
            per_pair[row["pair_id"]] += 1
    multi = [p for p, n in per_pair.items() if n > 1]
    if multi:
        raise SystemExit(
            f"REFUSING: {len(multi)} pairs have more than one clone interval (e.g. {multi[:3]}). "
            "This batch is multi-range, so (pair_id, operator) is not a unique key and coalescing "
            "would merge two separate edits into one interval. Extend the reference schema with a "
            "range id before using this script on such a corpus.")


def coalesce(rows: list[dict]) -> list[dict]:
    """One MUTATION row per (pair_id, operator); other kinds pass through untouched."""
    assert_single_range(rows)
    out: list[dict] = []
    groups: dict[tuple[str, str], list[dict]] = defaultdict(list)
    order: list[tuple[str, str]] = []

    for row in rows:
        if row["kind"] != "MUTATION" or row["operator"].startswith(KEEP_SPLIT):
            out.append(row)
            continue
        key = (row["pair_id"], row["operator"])
        if key not in groups:
            order.append(key)
        groups[key].append(row)

    for key in order:
        members = groups[key]
        merged = dict(members[0])
        merged["left_begin"], merged["left_end"] = _span(members, "left_begin", "left_end")
        merged["right_begin"], merged["right_end"] = _span(members, "right_begin", "right_end")
        out.append(merged)

    out.sort(key=lambda r: (r["pair_id"], int(r["ref_index"] or 0)))
    return out


def _read(path: Path) -> list[dict]:
    return list(csv.DictReader(path.open()))


def _key(row: dict) -> tuple:
    """Identity of a reference for comparison: everything but ref_index, which renumbers."""
    return (row["pair_id"], row["kind"], row["clone_type"], row["operator"],
            row["left_begin"].strip(), row["left_end"].strip(),
            row["right_begin"].strip(), row["right_end"].strip())


def selftest() -> int:
    """Coalescing batch 4's split references must reproduce its unsplit ones exactly."""
    base = ROOT / "batch4"
    split, unsplit = _read(base / "regions_left_v3.csv"), _read(base / "regions_left_v2.csv")

    # v3 keeps rename split by the round-4 rule, so for the round trip coalesce everything.
    global KEEP_SPLIT
    saved, KEEP_SPLIT = KEEP_SPLIT, ()
    try:
        rebuilt = coalesce(split)
    finally:
        KEEP_SPLIT = saved

    got = sorted(_key(r) for r in rebuilt)
    want = sorted(_key(r) for r in unsplit)
    print(f"batch4: {len(split)} split rows -> {len(rebuilt)} coalesced; "
          f"{len(unsplit)} rows in regions_left_v2.csv")
    if got == want:
        print("SELFTEST OK — coalescing round-trips to the unsplit references exactly")
        return 0

    only_got = [k for k in got if k not in set(want)]
    only_want = [k for k in want if k not in set(got)]
    print(f"SELFTEST FAILED — {len(only_got)} rebuilt rows absent from v2, "
          f"{len(only_want)} v2 rows not rebuilt")
    for k in only_got[:5]:
        print("  rebuilt only:", k)
    for k in only_want[:5]:
        print("  v2 only:     ", k)
    return 1


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("batch", nargs="?")
    parser.add_argument("--selftest", action="store_true")
    args = parser.parse_args()

    if args.selftest:
        raise SystemExit(selftest())
    if not args.batch:
        parser.error("give a batch name, or --selftest")

    base = ROOT / args.batch
    rows = _read(base / "regions_left.csv")

    # Coalesce EVERY operator, rename included, and write the v2 slot. `split_mutation_runs.py` then
    # re-splits from there, exactly as it did for batches 1-4. Leaving batch 5's rename runs as the
    # generator emitted them would have left the two arms' rename references produced by two
    # different implementations -- Java `mutationRuns()` here, the Python splitter there -- and that
    # difference is measurable: scored that way, rename read 96.6 % against 99.5 % for batches 1-4.
    # Pooling requires the reference pipeline to be the same code, not merely the same intent.
    global KEEP_SPLIT
    KEEP_SPLIT = ()
    out = coalesce(rows)

    before = sum(1 for r in rows if r["kind"] == "MUTATION")
    after = sum(1 for r in out if r["kind"] == "MUTATION")
    target = base / "regions_left_v2.csv"
    with target.open("w", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows({k: r[k] for k in FIELDS} for r in out)
    print(f"{args.batch}: MUTATION {before} -> {after}; wrote {target}")


if __name__ == "__main__":
    main()
