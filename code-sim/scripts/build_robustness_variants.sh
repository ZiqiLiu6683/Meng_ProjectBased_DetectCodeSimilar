#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SRC_DIR="$ROOT_DIR/evaluation/robustness/java"
OUT_DIR="$ROOT_DIR/target/robustness/classes"

mkdir -p "$OUT_DIR"

javac -g -d "$OUT_DIR/javac-g" "$SRC_DIR/probe/RobustnessProbe.java"
javac -g:none -d "$OUT_DIR/javac-g-none" "$SRC_DIR/probe/RobustnessProbe.java"

for release in 8 11 17; do
  if javac --release "$release" -version >/dev/null 2>&1; then
    javac --release "$release" -d "$OUT_DIR/javac-release-$release" "$SRC_DIR/probe/RobustnessProbe.java"
  else
    echo "Skipping javac --release $release because the active javac does not support it"
  fi
done

echo "Robustness bytecode variants written to $OUT_DIR"
