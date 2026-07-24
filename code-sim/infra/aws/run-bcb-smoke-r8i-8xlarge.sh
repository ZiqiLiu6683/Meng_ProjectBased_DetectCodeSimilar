#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
project_dir="${repo_root}/code-sim"
manifest="${project_dir}/results/bcb_smoke/manifest_v2.csv"
out_dir="${project_dir}/results/cloud-finalenv-bcb-smoke-r8i-8xl"

if [[ -n "$(git -C "${repo_root}" status --porcelain)" ]]; then
  echo "Refusing a provenance run from a dirty worktree:" >&2
  git -C "${repo_root}" status --short >&2
  exit 2
fi

cpu_count="$(nproc)"
memory_kib="$(awk '/^MemTotal:/ {print $2}' /proc/meminfo)"
if (( cpu_count < 32 || memory_kib < 240000000 )); then
  echo "This run requires r8i.8xlarge-class resources: at least 32 vCPU and 240,000,000 KiB RAM; found ${cpu_count} vCPU and ${memory_kib} KiB." >&2
  exit 2
fi

echo "== frozen r8i.8xlarge environment identity =="
git -C "${repo_root}" rev-parse HEAD
echo "vCPU=${cpu_count} MemTotalKiB=${memory_kib}"
python3 "${project_dir}/scripts/experiments/manifest_v2.py" validate \
  --manifest "${manifest}" \
  --dataset-id bcb-smoke-20260721

echo "== runtime classpath =="
mvn -f "${project_dir}/pom.xml" -q -Psemantic-analysis -DskipTests clean compile \
  dependency:build-classpath -Dmdep.outputFile=target/cp.txt

echo "== 140-pair BCB smoke on r8i.8xlarge =="
python3 -u "${project_dir}/scripts/experiments/run_shards.py" \
  --manifest "${manifest}" \
  --out "${out_dir}" \
  --shards 32 \
  --workers 32 \
  --xmx 6g \
  --max-attempts 1 \
  --config-id bcb-smoke-t1t3-dynamic-off-r8i-8xl-v1 \
  --environment-id aws-us-east-2-r8i-8xlarge-ubuntu-24.04 \
  --skip-dynamic

echo "== strict smoke scoring =="
python3 "${project_dir}/scripts/experiments/score_results_v2.py" \
  --results "${out_dir}/merged.jsonl" \
  --manifest "${manifest}" \
  --out "${out_dir}/score" \
  --detection-policy strict \
  --attempt-policy first \
  --bootstrap-iterations 2000 \
  --seed 20260721

echo "BCB r8i.8xlarge smoke and scoring finished: ${out_dir}"
