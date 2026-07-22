#!/usr/bin/env bash
set -euo pipefail

echo "== operating system =="
if [[ -f /etc/os-release ]]; then
  sed -n '1,12p' /etc/os-release
fi

echo "== cpu =="
lscpu | sed -n '1,24p'

echo "== memory =="
free -h

echo "== disk =="
df -h /

echo "== toolchain =="
java -version
javac -version
mvn -version
python3 --version
git --version
docker --version

echo "== identity metadata =="
echo "user=$(id -un)"
echo "groups=$(id -Gn)"
echo "utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
