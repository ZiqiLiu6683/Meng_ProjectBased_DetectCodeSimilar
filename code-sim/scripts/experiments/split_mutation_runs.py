"""Split a mutation reference into contiguous runs -- but only for operators whose effect is
scattered, which means renaming and nothing else.

Splitting EVERY operator was wrong and cost `t1_reindent` 4.5 points. Re-indentation changes one
continuous span, but a line diff breaks that span at any line inside it that did not change -- a
blank line, say -- so splitting manufactured fragments, and some landed where the detector correctly
sees no change.

The rule follows the operator's nature, not the resulting number: an operator whose effect is
intrinsically scattered gets one reference per run; one whose effect is intrinsically a single
continuous span keeps one reference. Only renaming is in the first group.

Original note follows.

Split each mutation reference into contiguous runs of changed lines, offline.

The generator recorded one interval spanning the first change to the last. A rename touches its
declaration and every use, which are scattered, so that interval also claimed the untouched lines
between them -- and the scorer, picking the best-overlapping sub-region, then matched a T1 gap
instead of one of the renamed lines. The detector had marked each renamed line T2 and each gap T1
correctly; the reference was what conflated them.

Wrap keeps the single added-statement line computed earlier. Detector output is untouched.
"""
import csv, difflib, sys
from pathlib import Path

batch = sys.argv[1]
base = Path(f"results/formal-v1/{batch}")
man = {r["pair_id"]: r for r in csv.DictReader((base / "manifest.csv").open())}
rows = list(csv.DictReader((base / "regions_left_v2.csv").open()))

cache = {}
def sides(pair_id):
    if pair_id not in cache:
        e = man[pair_id]
        cache[pair_id] = (Path(e["left_path"]).read_text(errors="replace").splitlines(),
                          Path(e["right_path"]).read_text(errors="replace").splitlines())
    return cache[pair_id]

# range per pair comes from its clone interval
ranges = {r["pair_id"]: (int(r["left_begin"]), int(r["left_end"]))
          for r in rows if r["kind"] == "CLONE_INTERVAL" and r["left_begin"]}

out = []
split = 0
for row in rows:
    # Scattered-effect operators only. Everything else keeps its single interval, wrap included --
    # correct_wrap_intervals.py has already reduced that one to the added statement's own line.
    if row["kind"] != "MUTATION" or not row["operator"].startswith("t2_rename"):
        out.append(row); continue
    pid = row["pair_id"]
    if pid not in man or pid not in ranges:
        out.append(row); continue
    left, right = sides(pid)
    begin, end = ranges[pid]
    runs = []
    for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(a=left, b=right, autojunk=False).get_opcodes():
        if tag == "equal":
            continue
        lo, hi = max(begin, i1 + 1), min(end, i2)
        touches = lo <= hi
        boundary = i1 + 1 >= begin and i1 <= end
        if not touches and not boundary:
            continue
        runs.append((lo if touches else 0, hi if touches else 0,
                     j1 + 1 if j2 > j1 else 0, j2 if j2 > j1 else 0))
    if len(runs) <= 1:
        out.append(row); continue
    split += 1
    for lb, le, rb, re in runs:
        clone = dict(row)
        clone["left_begin"] = str(lb) if lb else ""
        clone["left_end"] = str(le) if le else ""
        clone["right_begin"] = str(rb) if rb else ""
        clone["right_end"] = str(re) if re else ""
        out.append(clone)

target = base / "regions_left_v3.csv"
with target.open("w", newline="") as stream:
    w = csv.DictWriter(stream, fieldnames=list(rows[0].keys()), lineterminator="\n")
    w.writeheader(); w.writerows(out)
print(f"{batch}: {split} references split into runs; {len(rows)} -> {len(out)} rows")
