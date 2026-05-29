#!/usr/bin/env python3
import csv
import itertools
import json
import sys
from collections import defaultdict
from pathlib import Path


def method_id(record):
    provenance = record.get("provenance") or []
    if not provenance:
        return ""
    value = provenance[-1]
    if "#b" in value:
        return value.split("#b", 1)[0]
    if "#" in value:
        return value.split("#", 1)[1]
    return value


def load_snapshot(path):
    by_method_channel = defaultdict(set)
    with Path(path).open(encoding="utf-8") as handle:
        for line in handle:
            record = json.loads(line)
            mid = method_id(record)
            if not mid:
                continue
            key = (mid, record["channel"])
            by_method_channel[key].add(record["rawValue"])
    return by_method_channel


def jaccard(left, right):
    if not left and not right:
        return 1.0
    union = left | right
    if not union:
        return 1.0
    return len(left & right) / len(union)


def summarize(snapshot_paths):
    snapshots = [(Path(path).stem, load_snapshot(path)) for path in snapshot_paths]
    channels = sorted({
        channel
        for _, snapshot in snapshots
        for _, channel in snapshot.keys()
    })
    methods = sorted({
        method
        for _, snapshot in snapshots
        for method, _ in snapshot.keys()
    })
    rows = []
    for channel in channels:
        scores = []
        covered = set()
        distinct_values = set()
        channel_methods = set()
        for method in methods:
            present = []
            for variant, snapshot in snapshots:
                values = snapshot.get((method, channel), set())
                if values:
                    channel_methods.add(method)
                    covered.add((variant, method))
                    distinct_values.update(values)
                    present.append(values)
            for left, right in itertools.combinations(present, 2):
                scores.append(jaccard(left, right))
        avg = sum(scores) / len(scores) if scores else 0.0
        variance = sum((score - avg) ** 2 for score in scores) / len(scores) if scores else 0.0
        rows.append({
            "channel": channel,
            "avg_stability": f"{avg:.4f}",
            "stability_std": f"{variance ** 0.5:.4f}",
            "coverage": f"{len(covered) / max(1, len(channel_methods) * len(snapshots)):.4f}",
            "distinct_values": len(distinct_values),
            "pair_count": len(scores),
        })
    return rows


def main():
    if len(sys.argv) < 3:
        print(
            "Usage: analyze_wala_raw_channel_stability.py <output-csv> <snapshot-jsonl>...",
            file=sys.stderr,
        )
        return 2
    output = Path(sys.argv[1])
    rows = summarize(sys.argv[2:])
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "channel",
                "avg_stability",
                "stability_std",
                "coverage",
                "distinct_values",
                "pair_count",
            ],
        )
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote channel stability table to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
