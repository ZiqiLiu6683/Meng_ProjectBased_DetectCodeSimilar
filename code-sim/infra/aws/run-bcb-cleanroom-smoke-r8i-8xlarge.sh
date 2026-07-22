#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
project_dir="${repo_root}/code-sim"

: "${BCB_DB_BASE:?Set BCB_DB_BASE to the official BigCloneEval H2 database base path}"
: "${H2_JAR:?Set H2_JAR to the official database-compatible H2 jar}"
: "${BCB_REDUCED_ROOT:?Set BCB_REDUCED_ROOT to the official IJaDataset bcb_reduced directory}"
: "${BCB_ARCHIVE:?Set BCB_ARCHIVE to the unmodified official BigCloneBench_BCEvalVersion archive}"
: "${IJADATASET_ARCHIVE:?Set IJADATASET_ARCHIVE to the unmodified official IJaDataset_BCEvalVersion archive}"
: "${BCB_RELEASE:?Set BCB_RELEASE to the exact official release identifier}"
: "${IJADATASET_RELEASE:?Set IJADATASET_RELEASE to the exact official release identifier}"

if [[ -n "$(git -C "${repo_root}" status --porcelain)" ]]; then
  echo "Refusing a clean-room run from a dirty worktree:" >&2
  git -C "${repo_root}" status --short >&2
  exit 2
fi

cpu_count="$(nproc)"
memory_kib="$(awk '/^MemTotal:/ {print $2}' /proc/meminfo)"
if (( cpu_count < 32 || memory_kib < 240000000 )); then
  echo "Requires at least 32 vCPU and 240,000,000 KiB RAM; found ${cpu_count} vCPU and ${memory_kib} KiB." >&2
  exit 2
fi

commit="$(git -C "${repo_root}" rev-parse HEAD)"
short_commit="$(git -C "${repo_root}" rev-parse --short=12 HEAD)"
run_stamp="$(date -u +%Y%m%dT%H%M%SZ)"
artifact_root="${CODESIM_ARTIFACT_ROOT:-/opt/codesim/results}/bcb-cleanroom-smoke-${short_commit}-${run_stamp}"
dataset_dir="${artifact_root}/dataset"
run_dir="${artifact_root}/run"
score_dir="${artifact_root}/score"
mkdir -p "${artifact_root}"

echo "== clean-room identity =="
echo "commit=${commit}"
echo "vCPU=${cpu_count} MemTotalKiB=${memory_kib}"
echo "artifacts=${artifact_root}"

echo "== build fresh BCB-derived diagnostic dataset from official inputs =="
python3 -u "${project_dir}/scripts/experiments/bcb_cleanroom.py" \
  --db "${BCB_DB_BASE}" \
  --h2 "${H2_JAR}" \
  --bcb "${BCB_REDUCED_ROOT}" \
  --bcb-archive "${BCB_ARCHIVE}" \
  --ijadataset-archive "${IJADATASET_ARCHIVE}" \
  --out "${dataset_dir}" \
  --bcb-release "${BCB_RELEASE}" \
  --ijadataset-release "${IJADATASET_RELEASE}" \
  --per-stratum "${BCB_SMOKE_PER_STRATUM:-20}" \
  --seed 20260721

echo "== compile frozen runtime =="
mvn -f "${project_dir}/pom.xml" -q -Psemantic-analysis -DskipTests compile \
  dependency:build-classpath -Dmdep.outputFile=target/cp.txt

dataset_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["dataset_id"])' "${dataset_dir}/dataset_lock.json")"

echo "== execute unique complete-original-file pairs =="
python3 -u "${project_dir}/scripts/experiments/run_shards.py" \
  --manifest "${dataset_dir}/executions.csv" \
  --out "${run_dir}" \
  --shards 32 \
  --workers 32 \
  --xmx 6g \
  --max-attempts 1 \
  --config-id "bcb-cleanroom-original-files-dynamic-off-${short_commit}" \
  --environment-id aws-us-east-2-r8i-8xlarge-ubuntu-24.04 \
  --skip-dynamic

echo "== strict reference scoring with mandatory backend audit =="
python3 -u "${project_dir}/scripts/experiments/score_bcb_cleanroom.py" \
  --executions "${dataset_dir}/executions.csv" \
  --references "${dataset_dir}/references.csv" \
  --dataset-lock "${dataset_dir}/dataset_lock.json" \
  --run-config "${run_dir}/run_config.json" \
  --results "${run_dir}/merged.jsonl" \
  --machine "${run_dir}/machine.json" \
  --out "${score_dir}" \
  --attempt-policy first \
  --bootstrap-iterations 2000 \
  --seed 20260721

echo "Clean-room BCB smoke completed: ${artifact_root}"
echo "Dataset ID: ${dataset_id}"
