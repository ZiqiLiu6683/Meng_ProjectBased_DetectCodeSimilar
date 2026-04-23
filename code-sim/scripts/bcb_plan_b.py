#!/usr/bin/env python3
"""
BCB Plan-B evaluator
Queries the BigCloneBench H2 database for method-level clone pairs,
extracts the exact method lines from era_bcb_sample, wraps each fragment
in a synthetic Java class, runs AstMain on the pair, and outputs results.

Usage:
    python3 scripts/bcb_plan_b.py \
        --db   ~/BigCloneEval/bigclonebenchdb/bcb \
        --bcb  ~/Documents/西大Graduate/Project_base/测试数据集/era_bcb_sample \
        --h2   ~/BigCloneEval/libs/h2-1.3.176.jar \
        --tool ~/Documents/西大Graduate/Project_base/Code/Meng_ProjectBased_DetectCodeSimilar/code-sim \
        --out  results/bcb_plan_b.csv
"""

import argparse, csv, os, re, shutil, subprocess, sys, tempfile
from pathlib import Path

# ── how many pairs to sample per syntactic type ──────────────────────────────
SAMPLES = {
    1: 20,   # Type-1 (exact)
    2: 20,   # Type-2 (renamed)
    "3_strong":  20,   # Type-3 high sim  (token sim >= 0.7)
    "3_medium":  20,   # Type-3 mid sim   (0.5 <= sim < 0.7)
    "3_weak":    20,   # Type-3/4 low sim (sim < 0.5)
}
NON_CLONE_PAIRS = 20
MAIN_CLASS = "com.ziqi.codesim.ast.AstMain"
# ─────────────────────────────────────────────────────────────────────────────


# ── H2 query helper ───────────────────────────────────────────────────────────
def h2_query(h2_jar: Path, db_url: str, sql: str) -> list[dict]:
    """Run a SQL statement via H2 Shell and parse the tabular output."""
    cmd = [
        "java", "-cp", str(h2_jar),
        "org.h2.tools.Shell",
        "-url", db_url,
        "-user", "sa", "-password", "",
        "-sql", sql,
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
    lines = result.stdout.strip().splitlines()
    rows = []
    if len(lines) < 2:
        return rows
    # First line is header; separator is " | "
    headers = [h.strip() for h in lines[0].split("|")]
    for line in lines[1:]:
        if line.startswith("(") or not line.strip():
            continue
        cells = [c.strip() for c in line.split("|")]
        if len(cells) == len(headers):
            rows.append(dict(zip(headers, cells)))
    return rows


# ── file resolution ───────────────────────────────────────────────────────────
def resolve_file(bcb_root: Path, functionality_id: str,
                 ftype: str, name: str) -> Path | None:
    p = bcb_root / functionality_id / ftype / name
    return p if p.exists() else None


# ── method extraction + class wrapper ────────────────────────────────────────
def extract_fragment(src_file: Path, start: int, end: int) -> str | None:
    """
    Extract lines [start, end] (1-based, inclusive) from src_file.
    Prepend all import statements found at the top of the file so
    JavaParser can resolve types.
    """
    try:
        with open(src_file, encoding="utf-8", errors="replace") as f:
            all_lines = f.readlines()
    except OSError:
        return None
    if start < 1 or end > len(all_lines):
        return None

    imports = [l for l in all_lines[:max(start - 1, 0)]
               if l.strip().startswith("import ") or l.strip().startswith("package ")]
    method_lines = all_lines[start - 1:end]
    body = "".join(method_lines)

    return (
        "".join(imports) +
        "\npublic class Fragment {\n" +
        body +
        "\n}\n"
    )


# ── run AstMain ───────────────────────────────────────────────────────────────
def run_tool(tool_dir: Path, fa: Path, fb: Path) -> dict | None:
    cmd = [
        "mvn", "-q", "exec:java",
        f"-Dexec.mainClass={MAIN_CLASS}",
        f"-Dexec.args={fa} {fb}",
    ]
    try:
        r = subprocess.run(cmd, cwd=tool_dir,
                           capture_output=True, text=True, timeout=120)
        return parse_output(r.stdout)
    except Exception:
        return None


def parse_output(stdout: str) -> dict:
    scores = {}
    patterns = {
        "S1": r"S1 AST-token Winnowing.*?:\s*([\d.]+)%",
        "S2": r"S2 Exact Subtree.*?:\s*([\d.]+)%",
        "S3": r"S3 Method-level.*?:\s*([\d.]+)%",
        "S4": r"S4 Method-level TED.*?:\s*([\d.]+)%",
        "S5": r"S5 API Call.*?:\s*([\d.]+)",
        "Combined": r"Combined Score.*?:\s*([\d.]+)%",
    }
    for k, pat in patterns.items():
        m = re.search(pat, stdout)
        scores[k] = float(m.group(1)) if m else None
    return scores


# ── main ──────────────────────────────────────────────────────────────────────
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db",   required=True, help="H2 DB path prefix (no .h2.db)")
    ap.add_argument("--bcb",  required=True, help="era_bcb_sample root")
    ap.add_argument("--h2",   required=True, help="h2 jar path")
    ap.add_argument("--tool", required=True, help="code-sim root (pom.xml)")
    ap.add_argument("--out",  default="bcb_plan_b.csv")
    args = ap.parse_args()

    db_url   = f"jdbc:h2:{Path(args.db).expanduser().resolve()}"
    bcb_root = Path(args.bcb).expanduser().resolve()
    h2_jar   = Path(args.h2).expanduser().resolve()
    tool_dir = Path(args.tool).expanduser().resolve()
    out_path = Path(args.out).expanduser()
    out_path.parent.mkdir(parents=True, exist_ok=True)

    tmpdir = Path(tempfile.mkdtemp(prefix="bcb_plan_b_"))
    print(f"Temp dir: {tmpdir}")

    # ── build SQL queries for each category ──────────────────────────────────
    base_sql = """
        SELECT TOP {n}
          f1.NAME as n1, f1.TYPE as t1, f1.STARTLINE as s1, f1.ENDLINE as e1,
          f2.NAME as n2, f2.TYPE as t2, f2.STARTLINE as s2, f2.ENDLINE as e2,
          c.FUNCTIONALITY_ID as fid,
          c.SYNTACTIC_TYPE as stype,
          c.SIMILARITY_TOKEN as simtok
        FROM CLONES c
        JOIN FUNCTIONS f1 ON c.FUNCTION_ID_ONE  = f1.ID
        JOIN FUNCTIONS f2 ON c.FUNCTION_ID_TWO  = f2.ID
        WHERE c.SYNTACTIC_TYPE = {stype}
          AND f1.TYPE = 'sample'
          AND f2.TYPE = 'sample'
          {extra}
        ORDER BY RAND()
    """

    queries = {
        "T1":        base_sql.format(n=SAMPLES[1],             stype=1, extra=""),
        "T2":        base_sql.format(n=SAMPLES[2],             stype=2, extra=""),
        "T3_strong": base_sql.format(n=SAMPLES["3_strong"],    stype=3,
                                     extra="AND c.SIMILARITY_TOKEN >= 0.7"),
        "T3_medium": base_sql.format(n=SAMPLES["3_medium"],    stype=3,
                                     extra="AND c.SIMILARITY_TOKEN >= 0.5 AND c.SIMILARITY_TOKEN < 0.7"),
        "T3_weak":   base_sql.format(n=SAMPLES["3_weak"],      stype=3,
                                     extra="AND c.SIMILARITY_TOKEN < 0.5"),
    }

    all_pairs = []

    # ── fetch clone pairs ─────────────────────────────────────────────────────
    for label, sql in queries.items():
        print(f"\nQuerying {label} pairs...", flush=True)
        rows = h2_query(h2_jar, db_url, sql.strip())
        print(f"  Got {len(rows)} rows from DB")
        for r in rows:
            all_pairs.append({
                "label": label,
                "fid": r.get("FID", "?"),
                "stype": r.get("STYPE", "?"),
                "simtok": r.get("SIMTOK", "?"),
                "n1": r["N1"], "t1": r["T1"], "s1": int(r["S1"]), "e1": int(r["E1"]),
                "n2": r["N2"], "t2": r["T2"], "s2": int(r["S2"]), "e2": int(r["E2"]),
            })

    # ── build non-clone pairs from filesystem (no expensive DB scan) ──────────
    # Files in DIFFERENT functionality folders are guaranteed non-clones.
    # We look up their line ranges individually with cheap single-row queries.
    print("\nBuilding non-clone pairs from filesystem...", flush=True)
    import random as _random
    _random.seed(99)

    # Collect one sample file per functionality folder
    func_sample_files: dict[str, list[Path]] = {}
    for func_dir in sorted(bcb_root.iterdir()):
        if not func_dir.is_dir():
            continue
        sample_dir = func_dir / "sample"
        if sample_dir.exists():
            files = list(sample_dir.glob("*.java"))
            if files:
                func_sample_files[func_dir.name] = files

    func_ids = list(func_sample_files.keys())
    nc_added = 0
    attempts = 0
    while nc_added < NON_CLONE_PAIRS and attempts < 200:
        attempts += 1
        fid_a, fid_b = _random.sample(func_ids, 2)
        f_a = _random.choice(func_sample_files[fid_a])
        f_b = _random.choice(func_sample_files[fid_b])

        # Look up line ranges for each file individually (fast single-row query)
        sql_lookup = (
            f"SELECT TOP 1 STARTLINE as s, ENDLINE as e "
            f"FROM FUNCTIONS WHERE NAME='{f_a.name}' AND TYPE='sample'"
        )
        rows_a = h2_query(h2_jar, db_url, sql_lookup)
        sql_lookup = (
            f"SELECT TOP 1 STARTLINE as s, ENDLINE as e "
            f"FROM FUNCTIONS WHERE NAME='{f_b.name}' AND TYPE='sample'"
        )
        rows_b = h2_query(h2_jar, db_url, sql_lookup)
        if not rows_a or not rows_b:
            continue

        all_pairs.append({
            "label": "non-clone",
            "fid": f"{fid_a}/{fid_b}",
            "stype": "0", "simtok": "0",
            "n1": f_a.name, "t1": "sample",
            "s1": int(rows_a[0]["S"]), "e1": int(rows_a[0]["E"]),
            "n2": f_b.name, "t2": "sample",
            "s2": int(rows_b[0]["S"]), "e2": int(rows_b[0]["E"]),
            "_fid1": fid_a, "_fid2": fid_b,
        })
        nc_added += 1
    print(f"  Got {nc_added} non-clone pairs")

    print(f"\nTotal pairs to evaluate: {len(all_pairs)}")

    # ── run tool on each pair ─────────────────────────────────────────────────
    fieldnames = ["label", "fid", "stype", "simtok",
                  "file1", "file2", "S1", "S2", "S3", "S4", "S5", "Combined"]
    rows_out = []

    for idx, p in enumerate(all_pairs, 1):
        fid = p.get("_fid1", p["fid"]) if p["label"] == "non-clone" else p["fid"]
        fid2 = p.get("_fid2", p["fid"]) if p["label"] == "non-clone" else p["fid"]

        src1 = resolve_file(bcb_root, fid,  p["t1"], p["n1"])
        src2 = resolve_file(bcb_root, fid2, p["t2"], p["n2"])

        if src1 is None or src2 is None:
            print(f"[{idx:3}/{len(all_pairs)}] SKIP (file not found): "
                  f"{p['n1']} or {p['n2']}")
            continue

        frag1 = extract_fragment(src1, p["s1"], p["e1"])
        frag2 = extract_fragment(src2, p["s2"], p["e2"])
        if not frag1 or not frag2:
            print(f"[{idx:3}/{len(all_pairs)}] SKIP (extraction failed)")
            continue

        tmp1 = tmpdir / f"frag_{idx}_A.java"
        tmp2 = tmpdir / f"frag_{idx}_B.java"
        tmp1.write_text(frag1, encoding="utf-8")
        tmp2.write_text(frag2, encoding="utf-8")

        print(f"[{idx:3}/{len(all_pairs)}] {p['label']:10} "
              f"stype={p['stype']} sim={p['simtok'][:5] if p['simtok'] != '0' else '—':5}  "
              f"{p['n1']} vs {p['n2']} ...", end=" ", flush=True)

        scores = run_tool(tool_dir, tmp1, tmp2)
        if scores is None:
            print("FAILED")
            continue

        comb = scores.get("Combined")
        print(f"Combined={'%.1f%%' % comb if comb is not None else 'N/A'}")

        rows_out.append({
            "label":    p["label"],
            "fid":      p["fid"],
            "stype":    p["stype"],
            "simtok":   p["simtok"],
            "file1":    p["n1"],
            "file2":    p["n2"],
            **{k: (f"{v:.2f}" if v is not None else "N/A")
               for k, v in scores.items()},
        })

    # ── cleanup + write CSV ───────────────────────────────────────────────────
    shutil.rmtree(tmpdir, ignore_errors=True)

    with open(out_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows_out)

    print(f"\nResults saved → {out_path}")

    # ── summary by label ──────────────────────────────────────────────────────
    from collections import defaultdict
    by_label = defaultdict(list)
    for r in rows_out:
        if r["Combined"] != "N/A":
            by_label[r["label"]].append(float(r["Combined"]))

    print("\n=== Combined Score Summary ===")
    for lbl in ["T1", "T2", "T3_strong", "T3_medium", "T3_weak", "non-clone"]:
        vals = by_label.get(lbl, [])
        if vals:
            print(f"  {lbl:12} n={len(vals):3}  "
                  f"avg={sum(vals)/len(vals):.1f}%  "
                  f"min={min(vals):.1f}%  max={max(vals):.1f}%")


if __name__ == "__main__":
    main()
