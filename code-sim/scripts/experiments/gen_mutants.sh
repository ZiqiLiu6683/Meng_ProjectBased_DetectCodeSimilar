#!/bin/bash
# ---------------------------------------------------------------------------
# Mutation-based clone-pair generator (Roy & Cordy Mutation and Injection
# Framework operators). Applies validated mutation operators to seed Java
# programs to synthesise (original, mutant) pairs of KNOWN clone type.
#
# Runs inside WSL (needs txl + the built MutationInjectionFramework).
# Output pairs are then copied to Windows and run through BatchPairMain.
#
# Operator -> clone-type mapping (Roy & Cordy editing taxonomy):
#   T1 (layout/comments): mCW_A mCW_R mCC_BT mCC_EOL mCF_A mCF_R
#   T2 (rename/literals):  mSRI mARI mRL_N mRL_S
#   T3 (statement edits):  mIL mDL mML mSIL mSDL
#
# Usage:
#   gen_mutants.sh <framework_dir> <seed_dir> <out_dir> [per_operator]
#     framework_dir : ~/MutationInjectionFramework
#     seed_dir      : directory of candidate seed .java files (searched recursively)
#     out_dir       : where pairs/manifest/labels are written
#     per_operator  : target pairs per operator (default 400 -> 6000 total)
#
# Resume-safe: existing pair directories are skipped.
# ---------------------------------------------------------------------------
set -u

FRAMEWORK="${1:?framework dir}"
SEED_DIR="${2:?seed dir}"
OUT="${3:?out dir}"
PER_OP="${4:-400}"

TXL="$(command -v txl || echo /usr/local/bin/txl)"
OPDIR="$FRAMEWORK/mutators/java"
PAIRS="$OUT/pairs"
MANIFEST="$OUT/manifest.csv"
LABELS="$OUT/labels.csv"
mkdir -p "$PAIRS"

# operator  clone_type
OPS_T1="mCW_A mCW_R mCC_BT mCC_EOL mCF_A mCF_R"
OPS_T2="mSRI mARI mRL_N mRL_S"
OPS_T3="mIL mDL mML mSIL mSDL"

clone_type() {
  case " $OPS_T1 " in *" $1 "*) echo T1; return;; esac
  case " $OPS_T2 " in *" $1 "*) echo T2; return;; esac
  case " $OPS_T3 " in *" $1 "*) echo T3; return;; esac
  echo UNKNOWN
}

# headers (only if fresh)
[ -f "$MANIFEST" ] || echo "pair_id,left_path,right_path" > "$MANIFEST"
[ -f "$LABELS" ]   || echo "pair_id,operator,clone_type" > "$LABELS"

# collect seeds: reasonable-size single-type files parse fastest and are more
# likely to be self-contained. Filter 20..400 lines to avoid trivial/huge files.
mapfile -t SEEDS < <(find "$SEED_DIR" -name '*.java' -type f \
  -exec bash -c 'n=$(wc -l < "$1"); [ "$n" -ge 20 ] && [ "$n" -le 400 ] && echo "$1"' _ {} \; 2>/dev/null | shuf --random-source=<(yes 42))

echo "[gen] ${#SEEDS[@]} candidate seeds; target $PER_OP pairs/operator"

for OP in $OPS_T1 $OPS_T2 $OPS_T3; do
  TXLPROG="$OPDIR/$OP.txl"
  if [ ! -f "$TXLPROG" ]; then echo "[gen] missing operator $OP, skip"; continue; fi
  CT=$(clone_type "$OP")
  made=0; tried=0
  for SEED in "${SEEDS[@]}"; do
    [ "$made" -ge "$PER_OP" ] && break
    tried=$((tried+1))
    PID="${OP}_$(printf '%05d' "$made")"
    D="$PAIRS/$PID"
    [ -d "$D" ] && { made=$((made+1)); continue; }   # resume
    mkdir -p "$D"
    cp "$SEED" "$D/Original.java"
    # apply operator; ignore stderr noise, cap time so a pathological seed can't hang
    if timeout 60 "$TXL" -o "$D/Mutant.java" "$D/Original.java" "$TXLPROG" >/dev/null 2>&1 \
       && [ -s "$D/Mutant.java" ] \
       && ! diff -q "$D/Original.java" "$D/Mutant.java" >/dev/null 2>&1; then
      echo "$PID,$(readlink -f "$D/Original.java"),$(readlink -f "$D/Mutant.java")" >> "$MANIFEST"
      echo "$PID,$OP,$CT" >> "$LABELS"
      made=$((made+1))
    else
      rm -rf "$D"   # operator failed or produced no change on this seed
    fi
  done
  echo "[gen] $OP ($CT): $made pairs (from $tried seeds tried)"
done

echo "[gen] done. pairs: $(($(wc -l < "$MANIFEST") - 1))  ->  $OUT"
