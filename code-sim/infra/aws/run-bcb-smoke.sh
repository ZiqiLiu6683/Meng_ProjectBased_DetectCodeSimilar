#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
project_dir="${repo_root}/code-sim"
manifest="${project_dir}/results/bcb_smoke/manifest_v2.csv"
out_dir="${project_dir}/results/cloud-trial-bcb-smoke"

if [[ -n "$(git -C "${repo_root}" status --porcelain)" ]]; then
  echo "Refusing a provenance run from a dirty worktree:" >&2
  git -C "${repo_root}" status --short >&2
  exit 2
fi

echo "== frozen identity =="
git -C "${repo_root}" rev-parse HEAD
python3 "${project_dir}/scripts/experiments/manifest_v2.py" validate \
  --manifest "${manifest}" \
  --dataset-id bcb-smoke-20260721

echo "== runtime classpath =="
mvn -f "${project_dir}/pom.xml" -q -Psemantic-analysis -DskipTests clean compile \
  dependency:build-classpath -Dmdep.outputFile=target/cp.txt

echo "== 140-pair BCB plumbing smoke =="
python3 "${project_dir}/scripts/experiments/run_shards.py" \
  --manifest "${manifest}" \
  --out "${out_dir}" \
  --shards 4 \
  --workers 1 \
  --xmx 4g \
  --max-attempts 1 \
  --config-id bcb-smoke-t1t3-dynamic-off-v1 \
  --environment-id aws-us-east-2-m7i-flex-large-ubuntu-24.04-trial \
  --skip-dynamic

echo "BCB plumbing smoke finished: ${out_dir}"
