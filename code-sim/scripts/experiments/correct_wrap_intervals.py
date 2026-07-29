"""Recompute the wrap mutation interval as the ADDED STATEMENT\'s own line.

Wrapping adds one statement -- an `if` -- around an unchanged one. Two earlier attempts got the
extent wrong in opposite directions. Comparing against the reference\'s own left extent counted a
re-indented loop body as added, because its counterpart lay outside that extent. Taking the whole
span from the first added line to the last put the closing brace at the far end, so the interval
swallowed the wrapped statement again.

Neither the brace nor the body is what the operator added: it added ONE statement, and a statement\'s
own extent excludes what is nested inside it -- which is why `ownLineSpan` reports an `if` as its
header line alone. The reference is therefore the first added line, the `if (true) {`. This follows
from what the operator did and from how a statement\'s extent is defined, not from what the detector
happened to report.
"""
import csv, difflib, sys
from pathlib import Path

batch = sys.argv[1]
base = Path(f"results/formal-v1/{batch}")
man = {r["pair_id"]: r for r in csv.DictReader((base / "manifest.csv").open())}
rows = list(csv.DictReader((base / "regions_left.csv").open()))

added_cache = {}
def added_lines(pair_id):
    """Right-hand line numbers with no counterpart on the left, ignoring indentation."""
    if pair_id in added_cache:
        return added_cache[pair_id]
    entry = man[pair_id]
    left = [l.strip() for l in Path(entry["left_path"]).read_text(errors="replace").splitlines()]
    right = [l.strip() for l in Path(entry["right_path"]).read_text(errors="replace").splitlines()]
    out = set()
    for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(a=left, b=right, autojunk=False).get_opcodes():
        if tag in ("insert", "replace"):
            block = left[i1:i2]
            for j in range(j1, j2):
                if right[j] not in block:
                    out.add(j + 1)
    added_cache[pair_id] = out
    return out

corrected = 0
for row in rows:
    if row["kind"] != "MUTATION" or not row["operator"].startswith("t3_wrap"):
        continue
    if row["pair_id"] not in man:
        continue
    rb = int(row["right_begin"] or 0); re_ = int(row["right_end"] or 0)
    if not (rb and re_):
        continue
    inside = sorted(l for l in added_lines(row["pair_id"]) if rb <= l <= re_ + 2)
    if not inside:
        continue
    header = min(inside)
    row["right_begin"] = str(header); row["right_end"] = str(header)
    row["left_begin"] = ""; row["left_end"] = ""   # wrap removes nothing
    corrected += 1

out = base / "regions_left_v2.csv"
with out.open("w", newline="") as stream:
    w = csv.DictWriter(stream, fieldnames=list(rows[0].keys()), lineterminator="\n")
    w.writeheader(); w.writerows(rows)
print(f"{batch}: corrected {corrected} wrap intervals")

