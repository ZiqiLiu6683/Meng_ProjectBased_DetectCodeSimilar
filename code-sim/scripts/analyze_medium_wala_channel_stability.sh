#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SNAPSHOT_ROOT="$ROOT_DIR/target/robustness/medium/snapshots"
OUTPUT_ROOT="$ROOT_DIR/target/robustness/medium/stability"

mkdir -p "$OUTPUT_ROOT"

for project_dir in "$SNAPSHOT_ROOT"/*; do
  if [ ! -d "$project_dir" ]; then
    continue
  fi
  project="$(basename "$project_dir")"
  python3 "$ROOT_DIR/scripts/analyze_wala_raw_channel_stability.py" \
    "$OUTPUT_ROOT/$project-channel_stability.csv" \
    "$project_dir"/*.jsonl
done

echo "Medium channel stability tables written to $OUTPUT_ROOT"
