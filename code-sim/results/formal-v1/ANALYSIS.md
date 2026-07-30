# Region corpus analysis — batch1, batch2, batch3, batch4

Scored from `scored_v4/`. 4000 pairs, 16353 references, 249 CodeNet problems.

Confidence intervals are Wilson intervals on the sample size corrected by the design effect, clustering by problem per protocol §7.

**The clone-interval match rate is near-trivial by construction** and must not be quoted as detection accuracy: B is A with one small change, so the whole file corresponds and a prediction roughly 2.5x the size of the six-line reference encloses it. The informative figure on those rows is the conditional type accuracy.

## Clone intervals vs regions

N = 4000 · matched 97.0 % · type correct 96.9 % [96.2, 97.5] · ICC 0.0308 · cluster 16.6 · DEFF 1.48

| Operator | N | Matched | Type correct | Conditional | Median IoU | 95 % CI |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `t1_add_blank_line` | 398 | 97.2 % | 97.2 % | 100.0 % | 0.400 | [94.5, 98.6] |
| `t1_add_block_comment` | 400 | 96.2 % | 96.2 % | 100.0 % | 0.389 | [93.3, 97.9] |
| `t1_add_eol_comment` | 403 | 96.8 % | 96.8 % | 100.0 % | 0.400 | [93.9, 98.3] |
| `t1_reindent` | 398 | 95.5 % | 95.5 % | 100.0 % | 0.400 | [92.3, 97.4] |
| `t2_change_int_literal` | 401 | 97.8 % | 97.3 % | 99.5 % | 0.396 | [94.6, 98.6] |
| `t2_change_string_literal` | 399 | 98.5 % | 98.5 % | 100.0 % | 0.462 | [96.2, 99.4] |
| `t2_rename_local` | 399 | 98.5 % | 98.5 % | 100.0 % | 0.474 | [96.2, 99.4] |
| `t3_delete_statement` | 400 | 95.2 % | 95.0 % | 99.7 % | 0.333 | [91.7, 97.0] |
| `t3_insert_statement` | 400 | 97.2 % | 97.0 % | 99.7 % | 0.368 | [94.2, 98.5] |
| `t3_wrap_statement` | 402 | 97.3 % | 97.3 % | 100.0 % | 0.400 | [94.6, 98.6] |

## Mutations vs sub-regions

N = 4336 · matched 83.4 % · type correct 81.6 % [80.4, 82.8] · ICC 0.0061 · cluster 18.0 · DEFF 1.10

| Operator | N | Matched | Type correct | Conditional | Median IoU | 95 % CI |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `t1_add_blank_line` | 398 | 24.9 % | 24.9 % | 100.0 % | 0.059 | [20.7, 29.6] |
| `t1_add_block_comment` | 400 | 24.0 % | 24.0 % | 100.0 % | 0.059 | [19.9, 28.7] |
| `t1_add_eol_comment` | 403 | 78.2 % | 78.2 % | 100.0 % | 0.062 | [73.6, 82.1] |
| `t1_reindent` | 398 | 98.7 % | 98.7 % | 100.0 % | 0.357 | [97.0, 99.5] |
| `t2_change_int_literal` | 401 | 98.5 % | 98.3 % | 99.7 % | 1.000 | [96.3, 99.2] |
| `t2_change_string_literal` | 399 | 99.7 % | 99.7 % | 100.0 % | 1.000 | [98.5, 100.0] |
| `t2_rename_local` | 744 | 99.5 % | 99.5 % | 100.0 % | 1.000 | [98.6, 99.8] |
| `t3_delete_statement` | 399 | 98.7 % | 89.0 % | 90.1 % | 1.000 | [85.3, 91.8] |
| `t3_insert_statement` | 392 | 98.7 % | 88.8 % | 89.9 % | 1.000 | [85.1, 91.7] |
| `t3_wrap_statement` | 402 | 99.8 % | 99.8 % | 100.0 % | 1.000 | [98.5, 100.0] |

## Untouched runs vs sub-regions

N = 8017 · matched 98.2 % · type correct 98.1 % [97.6, 98.5] · ICC 0.0403 · cluster 32.2 · DEFF 2.26

| Operator | N | Matched | Type correct | Conditional | Median IoU | 95 % CI |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `none` | 8017 | 98.2 % | 98.1 % | 100.0 % | 0.333 | [97.6, 98.5] |

## Failure decomposition (mutations)

| Failure | N | Share |
| --- | ---: | ---: |
| never captured | 718 | 16.6 % |
| captured, typed wrongly | 79 | 1.8 % |

Misses by operator: `t1_add_block_comment` 304, `t1_add_blank_line` 299, `t1_add_eol_comment` 88, `t2_change_int_literal` 6, `t3_delete_statement` 5, `t3_insert_statement` 5, `t1_reindent` 5, `t2_rename_local` 4, `t3_wrap_statement` 1, `t2_change_string_literal` 1

Wrong types by operator and prediction: `t3_insert_statement`→T2 39, `t3_delete_statement`→T2 39, `t2_change_int_literal`→T1 1

## How to read the three low figures

- **`t1_add_blank_line` and `t1_add_block_comment` are NOT MEASURABLE at this scale, and their percentages should not be quoted as detection rates.** Sub-regions are built from statement extents, and a blank line or a comment-only line belongs to no statement, so no sub-region can correspond to it. Measured over 798 such references: of the 195 that matched, **100 % were covered incidentally** by a sub-region spanning three lines or more (median 8, p90 12) — none by an element pointing at the inserted line. The 24 % is therefore the probability that an insertion happened to land inside some multi-line statement, which says nothing about detection. Report as untestable, in the same way as `mARI` and `mSIL`. It is also a real product observation: adding only a blank line or a comment produces no highlight, which is semantically right since no code changed.
- `t1_add_eol_comment` at ~78 % is the same mechanism, partially escaped: an end-of-line comment attaches to an existing statement, so that statement's extent usually does cover it.
- Batches 1–4 carry the original insert and delete operators. A plain `int x = 5;` normalises to what every int declaration normalises to, so the statement-level LCS pairs the insertion with an existing declaration and reports a rename; deletion has the mirror problem. Both are corpus artefacts and the detector is right in each case. Batch 5 onward use corrected operators and form a **separate arm**.
