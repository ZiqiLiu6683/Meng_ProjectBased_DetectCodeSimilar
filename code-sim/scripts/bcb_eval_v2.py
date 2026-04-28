#!/usr/bin/env python3
"""
BCB Evaluator v2
Expanded evaluation with Precision / Recall / F1 at multiple thresholds,
per-type recall, and approximate AUC.

Outputs:
  <out_dir>/bcb_v2_pairs.csv      — one row per pair with all scores
  <out_dir>/bcb_v2_metrics.csv    — P/R/F1 at each threshold
  <out_dir>/bcb_v2_pertype.csv    — per-type recall at every threshold

Usage:
    python3 scripts/bcb_eval_v2.py \
        --db   ~/BigCloneEval/bigclonebenchdb/bcb \
        --bcb  ~/Documents/西大Graduate/Project_base/测试数据集/era_bcb_sample \
        --h2   ~/BigCloneEval/libs/h2-1.3.176.jar \
        --tool ~/Documents/西大Graduate/Project_base/Code/Meng_ProjectBased_DetectCodeSimilar/code-sim \
        --out  results/v2
"""

import argparse, csv, re, random, shutil, subprocess, sys, tempfile
from collections import defaultdict
from pathlib import Path

# ── configuration ─────────────────────────────────────────────────────────────
SAMPLES = {
    1:           50,    # Type-1  exact clones
    2:           50,    # Type-2  renamed clones
    "3_strong":  80,    # Type-3  token-sim >= 0.7
    "3_medium":  80,    # Type-3  0.5 <= token-sim < 0.7
    "3_weak":    80,    # Type-3/4 token-sim < 0.5
}
NON_CLONE_PAIRS = 150
# Thresholds to sweep (Combined Score as percentage)
THRESHOLDS = list(range(5, 96, 5))   # 5, 10, 15, … 95
MAIN_CLASS  = "com.ziqi.codesim.ast.AstMain"
RANDOM_SEED = 42
# ─────────────────────────────────────────────────────────────────────────────


# ── H2 query helper ───────────────────────────────────────────────────────────
def h2_query(h2_jar: Path, db_url: str, sql: str) -> list[dict]:
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
    headers = [h.strip() for h in lines[0].split("|")]
    for line in lines[1:]:
        if line.startswith("(") or not line.strip():
            continue
        cells = [c.strip() for c in line.split("|")]
        if len(cells) == len(headers):
            rows.append(dict(zip(headers, cells)))
    return rows


# ── file resolution ───────────────────────────────────────────────────────────
def resolve_file(bcb_root: Path, fid: str, ftype: str, name: str) -> Path | None:
    # Try the stated type first, then fall back to all known subdirectories
    for subdir in [ftype, "selected", "default", "sample"]:
        p = bcb_root / fid / subdir / name
        if p.exists():
            return p
    return None


# ── method extraction ─────────────────────────────────────────────────────────
def extract_fragment(src: Path, start: int, end: int) -> str | None:
    try:
        with open(src, encoding="utf-8", errors="replace") as f:
            lines = f.readlines()
    except OSError:
        return None
    if start < 1 or end > len(lines):
        return None
    imports = [l for l in lines[:max(start - 1, 0)]
               if l.strip().startswith("import ") or l.strip().startswith("package ")]
    body = "".join(lines[start - 1:end])
    return "".join(imports) + "\npublic class Fragment {\n" + body + "\n}\n"


# ── run AstMain ───────────────────────────────────────────────────────────────
def run_tool(tool_dir: Path, fa: Path, fb: Path) -> dict | None:
    cmd = [
        "mvn", "-q", "exec:java",
        f"-Dexec.mainClass={MAIN_CLASS}",
        f"-Dexec.args={fa} {fb}",
    ]
    try:
        r = subprocess.run(cmd, cwd=tool_dir,
                           capture_output=True, text=True, timeout=45)
        return parse_output(r.stdout)
    except subprocess.TimeoutExpired:
        return None
    except Exception:
        return None


def parse_output(stdout: str) -> dict:
    patterns = {
        "S1": r"S1 AST-token Winnowing.*?:\s*([\d.]+)%",
        "S2": r"S2 Exact Subtree.*?:\s*([\d.]+)%",
        "S3": r"S3 Method-level.*?:\s*([\d.]+)%",
        "S4": r"S4 Method-level TED.*?:\s*([\d.]+)%",
        "S5": r"S5 API Call.*?:\s*([\d.]+)",
        "Combined": r"Combined Score.*?:\s*([\d.]+)%",
    }
    scores = {}
    for k, pat in patterns.items():
        m = re.search(pat, stdout)
        scores[k] = float(m.group(1)) if m else None
    return scores


# ── metrics ───────────────────────────────────────────────────────────────────
def metrics_at(clone_scores: list[float], nc_scores: list[float],
               threshold: float) -> dict:
    """Compute P / R / F1 at a single threshold (0–100 scale)."""
    tp = sum(1 for s in clone_scores if s >= threshold)
    fn = sum(1 for s in clone_scores if s <  threshold)
    fp = sum(1 for s in nc_scores    if s >= threshold)
    tn = sum(1 for s in nc_scores    if s <  threshold)
    prec = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    rec  = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    f1   = 2 * prec * rec / (prec + rec) if (prec + rec) > 0 else 0.0
    return dict(threshold=threshold, tp=tp, fn=fn, fp=fp, tn=tn,
                precision=prec, recall=rec, f1=f1)


def auc_pr(clone_scores: list[float], nc_scores: list[float]) -> float:
    """Approximate area under P-R curve via trapezoid rule."""
    points = []
    for t in THRESHOLDS:
        m = metrics_at(clone_scores, nc_scores, t)
        points.append((m["recall"], m["precision"]))
    # add (0,1) and (1,0) anchors
    points = sorted(set([(0.0, 1.0)] + points + [(1.0, 0.0)]), key=lambda x: x[0])
    area = 0.0
    for i in range(1, len(points)):
        dr = points[i][0] - points[i-1][0]
        area += dr * (points[i][1] + points[i-1][1]) / 2
    return area


# ── main ──────────────────────────────────────────────────────────────────────
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db",   required=True)
    ap.add_argument("--bcb",  required=True)
    ap.add_argument("--h2",   required=True)
    ap.add_argument("--tool", required=True)
    ap.add_argument("--out",  default="results/v2")
    args = ap.parse_args()

    random.seed(RANDOM_SEED)
    db_url   = f"jdbc:h2:{Path(args.db).expanduser().resolve()}"
    bcb_root = Path(args.bcb).expanduser().resolve()
    h2_jar   = Path(args.h2).expanduser().resolve()
    tool_dir = Path(args.tool).expanduser().resolve()
    out_dir  = Path(args.out).expanduser()
    out_dir.mkdir(parents=True, exist_ok=True)

    tmpdir = Path(tempfile.mkdtemp(prefix="bcb_v2_"))
    print(f"Temp dir : {tmpdir}")
    print(f"Output   : {out_dir}\n")

    # ── build clone pair queries ──────────────────────────────────────────────
    base_sql = """
        SELECT TOP {n}
          f1.NAME as N1, f1.TYPE as T1, f1.STARTLINE as S1, f1.ENDLINE as E1,
          f2.NAME as N2, f2.TYPE as T2, f2.STARTLINE as S2, f2.ENDLINE as E2,
          c.FUNCTIONALITY_ID as FID,
          c.SYNTACTIC_TYPE  as STYPE,
          c.SIMILARITY_TOKEN as SIMTOK
        FROM CLONES c
        JOIN FUNCTIONS f1 ON c.FUNCTION_ID_ONE = f1.ID
        JOIN FUNCTIONS f2 ON c.FUNCTION_ID_TWO = f2.ID
        WHERE c.SYNTACTIC_TYPE = {stype}
          AND f1.TYPE IN ('selected', 'default', 'sample')
          AND f2.TYPE IN ('selected', 'default', 'sample')
          {extra}
        ORDER BY RAND()
    """

    queries = {
        "T1":        base_sql.format(n=SAMPLES[1],          stype=1, extra=""),
        "T2":        base_sql.format(n=SAMPLES[2],          stype=2, extra=""),
        "T3_strong": base_sql.format(n=SAMPLES["3_strong"], stype=3,
                                     extra="AND c.SIMILARITY_TOKEN >= 0.7"),
        "T3_medium": base_sql.format(n=SAMPLES["3_medium"], stype=3,
                                     extra="AND c.SIMILARITY_TOKEN >= 0.5 "
                                           "AND c.SIMILARITY_TOKEN < 0.7"),
        "T3_weak":   base_sql.format(n=SAMPLES["3_weak"],   stype=3,
                                     extra="AND c.SIMILARITY_TOKEN < 0.5"),
    }

    all_pairs = []

    for label, sql in queries.items():
        print(f"Querying {label} ...", end=" ", flush=True)
        rows = h2_query(h2_jar, db_url, sql.strip())
        print(f"{len(rows)} rows from DB")
        for r in rows:
            all_pairs.append({
                "label": label, "is_clone": True,
                "fid": r["FID"], "stype": r["STYPE"], "simtok": r["SIMTOK"],
                "n1": r["N1"], "t1": r["T1"],
                "s1": int(r["S1"]), "e1": int(r["E1"]),
                "n2": r["N2"], "t2": r["T2"],
                "s2": int(r["S2"]), "e2": int(r["E2"]),
            })

    # ── non-clone pairs from filesystem ──────────────────────────────────────
    print(f"\nBuilding {NON_CLONE_PAIRS} non-clone pairs ...", end=" ", flush=True)
    func_files: dict[str, list[Path]] = {}
    for d in sorted(bcb_root.iterdir()):
        if not d.is_dir():
            continue
        # Collect files from all three subdirectory types
        ff = []
        for subdir in ["selected", "default", "sample"]:
            sd = d / subdir
            if sd.exists():
                ff.extend(sd.glob("*.java"))
        if ff:
            func_files[d.name] = ff

    func_ids = list(func_files.keys())
    nc_added, attempts = 0, 0
    while nc_added < NON_CLONE_PAIRS and attempts < 500:
        attempts += 1
        fa_id, fb_id = random.sample(func_ids, 2)
        f_a = random.choice(func_files[fa_id])
        f_b = random.choice(func_files[fb_id])
        # Skip same-filename pairs — they may be identical files across folders
        if f_a.name == f_b.name:
            continue
        ra = h2_query(h2_jar, db_url,
                      f"SELECT TOP 1 STARTLINE as S, ENDLINE as E FROM FUNCTIONS "
                      f"WHERE NAME='{f_a.name}' "
                      f"AND TYPE IN ('selected','default','sample')")
        rb = h2_query(h2_jar, db_url,
                      f"SELECT TOP 1 STARTLINE as S, ENDLINE as E FROM FUNCTIONS "
                      f"WHERE NAME='{f_b.name}' "
                      f"AND TYPE IN ('selected','default','sample')")
        if not ra or not rb:
            continue
        all_pairs.append({
            "label": "non-clone", "is_clone": False,
            "fid": f"{fa_id}/{fb_id}", "stype": "0", "simtok": "0",
            "n1": f_a.name, "t1": "sample",
            "s1": int(ra[0]["S"]), "e1": int(ra[0]["E"]),
            "n2": f_b.name, "t2": "sample",
            "s2": int(rb[0]["S"]), "e2": int(rb[0]["E"]),
            "_fid1": fa_id, "_fid2": fb_id,
        })
        nc_added += 1
    print(f"{nc_added} pairs built ({attempts} attempts)")
    print(f"\nTotal pairs to evaluate: {len(all_pairs)}\n")

    # ── run tool ──────────────────────────────────────────────────────────────
    pair_rows = []
    skipped = 0

    for idx, p in enumerate(all_pairs, 1):
        fid1 = p.get("_fid1", p["fid"]) if not p["is_clone"] else p["fid"]
        fid2 = p.get("_fid2", p["fid"]) if not p["is_clone"] else p["fid"]
        src1 = resolve_file(bcb_root, fid1, p["t1"], p["n1"])
        src2 = resolve_file(bcb_root, fid2, p["t2"], p["n2"])

        if src1 is None or src2 is None:
            skipped += 1
            print(f"[{idx:3}/{len(all_pairs)}] SKIP  {p['label']:10} "
                  f"{p['n1']} or {p['n2']}")
            continue

        fr1 = extract_fragment(src1, p["s1"], p["e1"])
        fr2 = extract_fragment(src2, p["s2"], p["e2"])
        if not fr1 or not fr2:
            skipped += 1
            print(f"[{idx:3}/{len(all_pairs)}] SKIP  {p['label']:10} "
                  f"extraction failed")
            continue

        # Skip very large method pairs — APTED is O(n³) and will hang
        MAX_LINES = 150
        lines1 = p["e1"] - p["s1"] + 1
        lines2 = p["e2"] - p["s2"] + 1
        if lines1 > MAX_LINES or lines2 > MAX_LINES:
            skipped += 1
            print(f"[{idx:3}/{len(all_pairs)}] SKIP  {p['label']:10} "
                  f"method too large ({lines1}/{lines2} lines)")
            continue

        t1 = tmpdir / f"pair_{idx}_A.java"
        t2 = tmpdir / f"pair_{idx}_B.java"
        t1.write_text(fr1, encoding="utf-8")
        t2.write_text(fr2, encoding="utf-8")

        simtok_display = (p["simtok"][:5]
                          if p["simtok"] not in ("0", "?") else "—")
        print(f"[{idx:3}/{len(all_pairs)}] {p['label']:10} "
              f"sim={simtok_display:5}  {p['n1']} vs {p['n2']} ...",
              end=" ", flush=True)

        scores = run_tool(tool_dir, t1, t2)
        if scores is None:
            print("FAILED")
            skipped += 1
            continue

        comb = scores.get("Combined")
        print(f"Combined={'%.1f%%' % comb if comb is not None else 'N/A'}")

        pair_rows.append({
            "label":    p["label"],
            "is_clone": p["is_clone"],
            "fid":      p["fid"],
            "stype":    p["stype"],
            "simtok":   p["simtok"],
            "file1":    p["n1"],
            "file2":    p["n2"],
            **{k: (f"{v:.4f}" if v is not None else "N/A")
               for k, v in scores.items()},
        })

    shutil.rmtree(tmpdir, ignore_errors=True)
    print(f"\nEvaluated: {len(pair_rows)}   Skipped: {skipped}")

    # ── write pairs CSV ───────────────────────────────────────────────────────
    pairs_csv = out_dir / "bcb_v2_pairs.csv"
    pair_fields = ["label", "is_clone", "fid", "stype", "simtok",
                   "file1", "file2", "S1", "S2", "S3", "S4", "S5", "Combined"]
    with open(pairs_csv, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=pair_fields)
        w.writeheader()
        w.writerows(pair_rows)
    print(f"Pairs CSV → {pairs_csv}")

    # ── separate scores ───────────────────────────────────────────────────────
    labels_order = ["T1", "T2", "T3_strong", "T3_medium", "T3_weak"]
    scores_by_type: dict[str, list[float]] = defaultdict(list)
    nc_scores: list[float] = []

    for r in pair_rows:
        c = r["Combined"]
        if c == "N/A":
            continue
        val = float(c)
        if r["is_clone"]:
            scores_by_type[r["label"]].append(val)
        else:
            nc_scores.append(val)

    all_clone_scores = [s for lbl in labels_order
                        for s in scores_by_type.get(lbl, [])]

    # ── threshold sweep ───────────────────────────────────────────────────────
    print("\n" + "="*72)
    print("=== Threshold Sweep (global: all clone types vs non-clone) ===")
    print(f"{'Threshold':>10} {'Precision':>10} {'Recall':>8} "
          f"{'F1':>8} {'TP':>5} {'FP':>5} {'FN':>5} {'TN':>5}")
    print("-"*72)

    metric_rows = []
    best_f1, best_thresh = 0.0, 0.0
    for t in THRESHOLDS:
        m = metrics_at(all_clone_scores, nc_scores, t)
        metric_rows.append(m)
        marker = " ◀" if m["f1"] > best_f1 else ""
        if m["f1"] > best_f1:
            best_f1, best_thresh = m["f1"], t
        print(f"  {t:6}%    {m['precision']:8.3f}   {m['recall']:7.3f} "
              f"  {m['f1']:6.3f}  {m['tp']:4}  {m['fp']:4}  "
              f"{m['fn']:4}  {m['tn']:4}{marker}")

    auc = auc_pr(all_clone_scores, nc_scores)
    print(f"\nApprox AUC-PR : {auc:.4f}")
    print(f"Best threshold: {best_thresh}%  (F1={best_f1:.3f})")

    metrics_csv = out_dir / "bcb_v2_metrics.csv"
    with open(metrics_csv, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(metric_rows[0].keys()))
        w.writeheader()
        w.writerows(metric_rows)
    print(f"Metrics CSV  → {metrics_csv}")

    # ── per-type recall at each threshold ────────────────────────────────────
    print("\n" + "="*72)
    print("=== Per-type Recall at key thresholds ===")
    key_thresholds = [20, 25, 30, 35, 40, 50]
    header = f"{'Type':12}" + "".join(f"  @{t}%" for t in key_thresholds) + "   n"
    print(header)
    print("-" * len(header))

    pertype_rows = []
    all_labels_with_nc = labels_order + ["non-clone"]
    for lbl in all_labels_with_nc:
        sc = scores_by_type.get(lbl, []) if lbl != "non-clone" else nc_scores
        n = len(sc)
        row = {"type": lbl, "n": n}
        line = f"  {lbl:12}"
        for t in key_thresholds:
            recall = sum(1 for s in sc if s >= t) / n if n > 0 else 0.0
            row[f"recall@{t}"] = f"{recall:.3f}"
            line += f"  {recall:6.3f}"
            # For non-clone this is FPR (false positive rate)
        pertype_rows.append(row)
        note = "  (FPR)" if lbl == "non-clone" else ""
        print(line + f"  {n:4}{note}")

    pertype_csv = out_dir / "bcb_v2_pertype.csv"
    all_t_fields = ["type", "n"] + [f"recall@{t}" for t in THRESHOLDS]
    # recompute full pertype for CSV
    full_pertype = []
    for lbl in all_labels_with_nc:
        sc = scores_by_type.get(lbl, []) if lbl != "non-clone" else nc_scores
        n = len(sc)
        row = {"type": lbl, "n": n}
        for t in THRESHOLDS:
            recall = sum(1 for s in sc if s >= t) / n if n > 0 else 0.0
            row[f"recall@{t}"] = f"{recall:.4f}"
        full_pertype.append(row)
    with open(pertype_csv, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=all_t_fields)
        w.writeheader()
        w.writerows(full_pertype)
    print(f"\nPer-type CSV → {pertype_csv}")

    # ── per-type Combined Score distribution ─────────────────────────────────
    print("\n" + "="*72)
    print("=== Combined Score Distribution by Type ===")
    print(f"{'Type':12} {'n':>4} {'avg':>7} {'std':>7} {'min':>7} "
          f"{'p25':>7} {'p50':>7} {'p75':>7} {'max':>7}")
    print("-"*72)

    def stats(vals):
        if not vals:
            return None
        s = sorted(vals)
        n = len(s)
        avg = sum(s) / n
        std = (sum((x - avg)**2 for x in s) / n) ** 0.5
        p25 = s[int(n * 0.25)]
        p50 = s[int(n * 0.50)]
        p75 = s[int(n * 0.75)]
        return avg, std, s[0], p25, p50, p75, s[-1]

    for lbl in all_labels_with_nc:
        sc = scores_by_type.get(lbl, []) if lbl != "non-clone" else nc_scores
        st = stats(sc)
        if st:
            avg, std, mn, p25, p50, p75, mx = st
            print(f"  {lbl:12} {len(sc):4}  {avg:6.1f}%  {std:6.1f}%  "
                  f"{mn:6.1f}%  {p25:6.1f}%  {p50:6.1f}%  "
                  f"{p75:6.1f}%  {mx:6.1f}%")
        else:
            print(f"  {lbl:12}    0   — (no data)")

    print(f"\nAll results saved to: {out_dir}/")


if __name__ == "__main__":
    main()
