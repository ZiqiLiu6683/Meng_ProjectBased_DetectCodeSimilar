#!/bin/zsh
# Wait for batch 4, correct and analyse batches 1-4, then start batch 5.
#
# The gate is that used_seeds.txt GROWS to 4000, not that batch 4's process exits. run_batch.sh
# appends a batch's seeds only after verifying its record count matches its manifest, so a batch that
# failed deliberately leaves its seeds unspent -- and starting batch 5 anyway would hand it the same
# pool and produce near-duplicate pairs the statistics would treat as independent.
#
# Batch 4 was generated before the insert/delete operator fixes, so it needs the same two offline
# ground-truth corrections already applied to batches 1-3, and it belongs in the same arm. Batch 5
# uses the corrected operators and therefore forms a SEPARATE arm; it is generated here but not
# merged into the batch 1-4 analysis.
set -e

SIM=/Users/liuziqi/Documents/西大Graduate/Project_base/Code/Meng_ProjectBased_DetectCodeSimilar/code-sim
USED=$SIM/results/formal-v1/used_seeds.txt
HERE=$(cd $(dirname $0) && pwd)
EXPECT=4000
DEADLINE=$((SECONDS + 10 * 3600))

cd $SIM
echo "[chain5] waiting for batch4 (expecting $EXPECT used seeds)"
while true; do
    count=$(grep -c . $USED 2>/dev/null || echo 0)
    if [ "$count" -ge "$EXPECT" ]; then
        echo "[chain5] $(date -u +%H:%M:%SZ) batch4 complete, used seeds $count"
        break
    fi
    if [ $SECONDS -gt $DEADLINE ]; then
        echo "[chain5] TIMED OUT with $count used seeds; batch5 NOT started"
        exit 1
    fi
    if ! pgrep -f BatchPairMain >/dev/null 2>&1 && ! pgrep -f "run_batch.sh batch4" >/dev/null 2>&1; then
        sleep 120
        count=$(grep -c . $USED 2>/dev/null || echo 0)
        if [ "$count" -lt "$EXPECT" ]; then
            echo "[chain5] batch4 stopped without completing ($count seeds); batch5 NOT started"
            exit 1
        fi
    fi
    sleep 120
done

echo "[chain5] $(date -u +%H:%M:%SZ) applying ground-truth corrections to batch4"
python3 $HERE/correct_wrap_intervals.py batch4
python3 $HERE/split_mutation_runs.py batch4
python3 scripts/experiments/score_region_corpus.py \
    --results results/formal-v1/batch4/run/merged.jsonl \
    --references results/formal-v1/batch4/regions_left_v3.csv \
    --manifest results/formal-v1/batch4/manifest.csv \
    --out results/formal-v1/batch4/scored_v3 >/dev/null

echo "[chain5] $(date -u +%H:%M:%SZ) analysing batches 1-4"
python3 $HERE/analyze_region_corpus.py \
    --batches batch1,batch2,batch3,batch4 --scored scored_v3 \
    --out results/formal-v1/ANALYSIS.md >/tmp/analysis-1234.txt 2>&1
tail -30 /tmp/analysis-1234.txt

echo "[chain5] $(date -u +%H:%M:%SZ) starting batch5 (corrected operators — SEPARATE arm)"
zsh $HERE/run_batch.sh batch5 1000 20260801
