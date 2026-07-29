# Region corpus, batches 1–3 — results

3,000 pairs, 12,002 references, 244 CodeNet problems. Configuration frozen in [`FREEZE.md`](FREEZE.md);
raw product output preserved per batch in `batchN/raw/merged.jsonl.gz`, so every table below can be
regenerated without re-running the detector.

Written while batch 4 is still running. Nothing here is final; it is recorded now so the findings
are not carried in anyone's head.

## 1. Run inventory

| Batch | Pairs | Commit | Wall clock | Mean s/pair | Complete |
| --- | ---: | --- | ---: | ---: | :---: |
| batch1 | 1,000 | `42e320d` | 7 h 47 m | 55.9 | 1000/1000 |
| batch2 | 1,000 | `42e320d` | 13 h 50 m | 99.6 | 1000/1000 |
| batch3 | 1,000 | `fab519f` | 7 h 56 m | 53.3 | 1000/1000 |

Every batch: 0 missing records, 0 duplicates, `status=ok` and `analysisMode=SOURCE_PLUS_WALA_SMT`
on all 3,000 — **zero fallback**. The detector source is byte-identical across the two commits
(`git diff --name-only 42e320d fab519f -- code-sim/src/` is empty); see the amendment in
`FREEZE.md`.

Batch 2's cost is **not** a property of its seeds. Input sizes match batch 1 almost exactly (median
26 vs 27 lines, p90 66 vs 73), yet every stage was slower — graph 2.0 → 3.3 s, smt 2.3 → 4.1 s,
regions 36.1 → 41.9 s. Swap was near-exhausted (9.8 of 10 GB) because other work was running on the
same 8 GB host. Batch 3, run with nothing else competing, returned to batch 1's timings.

**These timings are planning data, not performance evidence.** §9 requires a fixed CPU allocation,
one measured worker and explicit warm-up; none of that applies here.

## 2. Measured intra-class correlation

The freeze assumed ICC = 0.05 for sizing. Measured, clustered by CodeNet problem:

| Reference kind | ICC | Pairs per problem | DEFF |
| --- | ---: | ---: | ---: |
| clone interval | 0.038 | 12.3 | 1.42 |
| mutation | 0.008 | 12.3 | 1.09 |
| untouched | 0.041 | 24.6 | 1.98 |

The assumption was conservative, so the intervals below are **narrower** than planned. All
confidence intervals are Wilson intervals on the design-effect-corrected sample size.

**The precision target is already met at 3,000 pairs.** The freeze aimed for ±4 % per operator at
p = 0.9 with 5,000 pairs; at 3,000 the per-operator intervals are ±3–5 % and the aggregates ±0.5–1.5 %.

## 3. Clone intervals vs regions — near-trivial by construction

| Expected | Operator | N | Matched | Type correct | Conditional | Median IoU | 95 % CI (type) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| T1 | `t1_add_blank_line` | 299 | 97.3 % | 97.3 % | 100 % | 0.400 | [94.2, 98.8] |
| T1 | `t1_add_block_comment` | 299 | 97.0 % | 97.0 % | 100 % | 0.398 | [93.7, 98.6] |
| T1 | `t1_add_eol_comment` | 302 | 96.0 % | 96.0 % | 100 % | 0.400 | [92.5, 97.9] |
| T1 | `t1_reindent` | 297 | 96.3 % | 96.3 % | 100 % | 0.409 | [92.8, 98.1] |
| T2 | `t2_change_int_literal` | 301 | 97.7 % | 97.0 % | 99.3 % | 0.404 | [93.7, 98.6] |
| T2 | `t2_change_string_literal` | 300 | 98.3 % | 98.3 % | 100 % | 0.462 | [95.5, 99.4] |
| T2 | `t2_rename_local` | 300 | 98.0 % | 98.0 % | 100 % | 0.500 | [95.1, 99.2] |
| T3 | `t3_delete_statement` | 300 | 95.3 % | 95.0 % | 99.7 % | 0.333 | [91.2, 97.2] |
| T3 | `t3_insert_statement` | 301 | 97.0 % | 97.0 % | 100 % | 0.355 | [93.7, 98.6] |
| T3 | `t3_wrap_statement` | 301 | 97.3 % | 97.3 % | 100 % | 0.393 | [94.2, 98.8] |
| **all** | | **3,000** | **97.0 %** | **96.9 %** | **99.9 %** | | [96.1, 97.6] |

**The 97 % detection figure must not be reported as detection accuracy.** Of the matched intervals,
**82 % have coverage exactly 1.000** and the median boundary precision is **0.400** — the predicted
region is about **2.5× the size** of the 6-line reference, so the reference falls entirely inside it
and c-match is satisfied almost automatically.

This is inherent to the corpus, not a scorer defect: B is A with one small change, so the *whole
file* corresponds and the detector reporting a large corresponding region is correct. Asking whether
it found a particular 6-line slice of that is close to a free pass.

**What is informative here is the conditional type accuracy: 99.9 %.** Having matched, the cascade
still had to choose among T1/T2/T3, and it did — 4 errors in 2,911.

## 4. Mutations vs sub-regions — the discriminating measurement

| Expected | Operator | N | Captured | Type correct | Conditional | Median IoU | 95 % CI (type) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| T2 | `t2_change_string_literal` | 300 | 99.7 % | **99.7 %** | 100 % | **1.000** | [98.0, 99.9] |
| T2 | `t2_change_int_literal` | 301 | 98.0 % | **98.0 %** | 100 % | **1.000** | [95.6, 99.1] |
| T1 | `t1_reindent` | 297 | 99.3 % | **99.3 %** | 100 % | 0.357 | [97.5, 99.8] |
| T3 | `t3_insert_statement` | 293 | 98.6 % | 89.8 % | 91.0 % | **1.000** | [85.6, 92.8] |
| T2 | `t2_rename_local` | 300 | 99.0 % | 88.3 % | 89.2 % | 0.833 | [84.0, 91.6] |
| T3 | `t3_delete_statement` | 299 | 99.0 % | 88.0 % | 88.9 % | **1.000** | [83.6, 91.3] |
| T1 | `t1_add_eol_comment` | 302 | 79.8 % | 79.8 % | 100 % | 0.062 | [74.7, 84.1] |
| T1 | `t1_add_blank_line` | 299 | 26.4 % | 26.4 % | 100 % | 0.059 | [21.6, 31.9] |
| T1 | `t1_add_block_comment` | 299 | 23.1 % | 23.1 % | 100 % | 0.062 | [18.5, 28.4] |
| T3 | `t3_wrap_statement` | 301 | 99.7 % | **7.3 %** | 7.3 % | 0.231 | [4.8, 11.0] |
| **all** | | **2,991** | **82.2 %** | **69.9 %** | 85.0 % | | [68.2, 71.6] |

Literal changes and statement insert/delete are localised **to the line** (IoU 1.000).

### Why the aggregate is 69.9 %

Two mechanisms, not one, and neither is ordinary misclassification:

| Failure | Count | Share |
| --- | ---: | ---: |
| never captured | 531 | 17.8 % |
| captured, typed wrongly | 369 | 12.3 % |

**450 of the 531 misses are blank lines and block comments.** Sub-region typing works on
*statements*; a blank line or a comment-only line produces no statement, so no sub-region can
correspond to it. An end-of-line comment attaches to an existing statement, which is why it is
captured 79.8 % of the time instead of 25 %. The system is not missing a change — it is correctly
reporting that no code changed there.

**278 of the 369 wrong types are `t3_wrap_statement`, all typed T1.** Wrapping `stmt;` in
`if (true) { stmt; }` leaves the statement's own tokens untouched, so at statement granularity it
still matches and is T1; the added `if (true) {` is a separate sub-region and is the T3 part. The
reference labels the whole wrapped range T3. Both readings are defensible — this is a granularity
disagreement, not an error.

**The residue is 91 cases (3.0 %) of genuine type confusion**, and only these are worth
investigating: 32 renames typed T1, 33 deletions and 26 insertions typed T2.

Read three ways, all of which should be reported:

| Scope | Type accuracy |
| --- | ---: |
| all ten operators | **69.9 %** |
| excluding blank/block comments (produce no statement) | **83.8 %** |
| also excluding `wrap` (granularity disagreement) | **96.6 %** |

## 5. Untouched runs — Type-1 on code that was not changed

**6,011 references, 98.3 % matched, 98.2 % typed T1** (95 % CI [97.7, 98.6]), one misclassification.
Before this corpus, T1 had never been measured at all.

## 6. Negatives (from the earlier 100-pair stratum, not yet at final scale)

| | value |
| --- | ---: |
| correct rejection (no region emitted) | 81 / 100 |
| strict-product FP (any region emitted) | 19 / 100 |
| **claimed a syntactic type (T1/T2/T3)** | **2 / 100** |

Emitted on negatives: `POSSIBLE_T4_CANDIDATE` 21, T2 1, T3 1. All 21 came from one deliberate branch
(`NextRegionTypeRecognizer.java:197`): a cross-method aligned region is a structural fact Phase A
established and *must not be silently dropped by a similarity number*, so it is surfaced as a
possible clone. On unrelated competition code the shared IO scaffolding is enough to align across
methods. §7 already requires this tier to be reported separately.

**Retracted:** "pairs sharing more scaffolding are the ones that misfire." Shared consecutive
tokens were 16 median in the pairs that produced a false positive and 15 in those that did not.
Raising the token threshold when selecting negatives would not reduce the false-positive rate.

## 7. What this corpus does and does not establish

**Establishes.** Under a clean, isolated single edit in otherwise identical code, region-level type
attribution is essentially perfect (99.9 % conditional), untouched code is recognised as T1 at
98.2 %, and literal and statement-level edits are localised to the line.

**Does not establish.**

- *Detection*, in any demanding sense. The whole file corresponds by construction, so finding a
  6-line slice of it is close to free (82 % of matches have coverage 1.000).
- *Several relationships in one pair.* Every pair here carries exactly one edit. The 600-pair
  two-range stratum exists for this and **has no data yet**.
- *Heavy modification or cross-method restructuring.* Out of scope for these operators.
- *`mARI` and `mSIL`.* Inconsistent renaming and an undeclared placeholder ARE those operators;
  they cannot be made to compile and are untestable on a compilation-based detector.

## 8. Open items

1. **91 genuine type confusions** (3.0 % of mutations) — not yet investigated.
2. **One `t2_rename_local` reference** whose normalised text did not match, found during GT
   verification and never explained.
3. **Naming.** A region typed T2 and a sub-region typed T2 mean different things; the write-up
   cannot use one word for both.
4. **Suppression uses bounding boxes** while 58 % of regions are non-contiguous; 58.9 % of accepted
   regions are suppressed. Unchanged deliberately — fixing it changes detector behaviour.
5. **NON_CLONE decisions are invisible** (`-Dcodesim.emitRejectedRegions=true` would expose them),
   so the number of rejected candidates is unrecorded.
6. **NON_CLONE cannot express "original work."** It is the cascade's fall-through for a candidate
   Phase A proposed; code with no counterpart never becomes a candidate. The distinction a
   plagiarism reviewer most wants — edited versus newly written — has no output.
7. **Sample size.** The precision target is met at 3,000. Batches 4–5 would take the per-operator
   interval from ±4 % to ±3 % for a further ~14 h, while the two-range and negative strata still
   have no data at final scale.
