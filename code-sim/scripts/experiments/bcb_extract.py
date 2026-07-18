#!/usr/bin/env python3
"""
BigCloneBench syntactic-subset extractor (new pipeline, region-level semantics).

Produces BatchPairMain manifests from the BigCloneEval H2 database and the
IJaDataset sample (bcb_reduced). Syntactic categories only; WT3/T4 excluded
by design (label quality; see Krinke & Ragkhitwetsagul, IWSC 2022).

Label mapping (new semantics):
    BCB type 1                          -> expected T1
    BCB type 2                          -> expected T2
    BCB type 3, similarity in [0.5, 1)  -> expected T3   (band recorded: VST3/ST3/MT3)
    FALSE_POSITIVES sample              -> expected NON_CLONE

Steps:
    1) probe    print tables/columns of the H2 db (verify schema before anything)
    2) extract  sample pairs, cut method fragments, wrap into compilable-ish
                classes, write manifest.csv + labels.csv + extraction_report.md

Usage:
    python3 scripts/experiments/bcb_extract.py probe \
        --db <path-to-db-without-.h2.db> --h2 <h2-*.jar>

    python3 scripts/experiments/bcb_extract.py extract \
        --db <db> --h2 <h2 jar> --bcb <bcb_reduced dir> --out results/bcb \
        [--per-type 0 = ALL pairs per category, or N to sample] \
        [--negatives 2000] [--seed 42]
"""

import argparse
import csv
import random
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path

BANDS = [("VST3", 0.90, 1.00), ("ST3", 0.70, 0.90), ("MT3", 0.50, 0.70)]


def h2_query(db: Path, h2_jar: Path, sql: str) -> list[list[str]]:
    """Run one SQL statement through the H2 Shell in CSV-ish mode and parse rows."""
    cmd = ["java", "-cp", str(h2_jar), "org.h2.tools.Shell",
           "-url", f"jdbc:h2:{db};IFEXISTS=TRUE;ACCESS_MODE_DATA=r",
           "-user", "sa", "-password", "", "-sql", sql]
    out = subprocess.run(cmd, capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(f"H2 query failed:\n{sql}\n{out.stderr[:2000]}")
    rows = []
    for line in out.stdout.splitlines():
        line = line.strip()
        if not line or set(line) <= {"-", "+", " "} or line.startswith("("):
            continue
        rows.append([c.strip() for c in line.split("|")])
    return rows  # first row is the header


def probe(args) -> None:
    print("== tables ==")
    for r in h2_query(args.db, args.h2, "SHOW TABLES;"):
        print(r)
    for table in ("CLONES", "FALSE_POSITIVES", "FUNCTIONS"):
        print(f"\n== columns: {table} ==")
        for r in h2_query(args.db, args.h2, f"SHOW COLUMNS FROM {table};"):
            print(r)
        print(f"\n== sample: {table} ==")
        for r in h2_query(args.db, args.h2, f"SELECT * FROM {table} LIMIT 3;")[:5]:
            print(r)


def fetch_functions(args, ids: set[str]) -> dict:
    rows = h2_query(args.db, args.h2,
                    "SELECT ID, TYPE, NAME, STARTLINE, ENDLINE FROM FUNCTIONS;")
    header, data = rows[0], rows[1:]
    funcs = {}
    for r in data:
        if len(r) >= 5 and r[0] in ids:
            funcs[r[0]] = {"dir": r[1], "file": r[2],
                           "start": int(r[3]), "end": int(r[4])}
    return funcs


def cut_fragment(bcb_root: Path, meta: dict) -> str | None:
    src = bcb_root / meta["dir"] / meta["file"]
    if not src.is_file():
        return None
    try:
        lines = src.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return None
    frag = lines[meta["start"] - 1: meta["end"]]
    return "\n".join(frag) if frag else None


def wrap(fragment: str, cls: str) -> str:
    # Method fragment -> standalone compilation unit. Static so that instance
    # state is irrelevant; unresolved references simply fail compilation, which
    # BatchPairMain records and the fallback path absorbs (compile-triage stat).
    body = re.sub(r"^\s*(public|protected|private)\s+", "public static ",
                  fragment, count=1)
    if "static" not in body.split("{")[0]:
        body = "public static " + fragment
    return f"public class {cls} {{\n{body}\n}}\n"


def extract(args) -> None:
    random.seed(args.seed)
    out = args.out
    (out / "pairs").mkdir(parents=True, exist_ok=True)

    selected = []  # (pair_id, expected, band, f1, f2)

    def sample(rows, expected, band, want):
        pool = rows if want == 0 else random.sample(rows, min(want, len(rows)))
        for k, (f1, f2) in enumerate(pool):
            selected.append((f"{expected if band == '' else band}_{k:06d}",
                             expected, band, f1, f2))

    for t in (1, 2):
        rows = [(r[0], r[1]) for r in h2_query(
            args.db, args.h2,
            f"SELECT FUNCTION_ID_ONE, FUNCTION_ID_TWO FROM CLONES "
            f"WHERE SYNTACTIC_TYPE = {t};")[1:]]
        print(f"[bcb] type {t}: {len(rows)} pairs in db")
        sample(rows, f"T{t}", "", args.per_type)

    for band, lo, hi in BANDS:
        rows = [(r[0], r[1]) for r in h2_query(
            args.db, args.h2,
            f"SELECT FUNCTION_ID_ONE, FUNCTION_ID_TWO FROM CLONES "
            f"WHERE SYNTACTIC_TYPE = 3 AND SIMILARITY_LINE >= {lo} "
            f"AND SIMILARITY_LINE < {hi};")[1:]]
        print(f"[bcb] {band}: {len(rows)} pairs in db")
        sample(rows, "T3", band, args.per_type)

    neg = [(r[0], r[1]) for r in h2_query(
        args.db, args.h2,
        "SELECT FUNCTION_ID_ONE, FUNCTION_ID_TWO FROM FALSE_POSITIVES;")[1:]]
    print(f"[bcb] false positives: {len(neg)} pairs in db")
    sample(neg, "NON_CLONE", "NEG", args.negatives)

    ids = {f for _, _, _, a, b in selected for f in (a, b)}
    funcs = fetch_functions(args, ids)
    print(f"[bcb] functions resolved: {len(funcs)}/{len(ids)}")

    stats = Counter()
    with open(out / "manifest.csv", "w", newline="", encoding="utf-8") as mf, \
         open(out / "labels.csv", "w", newline="", encoding="utf-8") as lf:
        mw, lw = csv.writer(mf), csv.writer(lf)
        mw.writerow(["pair_id", "left_path", "right_path"])
        lw.writerow(["pair_id", "expected_type", "band", "bcb_f1", "bcb_f2"])
        for pair_id, expected, band, f1, f2 in selected:
            m1, m2 = funcs.get(f1), funcs.get(f2)
            if not m1 or not m2:
                stats["missing_function_meta"] += 1
                continue
            frag1, frag2 = cut_fragment(args.bcb, m1), cut_fragment(args.bcb, m2)
            if not frag1 or not frag2:
                stats["missing_source"] += 1
                continue
            d = out / "pairs" / pair_id
            d.mkdir(exist_ok=True)
            left, right = d / "LeftInput.java", d / "RightInput.java"
            left.write_text(wrap(frag1, "LeftInput"), encoding="utf-8")
            right.write_text(wrap(frag2, "RightInput"), encoding="utf-8")
            mw.writerow([pair_id, str(left.resolve()), str(right.resolve())])
            lw.writerow([pair_id, expected, band, f1, f2])
            stats[f"written_{expected}{'_' + band if band else ''}"] += 1

    report = ["# BCB extraction report", ""]
    report += [f"- {k}: {v}" for k, v in sorted(stats.items())]
    (out / "extraction_report.md").write_text("\n".join(report) + "\n", encoding="utf-8")
    print("\n".join(report))


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    for name in ("probe", "extract"):
        p = sub.add_parser(name)
        p.add_argument("--db", required=True, type=Path)
        p.add_argument("--h2", required=True, type=Path)
        if name == "extract":
            p.add_argument("--bcb", required=True, type=Path)
            p.add_argument("--out", required=True, type=Path)
            p.add_argument("--per-type", type=int, default=0,
                           help="0 = ALL pairs per syntactic category")
            p.add_argument("--negatives", type=int, default=2000)
            p.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()
    (probe if args.cmd == "probe" else extract)(args)


if __name__ == "__main__":
    main()
