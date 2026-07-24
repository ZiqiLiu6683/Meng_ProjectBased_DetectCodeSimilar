#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export BCB_STUB_POLICY=forbid
exec bash "${script_dir}/run-bcb-cleanroom-smoke-r8i-8xlarge.sh"
