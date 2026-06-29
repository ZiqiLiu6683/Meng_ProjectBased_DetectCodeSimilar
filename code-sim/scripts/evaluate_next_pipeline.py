#!/usr/bin/env python3
"""Batch evaluation runner for the next-generation region clone pipeline."""

import argparse
import csv
import json
import subprocess
import time
from collections import Counter, defaultdict
from pathlib import Path


TYPE_MATCH_ALIASES = {
    "T1": {"T1"},
    "T2": {"T2"},
    "T3": {"T3"},
    "NON_CLONE": {"NON_CLONE"},
    # The current source-only next pipeline does not approve T4 by itself.
    # These rows are tracked as exploratory instead of failed strict type tests.
    "T4_WEAK": {"POSSIBLE_T4_CANDIDATE", "T4_CONFIRMED"},
}


# Engine selection lets us compare the source-only pipeline (plain) against the
# WALA CFG-on pipeline (wala) without changing the default behavior.
#   plain : NextPipelineMain          -> source-only, no CFG channel
#   wala  : WalaNextPipelineMain --json -> mounts RawToolCfgCandidateProvider
ENGINES = {
    "plain": {
        "main_class": "com.ziqi.codesim.next.NextPipelineMain",
        "profiles": [],
        "extra_args": [],
        "suffix": "",
    },
    "wala": {
        "main_class": "com.ziqi.codesim.next.semantic.WalaNextPipelineMain",
        "profiles": ["-Psemantic-analysis"],
        "extra_args": ["--json"],
        "suffix": "_wala",
    },
}


def parse_args():
    parser = argparse.ArgumentParser(description="Evaluate the next-generation region clone pipeline on labeled pairs.")
    parser.add_argument(
        "--engine",
        choices=sorted(ENGINES),
        default="plain",
        help="Which decision engine to evaluate: 'plain' (source-only) or 'wala' (CFG-on).",
    )
    parser.add_argument(
        "--pairs",
        default="evaluation/long_pairs.csv",
        help="CSV file with labeled evaluation pairs, relative to code-sim.",
    )
    parser.add_argument(
        "--output",
        default=None,
        help="Output CSV path, relative to code-sim. Defaults to an engine-specific path.",
    )
    parser.add_argument(
        "--summary",
        default=None,
        help="Output Markdown summary path, relative to code-sim. Defaults to an engine-specific path.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Optional maximum number of rows to evaluate.",
    )
    args = parser.parse_args()
    suffix = ENGINES[args.engine]["suffix"]
    if args.output is None:
        args.output = f"evaluation/results/long_code_next_pipeline_results{suffix}.csv"
    if args.summary is None:
        args.summary = f"evaluation/results/long_code_next_pipeline_summary{suffix}.md"
    return args


def run_pair(file_a, file_b, engine):
    spec = ENGINES[engine]
    exec_args = " ".join([file_a, file_b, *spec["extra_args"]])
    start = time.perf_counter()
    cmd = [
        "mvn",
        "-q",
        *spec["profiles"],
        "exec:java",
        f"-Dexec.mainClass={spec['main_class']}",
        f"-Dexec.args={exec_args}",
    ]
    completed = subprocess.run(
        cmd,
        check=False,
        capture_output=True,
        text=True,
    )
    runtime_ms = round((time.perf_counter() - start) * 1000)
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip())
    return json.loads(completed.stdout), runtime_ms


# Restraint threshold lives HERE in the evaluation (a measurement tool), not in the system:
# a NON_CLONE pair is "restrained" when only a small fraction of either file is involved. The
# system itself just reports raw coverage and assigns no file-level verdict.
NON_CLONE_MAX_COVERAGE = 0.20


def expected_match_status(expected_type, dominant_type, overall_relationship,
                          region_type_counts, coverage_left, coverage_right):
    # Information-provider metric: the system does not emit a single file verdict, so we do not
    # require dominant_type == label. Instead we ask whether it surfaced the right information:
    #   - for a clone label, did at least one region of the expected type appear in the breakdown;
    #   - for NON_CLONE, was only a small fraction of either file involved (raw coverage), judged
    #     here in the evaluation rather than by a system-imposed relationship label.
    if expected_type == "T4_WEAK":
        return "exploratory"
    if expected_type == "NON_CLONE":
        restrained = min(float(coverage_left), float(coverage_right)) < NON_CLONE_MAX_COVERAGE
        return str(restrained)
    aliases = TYPE_MATCH_ALIASES.get(expected_type, {expected_type})
    return str(any(int(region_type_counts.get(alias, 0)) > 0 for alias in aliases))


def join_sorted(values):
    return "|".join(sorted(str(value) for value in values))


def first_regions_by_type(regions, limit=5):
    names = []
    for region in regions:
        if len(names) >= limit:
            break
        left = region["left"]["displayName"]
        right = region["right"]["displayName"]
        names.append(f'{region["type"]}:{left}->{right}')
    return "|".join(names)


def write_summary(summary_path, output_path, rows, engine="plain"):
    by_expected = defaultdict(list)
    for row in rows:
        by_expected[row["expected_type"]].append(row)

    engine_label = {"plain": "source-only (no CFG)", "wala": "WALA CFG-on"}.get(engine, engine)
    lines = [
        "# Long-code Next Pipeline Evaluation",
        "",
        f"Engine: `{engine}` ({engine_label})",
        "",
        f"Results CSV: `{output_path}`",
        "",
        "## Overall",
        "",
        f"- Pairs evaluated: {len(rows)}",
        f"- Expected type surfaced (NON_CLONE: restrained): {sum(row['type_match_status'] == 'True' for row in rows)}",
        f"- Exploratory T4 rows: {sum(row['type_match_status'] == 'exploratory' for row in rows)}",
        f"- Average runtime: {round(sum(int(row['runtime_ms']) for row in rows) / max(1, len(rows)))} ms",
        "",
        "## By Expected Type",
        "",
        # No single-verdict columns (dominant type / relationship shape): the system assigns no
        # file-level label. Instead show the actual per-type region evidence that was surfaced.
        "| Expected | Count | Surfaced | Inspection Priorities | Region Types Surfaced | Avg Affected L/R | Avg Selected Regions |",
        "| --- | ---: | ---: | --- | --- | --- | ---: |",
    ]

    type_keys = ["T1", "T2", "T3", "T4_CONFIRMED", "POSSIBLE_T4_CANDIDATE", "NON_CLONE"]
    for expected_type in sorted(by_expected):
        group = by_expected[expected_type]
        priorities = Counter(row["inspection_priority"] for row in group)
        surfaced_types = {
            t: sum(int(row[f"count_{t}"]) for row in group) for t in type_keys
        }
        surfaced_types = {t: c for t, c in surfaced_types.items() if c}
        strict_pass = sum(row["type_match_status"] == "True" for row in group)
        avg_left = sum(float(row["matched_coverage_left"]) for row in group) / len(group)
        avg_right = sum(float(row["matched_coverage_right"]) for row in group) / len(group)
        avg_selected = sum(int(row["selected_region_count"]) for row in group) / len(group)
        lines.append(
            f"| {expected_type} | {len(group)} | {strict_pass} | "
            f"{dict(priorities)} | {surfaced_types} | "
            f"{avg_left:.2f}/{avg_right:.2f} | {avg_selected:.1f} |"
        )

    lines.extend([
        "",
        "## Rows Needing Inspection",
        "",
        "| Pair | Expected | Priority | Affected L/R | Tags | First Regions |",
        "| --- | --- | --- | --- | --- | --- |",
    ])
    for row in rows:
        if row["type_match_status"] == "True":
            continue
        lines.append(
            f"| {row['pair_id']} | {row['expected_type']} | {row['inspection_priority']} | "
            f"{float(row['matched_coverage_left']):.2f}/"
            f"{float(row['matched_coverage_right']):.2f} | {row['file_tags']} | "
            f"{row['first_regions']} |"
        )

    summary_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    args = parse_args()
    root = Path.cwd()
    pairs_path = root / args.pairs
    output_path = root / args.output
    summary_path = root / args.summary
    output_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.parent.mkdir(parents=True, exist_ok=True)

    with pairs_path.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    if args.limit:
        rows = rows[:args.limit]

    fieldnames = [
        "project",
        "pair_id",
        "language",
        "expected_type",
        "type_match_status",
        "expected_scope",
        "inspection_priority",
        "relationship_shape",
        "overall_relationship",
        "dominant_region_type",
        "matched_coverage_left",
        "matched_coverage_right",
        "unrelated_code_ratio",
        "accepted_region_count",
        "selected_region_count",
        "suppressed_region_count",
        "emitted_region_count",
        "count_T1",
        "count_T2",
        "count_T3",
        "count_T4_CONFIRMED",
        "count_POSSIBLE_T4_CANDIDATE",
        "count_NON_CLONE",
        "coverage_T1",
        "coverage_T2",
        "coverage_T3",
        "coverage_T4_CONFIRMED",
        "coverage_POSSIBLE_T4_CANDIDATE",
        "coverage_NON_CLONE",
        "file_tags",
        "first_regions",
        "runtime_ms",
        "transformation_tag",
        "difficulty",
        "limitation_tag",
        "notes",
    ]

    output_rows = []
    with output_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            file_a = Path("evaluation") / row["file_a"]
            file_b = Path("evaluation") / row["file_b"]
            result, runtime_ms = run_pair(str(file_a), str(file_b), args.engine)
            summary = result["fileSummary"]
            # The single-label verdict fields are demoted into "legacy" (the system intentionally
            # assigns no single file-level clone type). Fall back to the top level for older JSON.
            legacy = summary.get("legacy", summary)
            counts = summary.get("regionTypeCounts", {})
            coverage = summary.get("regionTypeCoverage", {})
            output_row = {
                "project": row["project"],
                "pair_id": row["pair_id"],
                "language": row["language"],
                "expected_type": row["expected_type"],
                "type_match_status": expected_match_status(
                    row["expected_type"],
                    legacy["dominantRegionType"],
                    legacy["overallRelationship"],
                    counts,
                    summary["matchedCoverageLeft"],
                    summary["matchedCoverageRight"],
                ),
                "expected_scope": row["expected_scope"],
                "inspection_priority": summary.get("inspectionPriority", ""),
                "relationship_shape": legacy.get("relationshipShape", ""),
                "overall_relationship": legacy["overallRelationship"],
                "dominant_region_type": legacy["dominantRegionType"],
                "matched_coverage_left": summary["matchedCoverageLeft"],
                "matched_coverage_right": summary["matchedCoverageRight"],
                "unrelated_code_ratio": summary["unrelatedCodeRatio"],
                "accepted_region_count": result["acceptedRegionCount"],
                "selected_region_count": result["selectedRegionCount"],
                "suppressed_region_count": result["suppressedRegionCount"],
                "emitted_region_count": result["emittedRegionCount"],
                "count_T1": counts.get("T1", 0),
                "count_T2": counts.get("T2", 0),
                "count_T3": counts.get("T3", 0),
                "count_T4_CONFIRMED": counts.get("T4_CONFIRMED", 0),
                "count_POSSIBLE_T4_CANDIDATE": counts.get("POSSIBLE_T4_CANDIDATE", 0),
                "count_NON_CLONE": counts.get("NON_CLONE", 0),
                "coverage_T1": coverage.get("T1", 0),
                "coverage_T2": coverage.get("T2", 0),
                "coverage_T3": coverage.get("T3", 0),
                "coverage_T4_CONFIRMED": coverage.get("T4_CONFIRMED", 0),
                "coverage_POSSIBLE_T4_CANDIDATE": coverage.get("POSSIBLE_T4_CANDIDATE", 0),
                "coverage_NON_CLONE": coverage.get("NON_CLONE", 0),
                "file_tags": join_sorted(summary.get("fileTags", [])),
                "first_regions": first_regions_by_type(result.get("regions", [])),
                "runtime_ms": runtime_ms,
                "transformation_tag": row["transformation_tag"],
                "difficulty": row["difficulty"],
                "limitation_tag": row["limitation_tag"],
                "notes": row["notes"],
            }
            writer.writerow(output_row)
            output_rows.append(output_row)
            print(
                f"{row['pair_id']}: {legacy['overallRelationship']} / "
                f"{legacy['dominantRegionType']} ({runtime_ms} ms)"
            )

    write_summary(summary_path, output_path, output_rows, args.engine)
    print(f"Wrote {len(output_rows)} evaluation rows to {output_path}")
    print(f"Wrote summary to {summary_path}")


if __name__ == "__main__":
    main()
