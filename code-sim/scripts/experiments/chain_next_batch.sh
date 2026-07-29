#!/bin/zsh
# Start batch 3 once batch 2 has finished SUCCESSFULLY.
#
# The gate matters: batch.sh appends a batch's seeds to used_seeds.txt only after verifying the
# record count matches the manifest. If batch 2 failed, its seeds are deliberately not marked used,
# and starting batch 3 anyway would hand it the same seed pool -- producing near-duplicate pairs
# that the statistics would treat as independent. So batch 3 waits for the seed list to actually
# grow, not merely for batch 2's process to exit.

SIM=/Users/liuziqi/Documents/西大Graduate/Project_base/Code/Meng_ProjectBased_DetectCodeSimilar/code-sim
USED=$SIM/results/formal-v1/used_seeds.txt
SCRATCH=$(dirname $0)
EXPECT=3000          # batches 1-3
DEADLINE=$((SECONDS + 8 * 3600))

echo "[chain] waiting for batch3 to complete (expecting $EXPECT used seeds)"
while true; do
    count=$(grep -c . $USED 2>/dev/null || echo 0)
    if [ "$count" -ge "$EXPECT" ]; then
        echo "[chain] $(date -u +%H:%M:%SZ) batch3 complete, used seeds now $count"
        break
    fi
    if [ $SECONDS -gt $DEADLINE ]; then
        echo "[chain] TIMED OUT after 8h with $count used seeds; batch3 NOT started"
        exit 1
    fi
    if ! pgrep -f "BatchPairMain" >/dev/null 2>&1 && ! pgrep -f "batch.sh batch3" >/dev/null 2>&1; then
        # Nothing is running and the seed list never grew: batch 2 ended without completing.
        sleep 120
        count=$(grep -c . $USED 2>/dev/null || echo 0)
        if [ "$count" -lt "$EXPECT" ]; then
            echo "[chain] batch3 stopped without completing ($count used seeds); batch3 NOT started"
            exit 1
        fi
    fi
    sleep 120
done

echo "[chain] $(date -u +%H:%M:%SZ) starting batch4"
zsh $(dirname $0)/run_batch.sh batch4 1000 20260731
