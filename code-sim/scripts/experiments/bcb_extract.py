#!/usr/bin/env python3
"""
BigCloneBench syntactic-subset extractor (BCEval V2 schema, verified 2026-07-19).

Categories and label mapping (region-level semantics):
    SYNTACTIC_TYPE = 1                          -> expected T1
    SYNTACTIC_TYPE = 2                          -> expected T2
    SYNTACTIC_TYPE = 3, BOTH-sim in [0.5, 1.0)  -> expected T3  (band: VST3/ST3/MT3)
    FALSE_POSITIVES                             -> expected NON_CLONE
BOTH-sim = LEAST(SIMILARITY_LINE, SIMILARITY_TOKEN), the official BigCloneEval
recommendation. WT3/T4 (BOTH-sim < 0.5) excluded by design (label quality;
Krinke & Ragkhitwetsagul, IWSC 2022). Official recommended minimum reference
clone sizes applied: MIN_SIZE >= 6, MIN_PRETTY_SIZE >= 6, MIN_TOKENS >= 50.

Source resolution: bcb_reduced/<FUNCTIONALITY_ID>/<FUNCTIONS.TYPE>/<FUNCTIONS.NAME>,
lines STARTLINE..ENDLINE.

Big queries are exported db-side via CSVWRITE (no multi-million-row piping).

Usage:
    py scripts/experiments/bcb_extract.py probe   --db <db> --h2 <h2 jar>
    py scripts/experiments/bcb_extract.py extract --db <db> --h2 <h2 jar> \
        --bcb <bcb_reduced dir> --out results/bcb_full \
        [--per-type 0 = ALL, or N] [--negatives 2000] [--seed 42]
"""

import argparse
import csv
import random
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path

SIZE_FILTER = "MIN_SIZE >= 6 AND MIN_PRETTY_SIZE >= 6 AND MIN_TOKENS >= 50"
BANDS = [("VST3", 0.90, 1.00), ("ST3", 0.70, 0.90), ("MT3", 0.50, 0.70)]

CLONE_SELECT = ("SELECT c.FUNCTION_ID_ONE, c.FUNCTION_ID_TWO, c.FUNCTIONALITY_ID, "
                "LEAST(c.SIMILARITY_LINE, c.SIMILARITY_TOKEN) AS SIM_BOTH, "
                "f1.NAME AS NAME1, f1.TYPE AS TYPE1, f1.STARTLINE AS S1, f1.ENDLINE AS E1, "
                "f2.NAME AS NAME2, f2.TYPE AS TYPE2, f2.STARTLINE AS S2, f2.ENDLINE AS E2 "
                "FROM {table} c "
                "JOIN FUNCTIONS f1 ON c.FUNCTION_ID_ONE = f1.ID "
                "JOIN FUNCTIONS f2 ON c.FUNCTION_ID_TWO = f2.ID "
                "WHERE {where}")


def run_h2(db: Path, h2_jar: Path, sql: str) -> str:
    db_url = f"jdbc:h2:{str(db).replace(chr(92), '/')};IFEXISTS=TRUE"
    cmd = ["java", "-Xmx2g", "-cp", str(h2_jar), "org.h2.tools.Shell",
           "-url", db_url, "-user", "sa", "-password", "", "-sql", sql]
    out = subprocess.run(cmd, capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(f"H2 query failed:\n{sql}\n{out.stderr[:2000]}")
    return out.stdout


def h2_rows(db: Path, h2_jar: Path, sql: str) -> list[list[str]]:
    rows = []
    for line in run_h2(db, h2_jar, sql).splitlines():
        line = line.strip()
        if not line or set(line) <= {"-", "+", " "} or line.startswith("("):
            continue
        rows.append([c.strip() for c in line.split("|")])
    return rows


def csv_export(db: Path, h2_jar: Path, select_sql: str, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    tpath = str(target.resolve()).replace(chr(92), "/")
    inner = select_sql.replace("'", "''")
    run_h2(db, h2_jar, f"CALL CSVWRITE('{tpath}', '{inner}');")
    if not target.is_file():
        sys.exit(f"CSVWRITE produced no file: {target}")


def probe(args) -> None:
    print("== tables ==")
    for r in h2_rows(args.db, args.h2, "SHOW TABLES;"):
        print(r)
    for table in ("CLONES", "FALSE_POSITIVES", "FUNCTIONS"):
        print(f"\n== columns: {table} ==")
        for r in h2_rows(args.db, args.h2, f"SHOW COLUMNS FROM {table};"):
            print(r)
        print(f"\n== sample: {table} ==")
        for r in h2_rows(args.db, args.h2, f"SELECT * FROM {table} LIMIT 3;")[:5]:
            print(r)


def category_queries() -> list[tuple[str, str, str]]:
    """(category_key, expected_type, WHERE clause) per extraction category."""
    cats = [("T1", "T1", f"c.SYNTACTIC_TYPE = 1 AND {SIZE_FILTER}"),
            ("T2", "T2", f"c.SYNTACTIC_TYPE = 2 AND {SIZE_FILTER}")]
    for band, lo, hi in BANDS:
        cats.append((band, "T3",
                     f"c.SYNTACTIC_TYPE = 3 AND {SIZE_FILTER} AND "
                     f"LEAST(c.SIMILARITY_LINE, c.SIMILARITY_TOKEN) >= {lo} AND "
                     f"LEAST(c.SIMILARITY_LINE, c.SIMILARITY_TOKEN) < {hi}"))
    # FALSE_POSITIVES has no size columns: negatives are taken as-is.
    cats.append(("NEG", "NON_CLONE", "1 = 1"))
    return cats


def cut_fragment(bcb_root: Path, fid: str, subdir: str, name: str,
                 start: int, end: int) -> str | None:
    src = bcb_root / fid / subdir / name
    if not src.is_file():
        return None
    try:
        lines = src.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return None
    frag = lines[start - 1: end]
    return "\n".join(frag) if frag else None


COMMON_IMPORTS = "\n".join([
    "import java.util.*;",
    "import java.io.*;",
    "import java.net.*;",
    "import java.nio.file.*;",
    "import java.nio.channels.*;",
    "import java.text.*;",
    "import java.math.*;",
    "import java.util.regex.*;",
    "import java.util.concurrent.*;",
    "import java.util.zip.*;",
    "import java.security.*;",
])


def wrap(fragment: str, cls: str) -> str:
    # Fragment kept verbatim (its own modifiers are legal inside a class);
    # common JDK imports raise the standalone-compile rate, unused ones are free.
    # java.sql.* / java.awt.* deliberately omitted (Date/List ambiguity with java.util.*).
    return f"{COMMON_IMPORTS}\n\npublic class {cls} {{\n{fragment}\n}}\n"


def extract(args) -> None:
    random.seed(args.seed)
    out = args.out
    (out / "pairs").mkdir(parents=True, exist_ok=True)
    (out / "raw").mkdir(parents=True, exist_ok=True)

    stats = Counter()
    with open(out / "manifest.csv", "w", newline="", encoding="utf-8") as mf, \
         open(out / "labels.csv", "w", newline="", encoding="utf-8") as lf:
        mw, lw = csv.writer(mf), csv.writer(lf)
        mw.writerow(["pair_id", "left_path", "right_path"])
        lw.writerow(["pair_id", "expected_type", "band", "functionality_id",
                     "sim_both", "bcb_f1", "bcb_f2"])

        for cat, expected, where in category_queries():
            table = "FALSE_POSITIVES" if cat == "NEG" else "CLONES"
            raw_csv = out / "raw" / f"{cat}.csv"
            if not raw_csv.is_file():  # resume-safe: reuse a previous export
                print(f"[bcb] exporting {cat} ...")
                csv_export(args.db, args.h2,
                           CLONE_SELECT.format(table=table, where=where), raw_csv)
            rows = list(csv.DictReader(open(raw_csv, encoding="utf-8")))
            print(f"[bcb] {cat}: {len(rows)} pairs in db")
            want = args.negatives if cat == "NEG" else args.per_type
            if want and len(rows) > want:
                rows = random.sample(rows, want)

            for k, r in enumerate(rows):
                pair_id = f"{cat}_{k:06d}"
                frag1 = cut_fragment(args.bcb, r["FUNCTIONALITY_ID"], r["TYPE1"],
                                     r["NAME1"], int(r["S1"]), int(r["E1"]))
                frag2 = cut_fragment(args.bcb, r["FUNCTIONALITY_ID"], r["TYPE2"],
                                     r["NAME2"], int(r["S2"]), int(r["E2"]))
                if not frag1 or not frag2:
                    stats[f"{cat}_missing_source"] += 1
                    continue
                d = out / "pairs" / pair_id
                d.mkdir(exist_ok=True)
                left, right = d / "LeftInput.java", d / "RightInput.java"
                left.write_text(wrap(frag1, "LeftInput"), encoding="utf-8")
                right.write_text(wrap(frag2, "RightInput"), encoding="utf-8")
                mw.writerow([pair_id, str(left.resolve()), str(right.resolve())])
                lw.writerow([pair_id, expected, cat if expected == "T3" else "",
                             r["FUNCTIONALITY_ID"], r["SIM_BOTH"],
                             r["FUNCTION_ID_ONE"], r["FUNCTION_ID_TWO"]])
                stats[f"{cat}_written"] += 1

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
                           help="0 = ALL pairs per category")
            p.add_argument("--negatives", type=int, default=2000)
            p.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()
    (probe if args.cmd == "probe" else extract)(args)


if __name__ == "__main__":
    main()
