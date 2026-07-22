#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
project_dir="${repo_root}/code-sim"

echo "== experiment identity =="
git -C "${repo_root}" rev-parse HEAD
git -C "${repo_root}" status --short

echo "== compile semantic profile =="
mvn -f "${project_dir}/pom.xml" -Psemantic-analysis -DskipTests clean compile

echo "== affected Java regression tests =="
mvn -f "${project_dir}/pom.xml" -q -Psemantic-analysis \
  -Dtest=WalaNextPipelineRunnerTest,WalaNextPipelineT4Test,WalaNextPipelineDynamicT4Test,PipelineExecutionProvenanceTest,BatchPairMainSchemaTest \
  test

echo "== experiment harness and scorer tests =="
python3 -m unittest discover \
  -s "${project_dir}/scripts/experiments/tests" \
  -p 'test_*.py'

echo "== portable BCB smoke manifest =="
python3 "${project_dir}/scripts/experiments/manifest_v2.py" validate \
  --manifest "${project_dir}/results/bcb_smoke/manifest_v2.csv" \
  --dataset-id bcb-smoke-20260721

echo "AWS trial preflight passed. No benchmark dataset was executed."
