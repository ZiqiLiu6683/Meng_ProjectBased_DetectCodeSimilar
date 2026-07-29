#!/bin/zsh
# Cap shard log growth while run_shards.py is running.
# run_shards opens each shard log with O_APPEND, so truncating in place frees the
# space immediately and the JVM's next write restarts at offset 0. Recent output
# is kept for diagnosis; only old spew is discarded.
#
# Usage: logcap.sh <run-dir> [max-mb] [poll-seconds]
DIR=${1:?run dir required}
MAXMB=${2:-200}
POLL=${3:-20}
MAXB=$((MAXMB * 1024 * 1024))

echo "[logcap] capping *.log in $DIR at ${MAXMB}MB, polling ${POLL}s"
while true; do
  # stop once no BatchPairMain JVM is alive
  if ! pgrep -f BatchPairMain >/dev/null 2>&1; then
    echo "[logcap] no BatchPairMain running; exiting"
    break
  fi
  for f in $DIR/*.log(N); do
    size=$(stat -f%z "$f" 2>/dev/null) || continue
    if [ "$size" -gt "$MAXB" ]; then
      tail -c 2097152 "$f" > "$f.keep" 2>/dev/null
      : > "$f"
      cat "$f.keep" >> "$f" 2>/dev/null
      rm -f "$f.keep"
      echo "[logcap] truncated $(basename $f) (was $((size/1048576))MB, kept last 2MB)"
    fi
  done
  sleep $POLL
done
