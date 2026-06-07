# Long-Code Evaluation Summary

Generated on 2026-06-06.

## Dataset

- Pair file: `evaluation/long_pairs.csv`
- Source folder: `evaluation/samples/java_long/`
- Pair count: 36
- Java file count: 72
- Total generated Java lines: 15,696
- File length range: 191-230 lines
- Average file length: 218.0 lines
- Size target: more than 10x the current short Java samples, which are mostly 5-27 lines.

## Coverage

Each of the six domains contributes one pair for each project:

- `LongT1`: near-identical long clone with comments/formatting changes
- `LongT2`: long renamed clone
- `LongT3`: long Type-3 clone with branch/threshold edits
- `LongInternalHelper`: long inline-to-helper extraction
- `LongCallChain`: long internal call-chain refactor
- `LongNonClone`: long unrelated program with generic loop/API overlap

## Staged Pipeline Result

Result file: `evaluation/results/long_code_evaluation_results.csv`

| Project | Pairs | Type Pass | Scope Pass | Avg Runtime |
|---|---:|---:|---:|---:|
| LongT1 | 6 | 6 | 6 | 1586 ms |
| LongT2 | 6 | 6 | 6 | 1594 ms |
| LongT3 | 6 | 0 | 6 | 1627 ms |
| LongInternalHelper | 6 | 0 | 6 | 1560 ms |
| LongCallChain | 6 | 0 | 0 | 1574 ms |
| LongNonClone | 6 | 0 | 6 | 1651 ms |

Overall type correctness: 12/36.

Main observed issue: long T3, T4-style helper/call-chain cases, and non-clone cases are frequently classified as `T2`. This suggests that, on long multi-method files, strong structural overlap and many method-level matches can dominate the final type decision.

## discovRE/CFG Evaluation Result

Result file: `evaluation/results/long_code_discovre_evaluation_results.csv`

All 108 view runs completed successfully:

- 36 pairs x 3 views
- Views: `DISCOVRE_NUMERIC`, `RAW_HASH_BUCKET`, `HYBRID_NUMERIC_HASH`

Average max non-constructor constrained similarity:

| View | Avg Similarity | Avg Candidate Reduction | Avg Runtime |
|---|---:|---:|---:|
| DISCOVRE_NUMERIC | 0.933 | 0.694 | 3596 ms |
| RAW_HASH_BUCKET | 0.893 | 0.802 | 4526 ms |
| HYBRID_NUMERIC_HASH | 0.898 | 0.852 | 4492 ms |

Notable observation: the hash-based views reduce non-clone similarity more strongly than the numeric-only view. For `LongNonClone`, average max non-constructor similarity drops from 0.599 with `DISCOVRE_NUMERIC` to 0.357 with `RAW_HASH_BUCKET` and 0.389 with `HYBRID_NUMERIC_HASH`.

## Interpretation

This long-code test set is useful for the next report because it shows both scalability and limitations:

- The pipeline runs on approximately 200-line Java files without crashing.
- Runtime remains measurable and reportable.
- T1/T2 long cases remain stable.
- Long T3/T4 cases reveal that the current classifier still over-compresses many edit/refactor patterns into `T2`.
- Long non-clone cases reveal false-positive risk when files share many generic helper methods, loops, and Java API patterns.
- CFG/discovRE candidate narrowing remains useful as an evidence stage, especially because raw/hash views separate non-clone cases better than numeric-only features.
