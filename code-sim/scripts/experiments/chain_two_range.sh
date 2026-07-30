#!/bin/zsh
# Wait for the negative stratum to finish, score it, then generate and run the two-range stratum.
#
# The gate is that the negatives' record count REACHES its manifest count -- not that the process
# exited. A runner can exit non-zero with a partial shard, and starting the next stratum on a half-
# finished host would both corrupt the timing picture and compete for an 8 GB machine that has
# already been measured losing 1.66x to contention.
#
# The two strata are never run concurrently for that reason.
set -e

SIM=/Users/liuziqi/Documents/西大Graduate/Project_base/Code/Meng_ProjectBased_DetectCodeSimilar/code-sim
GEN=/Users/liuziqi/Documents/西大Graduate/Project_base/Code/Meng_ProjectBased_DetectCodeSimilar/mutation-gen
CORPUS=/Users/liuziqi/codesim-data/extracted/Project_CodeNet_Java250
JAVA_HOME=/opt/homebrew/opt/openjdk@17
JAVA=$JAVA_HOME/bin/java
HERE=$(cd $(dirname $0) && pwd)

NEG=$SIM/results/formal-v1/negatives
OUT=$SIM/results/formal-v1/tworange
USED=$SIM/results/formal-v1/used_seeds.txt
PAIRS=600
DEADLINE=$((SECONDS + 12 * 3600))

cd $SIM
echo "[chain2r] waiting for the negative stratum (expecting 1000 records)"
while true; do
    count=$(cat $NEG/run/shard_00*.jsonl 2>/dev/null | grep -c '^{"schemaVersion"' || echo 0)
    if [ "$count" -ge 1000 ]; then
        echo "[chain2r] $(date -u +%H:%M:%SZ) negatives complete ($count)"
        break
    fi
    if [ $SECONDS -gt $DEADLINE ]; then
        echo "[chain2r] TIMED OUT with $count negative records; two-range NOT started"
        exit 1
    fi
    if ! pgrep -f BatchPairMain >/dev/null 2>&1; then
        sleep 120
        count=$(cat $NEG/run/shard_00*.jsonl 2>/dev/null | grep -c '^{"schemaVersion"' || echo 0)
        if [ "$count" -lt 1000 ]; then
            echo "[chain2r] negatives stopped at $count without completing; two-range NOT started"
            exit 1
        fi
    fi
    sleep 120
done

echo "[chain2r] $(date -u +%H:%M:%SZ) preserving and scoring the negatives"
cat $NEG/run/shard_00*.jsonl > $NEG/run/merged.jsonl
mkdir -p $NEG/raw && gzip -c $NEG/run/merged.jsonl > $NEG/raw/merged.jsonl.gz
python3 $HERE/score_negatives.py \
    --results $NEG/run/merged.jsonl --manifest $NEG/manifest_raw.csv \
    --positives $SIM/results/formal-v1/batch1/run/merged.jsonl,$SIM/results/formal-v1/batch2/run/merged.jsonl,$SIM/results/formal-v1/batch3/run/merged.jsonl,$SIM/results/formal-v1/batch4/run/merged.jsonl,$SIM/results/formal-v1/batch5/run/merged.jsonl \
    --out $NEG/scored > /tmp/negatives-score.txt 2>&1
tail -40 /tmp/negatives-score.txt

echo "[chain2r] $(date -u +%H:%M:%SZ) generating the two-range stratum ($PAIRS pairs, 2 ranges)"
cd $GEN
rm -rf $OUT
# Same operator set and geometry as the main stratum; only the range count differs, so any
# difference in the result is attributable to there being two relationships rather than one.
$JAVA -Dmutgen.excludeSeeds=$USED \
    -cp "target/mutation-gen-0.1.0.jar:$(cat target/cp.txt)" \
    com.ziqi.mutgen.RegionCorpusGenerator "$CORPUS" "$OUT" $PAIRS 2 6 3 20260803 2>&1 \
    | grep -v SLF4J | tail -16

cd $SIM
python3 - <<'PY'
import csv, os, pathlib
base = pathlib.Path("results/formal-v1/tworange")
rows = list(csv.DictReader((base / "manifest_raw.csv").open()))
with (base / "manifest.csv").open("w", newline="") as fh:
    writer = csv.writer(fh)
    writer.writerow(["pair_id", "left_path", "right_path"])
    for row in rows:
        writer.writerow([row["pair_id"], os.path.realpath(row["left_path"]),
                         os.path.realpath(row["right_path"])])
print(f"[chain2r] manifest rows: {len(rows)}")
PY
python3 scripts/experiments/to_execution_manifest.py \
    --in $OUT/manifest.csv --dataset-id formal-v1-tworange --out $OUT/exec_manifest.csv

echo "[chain2r] $(date -u +%H:%M:%SZ) running the two-range stratum"
rm -rf /tmp/cache-tworange
JAVA_HOME=$JAVA_HOME python3 scripts/experiments/run_shards.py \
    --manifest $OUT/exec_manifest.csv --out $OUT/run \
    --config-id formal-v1-tworange --environment-id mac-m-local-8gb \
    --java $JAVA --workers 2 --shards 8 --xmx 1600m --skip-dynamic \
    "--java-opt=-Dcodesim.compileCache=/tmp/cache-tworange"

echo "[chain2r] $(date -u +%H:%M:%SZ) preserving raw output"
cat $OUT/run/shard_00*.jsonl > $OUT/run/merged.jsonl
mkdir -p $OUT/raw && gzip -c $OUT/run/merged.jsonl > $OUT/raw/merged.jsonl.gz
echo "[chain2r] done. Score with score_region_corpus.py against $OUT/regions_left.csv"
