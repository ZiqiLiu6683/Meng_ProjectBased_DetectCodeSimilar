#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CLASS_ROOT="$ROOT_DIR/target/robustness/classes"
SNAPSHOT_ROOT="$ROOT_DIR/target/robustness/snapshots"

mkdir -p "$SNAPSHOT_ROOT"

for variant_dir in "$CLASS_ROOT"/*; do
  if [ ! -d "$variant_dir" ]; then
    continue
  fi
  variant="$(basename "$variant_dir")"
  mvn -q -f "$ROOT_DIR/pom.xml" -Psemantic-analysis exec:java \
    -Dexec.mainClass=com.ziqi.codesim.semantic.backend.wala.raw.WalaRawSnapshotMain \
    -Dexec.args="$variant_dir $SNAPSHOT_ROOT/$variant.jsonl"
done

echo "Raw WALA snapshots written to $SNAPSHOT_ROOT"
