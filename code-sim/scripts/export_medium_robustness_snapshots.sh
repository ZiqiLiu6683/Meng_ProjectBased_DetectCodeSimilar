#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPORT="$ROOT_DIR/target/robustness/medium/build_report.csv"
SNAPSHOT_ROOT="$ROOT_DIR/target/robustness/medium/snapshots"

if [ ! -f "$REPORT" ]; then
  echo "Missing build report: $REPORT" >&2
  exit 2
fi

mkdir -p "$SNAPSHOT_ROOT"

tail -n +2 "$REPORT" | while IFS=, read -r project variant status source_files output_dir error_log; do
  if [ "$status" != "success" ]; then
    continue
  fi
  out_dir="$SNAPSHOT_ROOT/$project"
  mkdir -p "$out_dir"
  mvn -q -f "$ROOT_DIR/pom.xml" -Psemantic-analysis exec:java \
    -Dexec.mainClass=com.ziqi.codesim.semantic.backend.wala.raw.WalaRawSnapshotMain \
    -Dexec.args="$output_dir $out_dir/$variant.jsonl"
done

echo "Medium raw WALA snapshots written to $SNAPSHOT_ROOT"
