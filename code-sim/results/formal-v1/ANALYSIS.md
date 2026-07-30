# Region corpus analysis — batch1, batch2, batch3, batch4, batch5

Scored from `scored_v4/`. 5000 pairs, 20462 references, 250 CodeNet problems.

Confidence intervals are Wilson intervals on the sample size corrected by the design effect, clustering by problem per protocol §7.

**The clone-interval match rate is near-trivial by construction** and must not be quoted as detection accuracy: B is A with one small change, so the whole file corresponds and a prediction roughly 2.5x the size of the six-line reference encloses it. The informative figure on those rows is the conditional type accuracy.

## Clone intervals vs regions

N = 5000 · matched 97.1 % · type correct 97.0 % [96.4, 97.6] · ICC 0.0376 · cluster 20.5 · DEFF 1.73

| Operator | N | Matched | Type correct | Conditional | Median IoU | 95 % CI |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `t1_add_blank_line` | 498 | 97.2 % | 97.2 % | 100.0 % | 0.381 | [94.6, 98.6] |
| `t1_add_block_comment` | 500 | 96.2 % | 96.2 % | 100.0 % | 0.385 | [93.3, 97.9] |
| `t1_add_eol_comment` | 503 | 96.8 % | 96.8 % | 100.0 % | 0.389 | [94.1, 98.3] |
| `t1_reindent` | 499 | 96.0 % | 96.0 % | 100.0 % | 0.400 | [93.1, 97.7] |
| `t2_change_int_literal` | 501 | 97.6 % | 97.2 % | 99.6 % | 0.400 | [94.6, 98.6] |
| `t2_change_string_literal` | 499 | 98.4 % | 98.4 % | 100.0 % | 0.462 | [96.2, 99.3] |
| `t2_rename_local` | 499 | 98.2 % | 98.2 % | 100.0 % | 0.500 | [95.9, 99.2] |
| `t3_delete_statement` | 500 | 95.4 % | 95.2 % | 99.8 % | 0.333 | [92.1, 97.1] |
| `t3_insert_statement` | 500 | 97.8 % | 97.6 % | 99.8 % | 0.371 | [95.1, 98.8] |
| `t3_wrap_statement` | 501 | 97.6 % | 97.6 % | 100.0 % | 0.400 | [95.1, 98.8] |

## Mutations vs sub-regions (operators with one definition)

N = 4449 · matched 80.1 % · type correct 80.1 % [78.8, 81.3] · ICC 0.0111 · cluster 18.4 · DEFF 1.19

| Operator | N | Matched | Type correct | Conditional | Median IoU | 95 % CI |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `t1_add_blank_line` | 498 | 24.1 % | 24.1 % | 100.0 % | 0.059 | [20.2, 28.4] |
| `t1_add_block_comment` | 500 | 24.4 % | 24.4 % | 100.0 % | 0.056 | [20.5, 28.7] |
| `t1_add_eol_comment` | 503 | 78.9 % | 78.9 % | 100.0 % | 0.062 | [74.8, 82.5] |
| `t1_reindent` | 499 | 99.0 % | 99.0 % | 100.0 % | 0.357 | [97.5, 99.6] |
| `t2_change_int_literal` | 501 | 98.6 % | 98.4 % | 99.8 % | 1.000 | [96.7, 99.2] |
| `t2_change_string_literal` | 499 | 99.8 % | 99.8 % | 100.0 % | 1.000 | [98.7, 100.0] |
| `t2_rename_local` | 948 | 99.1 % | 99.1 % | 100.0 % | 1.000 | [98.1, 99.5] |
| `t3_wrap_statement` | 501 | 99.6 % | 99.6 % | 100.0 % | 1.000 | [98.4, 99.9] |

## Untouched runs vs sub-regions

N = 10026 · matched 98.1 % · type correct 98.1 % [97.6, 98.5] · ICC 0.0377 · cluster 40.1 · DEFF 2.47

| Operator | N | Matched | Type correct | Conditional | Median IoU | 95 % CI |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `none` | 10026 | 98.1 % | 98.1 % | 100.0 % | 0.333 | [97.6, 98.5] |

## Redefined operators — two arms, never pooled

Old arm: batch1, batch2, batch3, batch4. New arm: batch5.

| Operator | Arm | N | Type correct | 95 % CI |
| --- | --- | ---: | ---: | --- |
| `t3_delete_statement` | old | 399 | 89.0 % | [85.5, 91.7] |
| `t3_delete_statement` | new | 100 | 85.0 % | [76.7, 90.7] |
| `t3_insert_statement` | old | 392 | 88.8 % | [85.3, 91.5] |
| `t3_insert_statement` | new | 96 | 100.0 % | [96.2, 100.0] |

## Failure decomposition (mutations)

| Failure | N | Share |
| --- | ---: | ---: |
| never captured | 886 | 19.9 % |
| captured, typed wrongly | 1 | 0.0 % |

Misses by operator: `t1_add_block_comment` 378, `t1_add_blank_line` 378, `t1_add_eol_comment` 106, `t2_rename_local` 9, `t2_change_int_literal` 7, `t1_reindent` 5, `t3_wrap_statement` 2, `t2_change_string_literal` 1

Wrong types by operator and prediction: `t2_change_int_literal`→T1 1

## How to read the three low figures

- **`t1_add_blank_line` and `t1_add_block_comment` are NOT MEASURABLE at this scale, and their percentages should not be quoted as detection rates.** Sub-regions are built from statement extents, and a blank line or a comment-only line belongs to no statement, so no sub-region can correspond to it. Measured over 798 such references: of the 195 that matched, **100 % were covered incidentally** by a sub-region spanning three lines or more (median 8, p90 12) — none by an element pointing at the inserted line. The 24 % is therefore the probability that an insertion happened to land inside some multi-line statement, which says nothing about detection. Report as untestable, in the same way as `mARI` and `mSIL`. It is also a real product observation: adding only a blank line or a comment produces no highlight, which is semantically right since no code changed.
- `t1_add_eol_comment` at ~78 % is the same mechanism, partially escaped: an end-of-line comment attaches to an existing statement, so that statement's extent usually does cover it.
- **Insert: the corpus-artefact hypothesis is confirmed.** The corrected operator measures 100.0 % [96.2, 100.0] against 88.8 % [85.3, 91.5] for the original — non-overlapping intervals. A plain `int x = 5;` normalises to what every int declaration normalises to, so the statement-level LCS paired the insertion with an existing declaration and reported a rename. The detector was right; the corpus was ambiguous.
- **Delete: the fix did not take, and the reason is measured.** The corrected arm is 85.0 % [76.7, 90.7] against 89.0 % [85.5, 91.7] — no separation. `deleteStatement` prefers a statement whose Type-2-normalised shape is unique in its method but **falls back to a twinned one rather than dropping the pair**, and the fallback fires often. Stratified over batch 5: deletions of a unique-shape statement are **65 / 65 correct (100 %)**, deletions of a twinned statement 18 / 32 (56 %), and **all 14 measurable failures are twinned, none unique**. So the operator is right when it applies and reintroduces the original artefact when it cannot. Report delete stratified by whether a normalised twin existed; the twinned cases ask the detector to say which of two indistinguishable statements was removed, which the ground truth cannot settle either.
