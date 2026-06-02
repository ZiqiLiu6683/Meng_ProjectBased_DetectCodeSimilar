#!/usr/bin/env python3
import csv
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SELECTION = ROOT / "target" / "robustness" / "medium" / "channel_selection.csv"
VIEW_ROOT = ROOT / "target" / "robustness" / "medium" / "knn_views"


def read_selection():
    with SELECTION.open(encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def write_view(name, channels):
    path = VIEW_ROOT / f"{name}.channels"
    path.write_text("\n".join(channels) + "\n", encoding="utf-8")
    return path


def main():
    rows = read_selection()
    VIEW_ROOT.mkdir(parents=True, exist_ok=True)
    raw_all = [
        row["channel"]
        for row in rows
        if row["decision"] != "DROP"
    ]
    raw_filtered = [
        row["channel"]
        for row in rows
        if row["decision"] == "KEEP"
    ]
    raw_instruction_stable = [
        row["channel"]
        for row in rows
        if row["decision"] == "KEEP" and row["channel"].startswith("instruction.")
    ]
    paths = [
        write_view("RAW_ALL_NON_DROP", raw_all),
        write_view("RAW_FILTERED_KEEP", raw_filtered),
        write_view("RAW_INSTRUCTION_STABLE", raw_instruction_stable),
    ]
    for path in paths:
        print(f"Wrote {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
