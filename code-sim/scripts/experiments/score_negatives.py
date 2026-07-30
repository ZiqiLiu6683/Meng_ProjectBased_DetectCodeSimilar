#!/usr/bin/env python3
"""
Negative-stratum scoring, per protocol §4.1.

§4.1 requires TWO false-positive definitions because they answer different questions, and forbids
collapsing them into one number:

1. **Reference-range false positive** -- a predicted clone covers at least 70 % of both negative
   reference ranges. A generated negative pair has no annotated sub-range, so the reference range is
   the whole file on each side. This is the BigCloneEval-style reading: did the system claim these
   two programs correspond?
2. **Strict product false positive** -- the system emits any clone region meeting the preregistered
   minimum clone size. This is what a user of the product actually sees, and it can legitimately
   count a real small clone inside a pair labelled non-clone at whole-file level. §4.1 therefore
   requires a stratified audit alongside it, which `--audit` writes out.

A third cut is reported because the pilot showed it dominates and the two definitions above hide it:
whether the emitted region claims a SYNTACTIC type (T1/T2/T3) or only `POSSIBLE_T4_CANDIDATE`. The
latter is the recognizer refusing to drop a structural fact Phase A established, not a claim that
the programs are clones, and reporting them as one number would misstate what the system said.

Combined metrics (specificity, FPR, balanced accuracy, MCC, precision) need the positive stratum
too; pass `--positives`. Precision is reported at explicitly stated prevalences, never from the
corpus mixture, because a 5,000/1,000 split is an artefact of how much compute was spent and not an
estimate of how often real submissions are clones.

Usage:
  score_negatives.py --results .../negatives/run/merged.jsonl --manifest .../manifest_raw.csv \
      [--positives .../batch1/run/merged.jsonl,...] [--min-region-lines 6] [--out DIR]
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import statistics
from collections import Counter, defaultdict
from pathlib import Path

SYNTACTIC = ("T1", "T2", "T3")


def region_lines(endpoint: dict) -> set[int]:
    """A region's real content: its runs when present, else the bounding box.

    Same definition as `score_region_corpus.py`, deliberately -- two scorers disagreeing on what a
    region covers would make the positive and negative tables incomparable.
    """
    segments = endpoint.get("segments")
    if segments:
        out: set[int] = set()
        for segment in segments:
            out.update(range(int(segment["begin"]), int(segment["end"]) + 1))
        return out
    begin, end = endpoint.get("beginLine"), endpoint.get("endLine")
    if begin is None or end is None:
        return set()
    return set(range(int(begin), int(end) + 1))


def file_line_count(path: str) -> int:
    try:
        return len(Path(path).read_text(errors="replace").splitlines())
    except OSError:
        return 0


def load(results: Path) -> dict[str, dict]:
    out: dict[str, dict] = {}
    for line in results.read_text(errors="replace").splitlines():
        if line.startswith('{"schemaVersion"'):
            record = json.loads(line)
            out[record["pairId"]] = record
    return out


def classify(record: dict, left_lines: int, right_lines: int,
             min_region_lines: int) -> dict:
    """Both §4.1 verdicts for one pair, plus what the emission actually claimed."""
    regions = (record.get("report") or {}).get("regions") or []
    emitted = []
    for region in regions:
        left = region_lines(region.get("left") or {})
        right = region_lines(region.get("right") or {})
        emitted.append((region.get("type") or "", left, right))

    # Definition 2: any region meeting the preregistered minimum size on its smaller side.
    strict = [r for r in emitted if min(len(r[1]), len(r[2])) >= min_region_lines]

    # Definition 1: a region covering >= 70 % of BOTH whole files.
    reference_range = False
    for _, left, right in emitted:
        if left_lines and right_lines and (len(left) / left_lines >= 0.70
                                           and len(right) / right_lines >= 0.70):
            reference_range = True
            break

    syntactic = [r for r in strict if r[0] in SYNTACTIC]
    return {
        "pair_id": record["pairId"],
        "status": record.get("status"),
        "analysis_mode": record.get("analysisMode"),
        "regions_emitted": len(emitted),
        "regions_at_min_size": len(strict),
        "fp_reference_range": reference_range,
        "fp_strict_product": bool(strict),
        "claims_syntactic_type": bool(syntactic),
        "types": "|".join(sorted({r[0] for r in strict})),
        "largest_region_lines": max((min(len(r[1]), len(r[2])) for r in emitted), default=0),
    }


def wilson(hits: int, total: int, deff: float = 1.0) -> tuple[float, float]:
    if total == 0:
        return 0.0, 0.0
    effective = total / max(deff, 1e-9)
    z = 1.96
    p = hits / total
    denom = 1 + z * z / effective
    centre = (p + z * z / (2 * effective)) / denom
    half = z * math.sqrt(p * (1 - p) / effective + z * z / (4 * effective * effective)) / denom
    return max(0.0, centre - half) * 100, min(1.0, centre + half) * 100


def icc_oneway(groups: dict[str, list[float]]) -> tuple[float, float]:
    clusters = [v for v in groups.values() if len(v) > 1]
    if not clusters:
        return 0.0, 1.0
    total = sum(len(v) for v in clusters)
    count = len(clusters)
    grand = sum(sum(v) for v in clusters) / total
    between = sum(len(v) * (sum(v) / len(v) - grand) ** 2 for v in clusters) / max(count - 1, 1)
    within = sum(sum((x - sum(v) / len(v)) ** 2 for x in v) for v in clusters) / max(total - count, 1)
    mean_size = total / count
    denom = between + (mean_size - 1) * within
    return (max(0.0, (between - within) / denom) if denom > 0 else 0.0), mean_size


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--results", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--positives", default="")
    parser.add_argument("--min-region-lines", type=int, default=6)
    parser.add_argument("--out", type=Path)
    parser.add_argument("--audit", type=int, default=20,
                        help="rows per stratum written to audit_sample.csv (§4.1 requires one)")
    args = parser.parse_args()

    manifest = {r["pair_id"]: r for r in csv.DictReader(args.manifest.open())}
    records = load(args.results)
    missing = sorted(set(manifest) - set(records))

    rows = []
    for pair_id, meta in manifest.items():
        record = records.get(pair_id)
        if record is None:
            continue
        row = classify(record, file_line_count(meta["left_path"]),
                       file_line_count(meta["right_path"]), args.min_region_lines)
        row["left_problem"] = meta.get("left_problem", "")
        row["right_problem"] = meta.get("right_problem", "")
        row["left_program"] = meta.get("left_program", "")
        row["right_program"] = meta.get("right_program", "")
        row["shared_tokens"] = meta.get("shared_tokens", "")
        rows.append(row)

    n = len(rows)
    if not n:
        raise SystemExit("no scored negatives")

    # Clustering by problem, as §7 requires. A negative pair belongs to two problems, so it is
    # attributed to the pair of them -- attributing it to one side would understate the dependence.
    by_problem: dict[str, list[float]] = defaultdict(list)
    for row in rows:
        key = "|".join(sorted((row["left_problem"], row["right_problem"])))
        by_problem[key].append(0.0 if row["fp_strict_product"] else 1.0)
    rho, mean_size = icc_oneway(by_problem)
    deff = 1 + (mean_size - 1) * rho

    fp_ref = sum(1 for r in rows if r["fp_reference_range"])
    fp_strict = sum(1 for r in rows if r["fp_strict_product"])
    fp_syn = sum(1 for r in rows if r["claims_syntactic_type"])

    out: list[str] = [f"# Negative stratum — {n} pairs", "",
                      f"From `{args.results}`. "
                      + (f"**{len(missing)} manifest pairs missing from the results.**"
                         if missing else "Every manifest pair has a result."), "",
                      f"Clustered by problem pair: ICC {rho:.4f}, mean cluster {mean_size:.1f}, "
                      f"DEFF {deff:.2f}.", "",
                      "## The two §4.1 definitions, reported separately as required", "",
                      "| Definition | FP | Rate | Specificity | 95 % CI on specificity |",
                      "| --- | ---: | ---: | ---: | --- |"]
    for label, count in (("reference-range (covers ≥ 70 % of both files)", fp_ref),
                         (f"strict product (any region ≥ {args.min_region_lines} lines)", fp_strict),
                         ("— of which claim a syntactic type T1/T2/T3", fp_syn)):
        lo, hi = wilson(n - count, n, deff)
        out.append(f"| {label} | {count} | {count / n * 100:.1f} % | "
                   f"{(n - count) / n * 100:.1f} % | [{lo:.1f}, {hi:.1f}] |")

    types = Counter(t for r in rows if r["fp_strict_product"] for t in r["types"].split("|") if t)
    out += ["", "Types claimed by the emitted regions: "
            + (", ".join(f"`{t}` {c}" for t, c in types.most_common()) or "none"), ""]

    sizes = sorted(r["largest_region_lines"] for r in rows if r["fp_strict_product"])
    if sizes:
        out += [f"Largest emitted region (min side), over the {len(sizes)} strict-FP pairs: "
                f"median {statistics.median(sizes):.0f} lines, p90 "
                f"{sizes[int(len(sizes) * 0.9)]:.0f}, max {sizes[-1]}.", ""]

    positives = [p.strip() for p in args.positives.split(",") if p.strip()]
    if positives:
        tp = fn = 0
        for path in positives:
            for record in load(Path(path)).values():
                regions = (record.get("report") or {}).get("regions") or []
                hit = any(min(len(region_lines(r.get("left") or {})),
                              len(region_lines(r.get("right") or {}))) >= args.min_region_lines
                          for r in regions)
                tp, fn = (tp + 1, fn) if hit else (tp, fn + 1)
        tn, fp = n - fp_strict, fp_strict
        sensitivity = tp / (tp + fn) if tp + fn else 0.0
        specificity = tn / (tn + fp) if tn + fp else 0.0
        denom = math.sqrt((tp + fp) * (tp + fn) * (tn + fp) * (tn + fn))
        mcc = ((tp * tn - fp * fn) / denom) if denom else 0.0
        out += ["## Combined, on the strict product definition", "",
                f"| | |", "| --- | ---: |",
                f"| true positives | {tp} |", f"| false negatives | {fn} |",
                f"| true negatives | {tn} |", f"| false positives | {fp} |",
                f"| sensitivity (recall) | {sensitivity * 100:.1f} % |",
                f"| specificity | {specificity * 100:.1f} % |",
                f"| balanced accuracy | {(sensitivity + specificity) / 2 * 100:.1f} % |",
                f"| MCC | {mcc:.3f} |", "",
                "### Precision at stated clone prevalences", "",
                "**Not** the precision of this corpus. §4.1 forbids reporting an artificial "
                "positive/negative mixture as population precision — the 5,000/1,000 split reflects "
                "how much compute was spent, not how often submissions are clones.", "",
                "| Prevalence | Precision |", "| ---: | ---: |"]
        for prevalence in (0.01, 0.05, 0.10, 0.25, 0.50):
            num = sensitivity * prevalence
            den = num + (1 - specificity) * (1 - prevalence)
            out.append(f"| {prevalence * 100:.0f} % | {(num / den * 100 if den else 0):.1f} % |")
        out.append("")

    target = args.out or args.results.parent.parent / "scored"
    target.mkdir(parents=True, exist_ok=True)
    with (target / "scored_negatives.csv").open("w", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    # §4.1 requires a stratified audit, so the sample is stratified by what the system said rather
    # than drawn at random -- a random sample of a mostly-clean stratum would show mostly clean.
    strata = {
        "claims syntactic type": [r for r in rows if r["claims_syntactic_type"]],
        "emits region, no syntactic claim": [r for r in rows if r["fp_strict_product"]
                                             and not r["claims_syntactic_type"]],
        "covers >= 70 % of both files": [r for r in rows if r["fp_reference_range"]],
        "clean": [r for r in rows if not r["fp_strict_product"]],
    }
    audit = []
    for name, subset in strata.items():
        for row in sorted(subset, key=lambda r: -r["largest_region_lines"])[:args.audit]:
            audit.append({"stratum": name, **row})
    if audit:
        with (target / "audit_sample.csv").open("w", newline="") as fh:
            writer = csv.DictWriter(fh, fieldnames=list(audit[0].keys()))
            writer.writeheader()
            writer.writerows(audit)
        out += ["## Audit sample", "",
                "`audit_sample.csv` holds the largest cases from each stratum "
                + ", ".join(f"{k} ({len(v)})" for k, v in strata.items())
                + ". Largest rather than random: §4.1 wants the audit to test whether a strict "
                  "false positive is a real small clone, and the biggest emissions are where that "
                  "question has teeth.", ""]

    (target / "SUMMARY.md").write_text("\n".join(out) + "\n", encoding="utf-8")
    print("\n".join(out))
    print(f"written to {target}")


if __name__ == "__main__":
    main()
