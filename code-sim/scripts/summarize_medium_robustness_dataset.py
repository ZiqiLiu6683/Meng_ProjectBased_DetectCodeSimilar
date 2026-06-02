#!/usr/bin/env python3
import csv
import json
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SNAPSHOT_ROOT = ROOT / "target" / "robustness" / "medium" / "snapshots"
OUTPUT = ROOT / "target" / "robustness" / "medium" / "dataset_summary.csv"


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


def load_variant(path):
    methods = set()
    blocks_by_method = defaultdict(set)
    records = 0
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            records += 1
            record = json.loads(line)
            mid = method_id(record)
            if not mid:
                continue
            if record["channel"] == "method.rawMethodSignature":
                methods.add(record["rawValue"])
            if record["channel"] == "block.rawBlockNumber":
                blocks_by_method[mid].add(record["rawValue"])
    return methods, blocks_by_method, records


def main():
    rows = []
    for project_dir in sorted(path for path in SNAPSHOT_ROOT.iterdir() if path.is_dir()):
        variants = sorted(project_dir.glob("*.jsonl"))
        variant_data = [load_variant(path) for path in variants]
        if not variant_data:
            continue
        common_methods = set.intersection(*(methods for methods, _, _ in variant_data))
        min5_common = set()
        for method in common_methods:
            if all(len(blocks.get(method, set())) >= 5 for _, blocks, _ in variant_data):
                min5_common.add(method)
        rows.append({
            "project": project_dir.name,
            "variants": len(variants),
            "raw_records": sum(records for _, _, records in variant_data),
            "methods_in_all_variants": len(common_methods),
            "methods_min_5_blocks_all_variants": len(min5_common),
            "snapshot_dir": str(project_dir),
        })
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "project",
                "variants",
                "raw_records",
                "methods_in_all_variants",
                "methods_min_5_blocks_all_variants",
                "snapshot_dir",
            ],
        )
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote medium robustness dataset summary to {OUTPUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
