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

echo "== strict scorer tests =="
python3 "${project_dir}/scripts/experiments/tests/test_score_results_v2.py"

echo "AWS trial preflight passed. No benchmark dataset was executed."
