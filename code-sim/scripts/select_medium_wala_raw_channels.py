#!/usr/bin/env python3
import csv
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
STABILITY_ROOT = ROOT / "target" / "robustness" / "medium" / "stability"
OUTPUT = ROOT / "target" / "robustness" / "medium" / "channel_selection.csv"


NEVER_KNN_CHANNELS = {
    "program.input",
    "class.rawClassLoader",
    "method.rawSymbolTableText",
    "method.rawIRText",
}

LOW_DISCRIMINATION_CHANNELS = {
    "block.rawIsEntry",
    "block.rawIsExit",
}

PREFERRED_PREFIXES = (
    "instruction.",
    "block.rawNormalSuccessor",
    "block.rawPredecessor",
    "block.rawExceptionalSuccessor",
    "method.rawCFGText",
)


def read_tables():
    by_channel = defaultdict(list)
    for path in sorted(STABILITY_ROOT.glob("*-channel_stability.csv")):
        project = path.name.replace("-channel_stability.csv", "")
        with path.open(encoding="utf-8") as handle:
            for row in csv.DictReader(handle):
                row = dict(row)
                row["project"] = project
                by_channel[row["channel"]].append(row)
    return by_channel


def mean(rows, field):
    values = [float(row[field]) for row in rows]
    return sum(values) / len(values) if values else 0.0


def total(rows, field):
    return sum(int(row[field]) for row in rows)


def decision(channel, avg_stability, avg_std, avg_coverage, distinct_values):
    if channel in NEVER_KNN_CHANNELS:
        return "DROP", "unstable_or_nonsemantic_raw_dump"
    if channel in LOW_DISCRIMINATION_CHANNELS:
        return "DROP", "stable_but_low_discrimination"
    if distinct_values <= 1:
        return "DROP", "stable_but_constant"
    if avg_coverage < 0.80:
        return "DROP", "low_coverage"
    if avg_stability < 0.90:
        return "DROP", "low_cross_build_stability"
    if avg_std > 0.20:
        return "REVIEW", "stability_varies_by_method"
    if channel.startswith(PREFERRED_PREFIXES):
        return "KEEP", "stable_traceable_raw_channel"
    if channel.startswith("method.rawMethod") or channel in {
        "method.rawDeclaringClass",
        "method.rawParameterType",
        "method.rawReturnType",
        "class.rawClassName",
        "class.rawClassString",
    }:
        return "REVIEW", "identity_or_signature_channel_not_block_content"
    return "REVIEW", "stable_but_needs_manual_review"


def main():
    rows = []
    for channel, project_rows in sorted(read_tables().items()):
        avg_stability = mean(project_rows, "avg_stability")
        avg_std = mean(project_rows, "stability_std")
        avg_coverage = mean(project_rows, "coverage")
        distinct_values = total(project_rows, "distinct_values")
        pair_count = total(project_rows, "pair_count")
        selected_decision, reason = decision(
            channel,
            avg_stability,
            avg_std,
            avg_coverage,
            distinct_values,
        )
        rows.append({
            "channel": channel,
            "projects": len(project_rows),
            "avg_stability": f"{avg_stability:.4f}",
            "avg_stability_std": f"{avg_std:.4f}",
            "avg_coverage": f"{avg_coverage:.4f}",
            "distinct_values_total": distinct_values,
            "pair_count_total": pair_count,
            "decision": selected_decision,
            "reason": reason,
        })
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "channel",
                "projects",
                "avg_stability",
                "avg_stability_std",
                "avg_coverage",
                "distinct_values_total",
                "pair_count_total",
                "decision",
                "reason",
            ],
        )
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote medium raw channel selection table to {OUTPUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
