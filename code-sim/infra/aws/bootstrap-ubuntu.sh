#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo bash $0" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends \
  ca-certificates \
  curl \
  docker.io \
  git \
  jq \
  maven \
  openjdk-17-jdk-headless \
  python3 \
  python3-venv \
  rsync \
  time \
  tmux \
  zip \
  unzip

systemctl enable --now docker

for codesim_user in ubuntu ssm-user; do
  if id "${codesim_user}" >/dev/null 2>&1; then
    usermod -aG docker "${codesim_user}"
  fi
done

install -d -m 2775 -o root -g docker /opt/codesim
install -d -m 2775 -o root -g docker /opt/codesim/cache
install -d -m 2775 -o root -g docker /opt/codesim/datasets
install -d -m 2775 -o root -g docker /opt/codesim/results

cat <<'EOF'
CodeSim trial host bootstrap completed.
Log out and reconnect before using Docker as ubuntu or ssm-user.
Do not place credentials under /opt/codesim or in the repository.
EOF
