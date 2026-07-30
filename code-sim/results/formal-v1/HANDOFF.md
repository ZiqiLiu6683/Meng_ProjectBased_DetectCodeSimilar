# Handoff — region corpus formal run

Written 2026-07-29T22:52Z because a conversation was ending. Everything below is state, evidence and
open decisions, recorded in full rather than summarised. Read this first, then `FREEZE.md` (frozen
configuration), then `RESULTS.md` (results as of batches 1–3, **now partly superseded — see §5**).

---

## 1. What is running right now

| | |
| --- | --- |
| **batch4** | 619 / 1000 at 23:21Z, ~5 h remaining. Mean 95.2 s/pair — inflated by my own concurrent work again, same as batch 2. |
| chain | `chain_batch5.sh` is running: waits for 4000 used seeds, applies the two ground-truth corrections to batch 4, writes `ANALYSIS.md` for batches 1–4, then starts batch 5. |
| used seeds | 3000 (batches 1–3). batch4's are appended only when it verifies complete. |
| host state | 2 JVMs × `-Xmx1600m`, ~1 GB RAM free, 49 GiB disk |

**Do not run anything heavy on this machine while a batch is running.** Batch 2 took 13 h 50 m
instead of ~8 h purely because other work (corpus scans, generation, git) was competing for an 8 GB
host and swap hit 9.8 of 10 GB. Same inputs, every stage slower. Batch 3, run alone, returned to
batch 1's timings. Confirmed by measurement, not inference.

**Do not edit a script while it is running.** Batch 2 exited 1 because `batch.sh` was edited
mid-run; zsh reads scripts by byte offset, so the file growing shifted its parse position. The data
was fine but the raw-output preservation step never executed and had to be done by hand.

## 2. Commands to resume

```bash
# progress of a running batch
cd code-sim && cat results/formal-v1/batchN/run/shard_00*.jsonl | grep -c '^{"schemaVersion"'

# start a batch (name, pairs, rng seed). Excludes seeds already used; appends its own only on success
zsh scripts/experiments/run_batch.sh batch5 1000 20260801

# chain one batch behind another (waits for used_seeds.txt to GROW, not for the process to exit)
zsh scripts/experiments/chain_next_batch.sh          # template; EXPECT= must be the target used-seed count

# score a batch
python3 scripts/experiments/score_region_corpus.py \
    --results results/formal-v1/batchN/run/merged.jsonl \
    --references results/formal-v1/batchN/regions_left.csv \
    --manifest results/formal-v1/batchN/manifest.csv \
    --out results/formal-v1/batchN/scored

# tests. JAVA_HOME is mandatory: the Maven enforcer rejects any JDK but 17
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -Psemantic-analysis test     # 81 tests
python3 -m unittest scripts.experiments.tests.test_score_region_corpus  # 14 tests
```

**The scripts have been rescued out of the session scratchpad into the repo** and are committed:

| Script | Was | Does |
| --- | --- | --- |
| `scripts/experiments/run_batch.sh` | `batch.sh` | one batch end to end: generate, run, preserve raw, score, spend seeds |
| `scripts/experiments/chain_next_batch.sh` | `chain4.sh` | start the next batch once `used_seeds.txt` GROWS (not merely when the process exits) |
| `scripts/experiments/cap_wala_logs.sh` | `logcap.sh` | truncate WALA call-graph tracing in place above a size cap |
| `scripts/experiments/correct_wrap_intervals.py` | `/tmp/fix3.py` | GT round 1: wrap interval = the added statement's own line |
| `scripts/experiments/split_mutation_runs.py` | `/tmp/fix_runs.py` | GT round 3: split mutation references into contiguous runs |

`chain_next_batch.sh` carries `EXPECT=3000` inline; edit it to the target used-seed count before
chaining. §9 still describes the batch sequence in case any of it needs rebuilding.

## 2a. Batch 5 is a SEPARATE ARM — do not merge it with batches 1–4

The insert and delete operator fixes (§5 round 2) change the **corpus**, not the reference, and they
landed after batch 4 was generated. So:

| | batches 1–4 | batch 5 onward |
| --- | --- | --- |
| insert | `int x = 95;` | `int x = ((37 % 8) + (5 * 3));` |
| delete | any single-line deletable statement | prefers one with no Type-2-normalised twin |

Those are different operator definitions. Averaging them into one per-operator recall figure would
mix two definitions. `analyze_region_corpus.py` takes `--batches` for exactly this reason and its
output states the caveat.

Batch 5's real value is as that separate arm: it turns "the residual insert/delete errors are a
corpus artefact, not a detector limitation" from an argument into a measurement. Predicted: insert
and delete rise from ~88–90 % to ~99 %.

**After the round-1 and round-3 reference corrections, the only remaining type errors across
batches 1–3 are exactly those two artefacts** — `t3_delete_statement`→T2 33 and
`t3_insert_statement`→T2 26, 59 cases in 3,291 (1.8 %). Nothing else is mistyped. That is the
strongest statement available about the cascade's type attribution, and batch 5 is what would close
it.

## 3. What the experiment is

Adapted MIF (protocol E2). One CodeNet Java250 file is the seed; the pair is (Spoon-printed seed,
Spoon-printed seed + one known mutation). References come in three kinds, per protocol §6:

| Reference | Scored against | Rule | Answers |
| --- | --- | --- | --- |
| `CLONE_INTERVAL` | regions | c-match, min-side coverage ≥ 0.70 | did it find the corresponding block? |
| `MUTATION` | sub-regions | any overlap + type must match | did it identify the edit, and of which kind? |
| `UNTOUCHED` | sub-regions | expected T1 | is unchanged code recognised as T1? |

Ten type-safe operators stand in for the MIF families, assigned from a shuffled balanced schedule.
Full detail in `FREEZE.md` §4.

## 4. Results as of batches 1–3 (3,000 pairs, 244 problems)

Every batch: 1000/1000 records, 0 missing, 0 duplicate, `status=ok`,
`analysisMode=SOURCE_PLUS_WALA_SMT`, **zero fallback**.

### Clone intervals vs regions — near-trivial, do not quote as detection accuracy

97.0 % matched, 96.9 % type correct, conditional type accuracy **99.9 %** (4 errors in 2,911).

**82 % of matches have coverage exactly 1.000** and median boundary precision is **0.400** — the
predicted region is ~2.5× the six-line reference, so the reference falls inside it and c-match is
satisfied almost automatically. Inherent to the corpus: B is A with one small change, so the whole
file corresponds and reporting a large corresponding region is correct. **The informative number
here is the conditional type accuracy, not the 97 %.**

### Mutations vs sub-regions — the discriminating measurement

**Superseded by the batches 1–4 table below.** Kept only so the effect of each GT round stays
auditable; do not quote from it.

## 4a. Results as of batches 1–4 (4,000 pairs, 249 problems, `scored_v4/`) — QUOTE THESE

All four GT rounds applied (§5). `N = 4336 · matched 83.4 % · type correct 81.6 % [80.4, 82.8] ·
ICC 0.0061 · cluster 18.0 · DEFF 1.10`.

| Operator | Captured | Type correct | 95 % CI | Median IoU | Reading |
| --- | ---: | ---: | --- | ---: | --- |
| `t3_wrap_statement` | 99.8 % | **99.8 %** | [98.5, 100] | 1.000 | ✅ (was 7.3 % under the original GT) |
| `t2_change_string_literal` | 99.7 % | **99.7 %** | [98.5, 100] | 1.000 | ✅ |
| `t2_rename_local` | 99.5 % | **99.5 %** | [98.6, 99.8] | 1.000 | ✅ (was 88.3 %) |
| `t1_reindent` | 98.7 % | **98.7 %** | [97.0, 99.5] | 0.357 | ✅ (94.8 % under round 3) |
| `t2_change_int_literal` | 98.5 % | **98.3 %** | [96.3, 99.2] | 1.000 | ✅ |
| `t3_delete_statement` | 98.7 % | 89.0 % | [85.3, 91.8] | 1.000 | corpus artifact, §5 round 2 |
| `t3_insert_statement` | 98.7 % | 88.8 % | [85.1, 91.7] | 1.000 | corpus artifact, §5 round 2 |
| `t1_add_eol_comment` | 78.2 % | 78.2 % | [73.6, 82.1] | 0.062 | representational, §6.9 |
| `t1_add_blank_line` | 24.9 % | 24.9 % | [20.7, 29.6] | 0.059 | **not measurable**, §6.9 |
| `t1_add_block_comment` | 24.0 % | 24.0 % | [19.9, 28.7] | 0.059 | **not measurable**, §6.9 |

**Five operators sit at 98.3–99.8 % with IoU 1.000 — the mutated line is identified exactly, and the
right clone type assigned to it.** The three remaining low figures each have a documented cause
outside the detector: insert/delete is the corpus artifact fixed in the generator (batch 5 validates
it), and blank-line/block-comment is a scale mismatch that makes the operator untestable rather than
undetected.

Every failure decomposes to *never captured* 16.6 % vs *captured, typed wrongly* 1.8 %, and 691 of
the 718 misses are the two untestable layout operators.

### Untouched runs

6,011 references, 98.3 % matched, 98.2 % typed T1, one misclassification. T1 had never been
measured before this corpus.

### Negatives (100-pair pilot only; final scale 1,000 not yet run)

81 / 100 correct rejection. 19 / 100 emitted something. **2 / 100 claimed a syntactic type.**
Emitted: `POSSIBLE_T4_CANDIDATE` 21, T2 1, T3 1. All 21 came from
`NextRegionTypeRecognizer.java:197` — a cross-method aligned region is a structural fact Phase A
established and *must not be silently dropped by a similarity number*, so it is surfaced as a
possible clone; unrelated competition code shares enough IO scaffolding to align across methods.

### Measured ICC (replaces the assumed 0.05)

| Reference kind | ICC | Refs per problem | DEFF |
| --- | ---: | ---: | ---: |
| clone interval | 0.038 | 12.3 | 1.42 |
| mutation | 0.008 | 12.3 | 1.09 |
| untouched | 0.041 | 24.6 | 1.98 |

Wilson intervals on the corrected sample size: aggregates ±0.5–1.5 %, per-operator ±3–5 %.
**The freeze's ±4 % per-operator target is met at 3,000 pairs, not 5,000.**

## 5. Ground-truth corrections — full history, because this is the risk area

**Three rounds of GT correction have been applied. A fourth is proposed and NOT yet done.** Every
round changed reported numbers, which is exactly why the reasoning must be auditable. Detector
output was never touched; only references were recomputed and the preserved raw output rescored.

### Round 1 — `t3_wrap_statement`, 7.3 % → 99.7 %

The GT interval spanned the whole replaced block. Wrapping `stmt;` in `if (true) { stmt; }` leaves
the statement's own tokens untouched and adds two lines; a line diff calls the whole thing a replace
because the statement is re-indented. So the reference claimed the statement's line as mutated —
but the genuinely added line has **no left-hand counterpart**, so requiring coverage on both sides
made the correct T3 sub-region unmatchable and the unchanged statement's T1 sub-region won.

Evidence, `R00006`: GT left 13-13 / right 13-15; sub-regions were `T3 left[] right[(13,13)]` (the
`if (true) {`) and `T1 left[(13,18)] right[(14,14),(16,20)]`. The detector was right.

Three attempts before it was right:

| Attempt | Interval definition | Result |
| --- | --- | ---: |
| original | every changed line in the range, both sides | 7.3 % |
| 1 | lines with no counterpart in the reference's own left extent | 68.4 % |
| 2 | whole-file indentation-insensitive diff, `min(added)..max(added)` | **57.5 %, worse** |
| 3 | **the added statement's own line** | **99.7 %** |

Attempt 2 was worse because `max(added)` is the closing brace, past the whole loop, so the interval
swallowed the wrapped statement again. Attempt 3's justification is *not* that the number improved:
wrap adds **one statement**, and a statement's own extent excludes what is nested inside it — which
is why `ownLineSpan` reports an `if` as its header line. The closing brace is not a statement.

### Round 2 — insert and delete operator artifacts, NOT yet fixed in data

`t3_insert_statement` 89.8 %, `t3_delete_statement` 88.0 %. The residual failures are **corpus
artifacts, not detector errors**:

- Insert: the inserted `int rIns5226 = 95;` normalises to `int ID = NUM ;`, which is what *every*
  int declaration becomes, so the statement-level LCS pairs the insertion with an existing
  declaration and reports a **rename**. Evidence `R00091`: GT right 12-12, IoU 1.000 (interval
  exact), sub-region `T2 left(12,12) right(12,12)`. Under Type-2 normalisation those statements
  *are* identical — the detector is right.
- Delete: the mirror case. Deleting a statement with a normalised twin lets the LCS re-pair the
  survivors and report a rename.

**Fixed in the generator** (compiled, verified on a fresh 40-pair sample: 3/3 wrap intervals now
point at the `if (true) {` line alone, left side empty):
- insert now uses a compound initialiser `((a % b) + (c * d))`, a token shape ordinary declarations
  lack;
- delete prefers a statement whose normalised shape is unique in its method, falling back rather
  than dropping the pair.

**These change the corpus, so they apply only to batches generated from now on.** Batches 1–4 keep
the artifact and it must be reported as a limitation of those batches. **The verification corpus is
already generated at `/tmp/rc-verify` (150 pairs, seed 888) and has NOT been run** — deliberately,
to avoid competing with batch 4. Run it when the machine is free to confirm insert/delete rise to
~99 %.

### Round 3 — mutation references split into contiguous runs, 88.3 % → 99.3 % for rename

The GT recorded one interval from the first changed line to the last. A rename touches its
declaration and each use, which are **scattered**, so the interval also claimed the untouched lines
between them; the scorer, picking the best-overlapping sub-region, then matched a T1 gap.

Evidence `R00365`: GT left 23-30 (8 lines). Sub-regions: `T2(23) T1(24-26) T2(27) T1(28) T2(30)` —
the detector marked every renamed line T2 and every gap T1, exactly right. Now one reference per
contiguous run.

### Round 4 — APPLIED 2026-07-29, sanctioned by the user; split only scattered-effect operators

Round 3 dropped `t1_reindent` from 99.3 % to **94.8 %** and grew its references from 297 to 346.
Re-indentation changes a contiguous span, but the diff breaks at unchanged lines inside it (blank
lines), so splitting produced fragments, some landing where the detector sees no change.

**Rule now in force** (`split_mutation_runs.py`): split into contiguous runs **only** when the
operator's effect is intrinsically scattered — currently `t2_rename*` alone. Every other operator
keeps its single interval, wrap included, since `correct_wrap_intervals.py` has already reduced that
one to the added statement's own line.

The justification is the operator's nature, not the resulting number: a rename touches a declaration
and each use, which are separate positions by definition, whereas re-indentation shifts one
continuous span. The interval's shape follows what the operator does. This was put to the user with
the concern stated explicitly — three GT rounds had already moved figures, so a fourth that happens
to raise one needed a human decision — and the user sanctioned it.

Applied to all four batches: 64 / 45 / 55 / 59 references split. **`t1_reindent` recovered to
98.7 %** and no other operator's figure changed. Scored into `scored_v4/`.

### Where each GT version lives

| File | Content |
| --- | --- |
| `batchN/regions_left.csv` | as generated (original intervals) |
| `batchN/regions_left_v2.csv` | round 1 — wrap corrected |
| `batchN/regions_left_v3.csv` | round 4 — plus rename runs split (in place; round 3 re-derived) |
| `batchN/scored/`, `scored_v2/`, `scored_v3/`, `scored_v4/` | scoring of each, same raw output |

`regions_left_v3.csv` currently holds the round-4 definition — the file name was kept and the
content re-derived from `regions_left_v2.csv`, so `scored_v3/` (round 3) and `scored_v4/` (round 4)
are the two that can be compared. **Quote `scored_v4/`.**

## 6. Findings that are settled and should reach the paper

1. **A region is not a contiguous span.** `beginLine`/`endLine` is a bounding box over the region's
   statements. 58 % of regions consist of more than one run; the worst case reported 13 lines of
   content as an 89-line span (8.7×). `CodeRegion.segments` now carries the real runs. Fixed and
   committed; the SPA highlights by segments and the region label shows the true line count.
2. **A region carries one type while holding several relationships.** 92 of 156 regions (59 %) had
   both rename evidence and statement edits and were reported T3 alone. `RegionDecision.subRegions`
   applies the same T1→T2→T3 cascade per aligned statement pair. 636 sub-regions surfaced 150 T2
   runs where the region scale reported 11. Fixed and committed.
3. **Both changes are additive.** After each, the full 100-pair corpus was re-run and, with the new
   field removed, results were byte-identical in all 100 pairs. Tests stayed 81/81.
4. **`--skip-dynamic` changes no syntactic verdict.** Over the 59 pairs completed both ways: 0
   detection and 0 type differences. Freezing it costs nothing for T1–T3.
5. **NON_CLONE means "this proposed correspondence is not a clone", not "this code is original".**
   Single production site, the cascade's fall-through, applied to a candidate Phase A already
   proposed. Code with no counterpart never becomes a candidate, so **a donor block can never be
   labelled NON_CLONE** — the reference asked for a label the output cannot express.
6. **Unrelated code does not stop region growth.** Hypothesis falsified on 20 pairs: 85 % still
   produced exactly one region (82 % without the donor) and the donor lines fell inside a predicted
   region 20/20 times. Growth continues because the code either side still corresponds. But the
   sub-region breakdown isolates the donor exactly. The donor path is kept, off by default.
7. **Scaffolding volume does not predict false positives.** Shared consecutive tokens: 16 median in
   negatives that misfired, 15 in those that did not. Raising the token threshold when selecting
   negatives would not help.
8. **Timing here is planning data, not performance evidence.** §9 requires a fixed CPU allocation,
   one measured worker and warm-up; none applies. Batch 2's 1.66× cost was host contention.
9. **`t1_add_blank_line` and `t1_add_block_comment` are untestable at sub-region scale — their ~24 %
   is not a detection rate and must not be quoted as one.** Measured, not argued: of 798 such
   references, 195 matched, and **195/195 (100 %) were covered incidentally** by a sub-region
   spanning ≥ 3 lines (median 8, p90 12). Not one match came from an element pointing at the inserted
   line. Sub-regions are built from statement extents and a blank or comment-only line belongs to no
   statement, so no sub-region can ever correspond to one; the 24 % is simply the chance the
   insertion landed inside some multi-line statement. Report alongside `mARI` and `mSIL` as
   untestable on a compilation-based detector. It is also correct product behaviour: adding only a
   blank line or a comment highlights nothing, because no code changed. `t1_add_eol_comment` at
   78.2 % is the same mechanism partly escaped — an end-of-line comment rides on a real statement,
   whose extent usually covers it.

## 7. Open items

1. ~~Round-4 GT rule~~ **CLOSED 2026-07-29** — sanctioned by the user, applied to all four batches,
   scored into `scored_v4/`. `t1_reindent` 94.8 % → 98.7 %.
2. **Insert/delete operator fixes are unvalidated.** `/tmp/rc-verify` (150 pairs) is generated and
   waiting for a free machine.
3. **`t2_rename_local`, one reference** whose normalised text did not match during GT verification —
   found long ago, never explained.
4. **Naming.** A region typed T2 and a sub-region typed T2 mean different things. The write-up
   cannot use one word for both. Undecided.
5. **Suppression uses bounding boxes.** `AcceptedRegionSelector.overlapRatio` computes containment
   from `beginLine`/`endLine` while 58 % of regions are non-contiguous, and 58.9 % of accepted
   regions are suppressed. Deliberately unchanged — fixing it changes detector behaviour and needs
   its own measurement.
6. **NON_CLONE decisions are invisible.** Filtered before selection, emitted only under
   `-Dcodesim.emitRejectedRegions=true`. The number of rejected candidates has never been recorded.
7. **No output means "original work."** The distinction a plagiarism reviewer most wants — edited
   versus newly written — is not expressible. Would need a new output concept.
8. **Layout operators, decided not open:** blank-line and block-comment are recorded as untestable
   (§6.9), not as an accuracy gap. No further work; the measurement that settled it is in §6.9.
9. **Sample size decision.** Precision target met at 3,000. Batches 4–5 take per-operator from
   ±4 % to ±3 % for ~14 h, while the two-range and negative strata still have no data at scale. I
   recommended stopping the main stratum and spending the time on those two; **the user said to keep
   going with the original plan, so batch 4 is running and batch 5 is not yet chained.**
10. **`dirtyWorktree=true` on every record.** Verified as untracked *result* files only; source diff
   empty for every batch. The flag cannot distinguish the two cases.

## 8. Remaining work under the current plan

| Stratum | Target | Done | Remaining time |
| --- | ---: | ---: | ---: |
| positive main (1 range) | 5,000 | 3,000 + batch4 running | ~7 h for batch 5 |
| positive multi-relationship (2 ranges) | 600 | **0** | ~8 h |
| negative | 1,000 | 100 (pilot) | ~7 h |

The two-range stratum answers "can several relationships in one pair be separated" — the question
the main stratum cannot touch, since every pair there has exactly one edit. It has no data at all.

Supply: 1 range/pair keeps 21.6 % of attempts, so ~16,200 pairs are available; 2 ranges/pair keeps
0.8 %, capping that stratum at ~600.

## 9. Reconstructing `batch.sh` if the scratchpad is gone

Sequence, with the reasons each step exists:

1. Generate: `java -Dmutgen.excludeSeeds=results/formal-v1/used_seeds.txt -cp
   "mutation-gen/target/mutation-gen-0.1.0.jar:$(cat mutation-gen/target/cp.txt)"
   com.ziqi.mutgen.RegionCorpusGenerator <corpus> <out> <pairs> 1 6 3 <rng>`.
   The exclusion matters: batches shuffle the same corpus with different seeds, so without it a
   later batch silently reuses files and near-duplicate pairs get treated as independent.
2. Build `manifest.csv` (3 columns) from `manifest_raw.csv`, then
   `to_execution_manifest.py` for the checksummed 7-column form.
3. Run: `run_shards.py --workers 2 --shards 8 --xmx 1600m --skip-dynamic
   "--java-opt=-Dcodesim.compileCache=/tmp/cache-<name>"`. Eight shards so a dead shard costs less;
   fresh cache so every batch starts cold and is comparable.
4. Cap logs: an external watchdog truncating each shard log above 150 MB. WALA writes
   `got NEW <...> in Node: <...>` call-graph tracing — 36 GB across four shards in one hour on an
   earlier run, and `BatchPairMain` has no quiet flag. `run_shards` opens logs `O_APPEND`, so
   truncating in place frees the space and the next write restarts at offset 0 with no sparse hole
   (verified 1,000,000 → 0 → 100 bytes).
5. Copy `run/merged.jsonl` to `raw/merged.jsonl.gz` plus `run_config.json` and `machine.json`.
   `run/` is working state and gitignored; the raw output is kept because §10 and §11 require
   rescoring without re-running, and one batch costs ~7 h to regenerate.
6. Score.
7. **Verify record count equals manifest row count, and only then append the batch's seeds to
   `used_seeds.txt`.** A batch that fails must not burn its seeds — supply is finite.

## 10. Files

Under `code-sim/results/formal-v1/`:

| File | Content |
| --- | --- |
| `HANDOFF.md` | this file |
| `FREEZE.md` | frozen configuration + two amendments (commit move, measured ICC) |
| `RESULTS.md` | results write-up for batches 1–3. **Mutation-scale numbers predate rounds 1 and 3 — trust `scored_v3/` and §4/§5 here instead.** |
| `used_seeds.txt` | consumed seeds, cross-batch dedup |
| `batchN/regions_left*.csv` | ground truth, three versions |
| `batchN/scored*/` | scoring of each version |
| `batchN/raw/merged.jsonl.gz` | **raw detector output — 7 h/batch to regenerate, never delete** |
| `batchN/manifest_raw.csv` | pair → seed file → CodeNet problem (the cluster key) |

Elsewhere:

| Path | Content |
| --- | --- |
| `code-sim/docs/decision-log.md` | every decision and every retracted conclusion, newest first |
| `code-sim/evaluation/paper_evaluation_protocol.md` | the preregistered protocol. **Read §4, §6, §7, §10, §11, §12 before changing any measurement** |
| `code-sim/scripts/experiments/score_region_corpus.py` | the scorer |
| `code-sim/scripts/experiments/tests/test_score_region_corpus.py` | 14 tests, verified by deliberately breaking three scoring rules |
| `mutation-gen/` | standalone Spoon generator, own pom, does not touch code-sim's build |
| `mutation-gen/src/.../probes/` | archived one-off diagnostics with a package-info explaining what each answered |

**Uncommitted at handoff time:** `mutation-gen/.../RegionCorpusGenerator.java` (rounds 1–3 fixes),
`batchN/regions_left_v2.csv`, `regions_left_v3.csv`, `scored_v2/`, `scored_v3/`. Commit these.
