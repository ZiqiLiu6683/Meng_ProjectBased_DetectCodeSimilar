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
  -Dcodesim.compileCache="${CODESIM_COMPILE_CACHE:-/opt/codesim/cache/compilation-v4}" \
  -Dtest=WalaNextPipelineRunnerTest,WalaNextPipelineT4Test,WalaNextPipelineDynamicT4Test,PipelineExecutionProvenanceTest,BatchPairMainSchemaTest,JavaCompilationCoordinatorTest,ProjectCandidateIndexerTest,DynamicEquivalenceCheckerTest \
  test

echo "== experiment harness and scorer tests =="
python3 -m unittest discover \
  -s "${project_dir}/scripts/experiments/tests" \
  -p 'test_*.py'

echo "== clean-room scripts compile =="
python3 -m py_compile \
  "${project_dir}/scripts/experiments/execution_manifest.py" \
  "${project_dir}/scripts/experiments/bcb_cleanroom.py" \
  "${project_dir}/scripts/experiments/run_shards.py" \
  "${project_dir}/scripts/experiments/score_bcb_cleanroom.py"

echo "AWS preflight passed. Legacy bcb_smoke artifacts were not read. No benchmark dataset was executed."
