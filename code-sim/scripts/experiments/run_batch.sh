#!/bin/zsh
# One batch of the frozen region-corpus run: generate, execute, score, and record the seeds used.
#
# Usage: batch.sh <batch-name> <pairs> <rng-seed>
#
# Seeds already consumed are excluded, and this batch's seeds are appended when it finishes. Batches
# shuffle the same 75,000-file corpus with different RNG seeds, so without the exclusion a later
# batch silently reuses files an earlier one used and the statistics treat near-duplicate pairs as
# independent. The list is only appended AFTER a batch completes, so a failed batch does not burn
# its seeds.
set -e

NAME=${1:?batch name required}
PAIRS=${2:?pair count required}
SEED=${3:?rng seed required}

ROOT=/Users/liuziqi/Documents/西大Graduate/Project_base/Code/Meng_ProjectBased_DetectCodeSimilar
GEN=$ROOT/mutation-gen
SIM=$ROOT/code-sim
OUT=$SIM/results/formal-v1/$NAME
USED=$SIM/results/formal-v1/used_seeds.txt
CORPUS=/Users/liuziqi/codesim-data/extracted/Project_CodeNet_Java250
JAVA_HOME=/opt/homebrew/opt/openjdk@17
JAVA=$JAVA_HOME/bin/java
SCRATCH=$(dirname $0)

echo "=== $(date -u +%H:%M:%SZ) $NAME: generating $PAIRS pairs (rng=$SEED) ==="
cd $GEN
rm -rf $OUT
$JAVA -Dmutgen.excludeSeeds=$USED \
    -cp "target/mutation-gen-0.1.0.jar:$(cat target/cp.txt)" \
    com.ziqi.mutgen.RegionCorpusGenerator "$CORPUS" "$OUT" $PAIRS 1 6 3 $SEED 2>&1 \
    | grep -v SLF4J | tail -14

cd $SIM
python3 - "$NAME" <<'PY'
import csv, pathlib, sys
base = pathlib.Path("results/formal-v1") / sys.argv[1]
rows = list(csv.DictReader((base / "manifest_raw.csv").open()))
with (base / "manifest.csv").open("w", newline="") as stream:
    writer = csv.DictWriter(stream, fieldnames=["pair_id", "left_path", "right_path"])
    writer.writeheader()
    for row in rows:
        writer.writerow({k: row[k] for k in ("pair_id", "left_path", "right_path")})
print(f"[{sys.argv[1]}] manifest rows: {len(rows)}")
PY

python3 scripts/experiments/to_execution_manifest.py \
    --in $OUT/manifest.csv --dataset-id formal-v1-$NAME --out $OUT/exec_manifest.csv

echo "=== $(date -u +%H:%M:%SZ) $NAME: running ==="
rm -rf /tmp/cache-$NAME
JAVA_HOME=$JAVA_HOME python3 scripts/experiments/run_shards.py \
    --manifest $OUT/exec_manifest.csv --out $OUT/run \
    --config-id formal-v1-$NAME --environment-id mac-m-local-8gb \
    --java $JAVA --workers 2 --shards 8 --xmx 1600m --skip-dynamic \
    "--java-opt=-Dcodesim.compileCache=/tmp/cache-$NAME" &
RUNNER=$!
sleep 60
zsh $(dirname $0)/cap_wala_logs.sh $OUT/run 150 30 >/tmp/$NAME-logcap.out 2>&1 &
wait $RUNNER

# Copy the raw product output out of the working directory and keep it compressed. run/ is
# working state and is not preserved; this file is, because rescoring must never require another
# seven hours of detector time.
mkdir -p $OUT/raw
gzip -c $OUT/run/merged.jsonl > $OUT/raw/merged.jsonl.gz
cp $OUT/run/run_config.json $OUT/raw/run_config.json
cp $OUT/run/machine.json $OUT/raw/machine.json

echo "=== $(date -u +%H:%M:%SZ) $NAME: scoring ==="
python3 scripts/experiments/score_region_corpus.py \
    --results $OUT/run/merged.jsonl --references $OUT/regions_left.csv \
    --manifest $OUT/manifest.csv --out $OUT/scored > $OUT/scored-summary.txt 2>&1

# Only now are the seeds spent. Also checks the batch is whole: a record count that does not match
# the manifest means something was lost, and burning the seeds would make it unrepeatable.
python3 - "$NAME" <<'PY'
import csv, json, pathlib, sys
base = pathlib.Path("results/formal-v1") / sys.argv[1]
manifest = {r["pair_id"] for r in csv.DictReader((base / "exec_manifest.csv").open())}
seen = set()
for line in (base / "run" / "merged.jsonl").read_text(errors="replace").splitlines():
    if line.startswith('{"schemaVersion"'):
        seen.add(json.loads(line)["pairId"])
if seen != manifest:
    raise SystemExit(f"[{sys.argv[1]}] INCOMPLETE: {len(manifest)} planned, {len(seen)} recorded; "
                     f"seeds NOT marked used")
used = pathlib.Path("results/formal-v1/used_seeds.txt")
seeds = [r["seed_file"] for r in csv.DictReader((base / "manifest_raw.csv").open())]
with used.open("a") as stream:
    stream.write("\n".join(seeds) + "\n")
total = len([l for l in used.read_text().splitlines() if l.strip()])
print(f"[{sys.argv[1]}] complete: {len(seen)} pairs; used-seed list now {total}")
PY

echo "=== $(date -u +%H:%M:%SZ) $NAME complete ==="
