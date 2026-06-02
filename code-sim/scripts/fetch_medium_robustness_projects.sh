#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECTS_FILE="$ROOT_DIR/evaluation/robustness/medium_projects.csv"
SOURCE_ROOT="$ROOT_DIR/target/robustness/medium/sources"

mkdir -p "$SOURCE_ROOT"

tail -n +2 "$PROJECTS_FILE" | while IFS=, read -r project repo ref; do
  if [ -z "$project" ] || [ -z "$repo" ]; then
    continue
  fi
  dest="$SOURCE_ROOT/$project"
  if [ -d "$dest/.git" ]; then
    echo "Already fetched $project at $dest"
    continue
  fi
  echo "Fetching $project from $repo"
  if [ -n "$ref" ]; then
    git clone --depth 1 --branch "$ref" "$repo" "$dest"
  else
    git clone --depth 1 "$repo" "$dest"
  fi
done

echo "Medium robustness project sources are under $SOURCE_ROOT"
