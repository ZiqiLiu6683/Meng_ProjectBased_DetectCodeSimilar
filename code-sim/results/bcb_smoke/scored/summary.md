# Scoring summary (dual metric, coverage >= 70%)

results: `results\bcb_smoke\run.jsonl`

| Group | Pairs | Detected/Correct | Detection | Type-match | Errors | Sub-coverage regions | Avg ms | Median ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| MT3 | 20 | 3 | 15.00% | 15.00% | 0 | 9 | 5261 | 185 |
| NON_CLONE | 40 | 40 | 100.00% | 100.00% | 0 | 5 | 123 | 95 |
| ST3 | 20 | 20 | 100.00% | 100.00% | 0 | 0 | 6629 | 453 |
| T1 | 20 | 19 | 95.00% | 95.00% | 0 | 1 | 8409 | 4727 |
| T2 | 20 | 20 | 100.00% | 95.00% | 0 | 0 | 2626 | 260 |
| VST3 | 20 | 20 | 100.00% | 65.00% | 0 | 0 | 5519 | 2677 |

Group = similarity band where available (VST3/ST3/MT3), else expected type.
For NON_CLONE the Detection column is the correct-rejection rate.

## Misses / false positives: MT3
- MT3_000000: found (none)
- MT3_000001: found (none)
- MT3_000002: found (none)
- MT3_000003: found T2
- MT3_000004: found T3
- MT3_000005: found T2
- MT3_000006: found T2
- MT3_000007: found (none)
- MT3_000008: found (none)
- MT3_000010: found (none)
- MT3_000011: found (none)
- MT3_000012: found T2
- MT3_000013: found T2;T3
- MT3_000014: found T2
- MT3_000016: found T1;T2
- MT3_000017: found (none)
- MT3_000019: found T2

## Misses / false positives: T1
- T1_000013: found T1

