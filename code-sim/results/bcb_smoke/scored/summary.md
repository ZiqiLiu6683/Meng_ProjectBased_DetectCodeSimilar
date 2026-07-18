# Scoring summary (dual metric, coverage >= 70%)

results: `results\bcb_smoke\run.jsonl`

| Group | Pairs | Detected/Correct | Detection | Type-match | Errors | Sub-coverage regions | Avg ms | Median ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| T1 | 19 | 18 | 94.74% | 94.74% | 0 | 1 | 7733 | 4988 |

Group = similarity band where available (VST3/ST3/MT3), else expected type.
For NON_CLONE the Detection column is the correct-rejection rate.

## Misses / false positives: T1
- T1_000013: found T1

Pairs in labels but missing from results: 121
