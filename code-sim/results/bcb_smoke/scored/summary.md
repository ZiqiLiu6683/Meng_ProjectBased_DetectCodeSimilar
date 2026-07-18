# Scoring summary

results: `results\bcb_smoke\run.jsonl`  labels: `results\bcb_smoke\labels.csv`

| Expected | Pairs | Hits | Accuracy | Errors | Avg ms | Median ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| NON_CLONE | 40 | 35 | 87.50% | 0 | 128 | 96 |
| T1 | 20 | 20 | 100.00% | 0 | 6853 | 3644 |
| T2 | 20 | 19 | 95.00% | 0 | 1386 | 226 |
| T3 | 60 | 38 | 63.33% | 0 | 5803 | 383 |

**Overall (determinate labels): 112/140 = 80.00%**

## Misses
- T2_000001 (expected T2): found T1
- VST3_000005 (expected T3): found T1
- VST3_000006 (expected T3): found T1
- VST3_000007 (expected T3): found T2
- VST3_000008 (expected T3): found T2
- VST3_000009 (expected T3): found T1
- VST3_000012 (expected T3): found T1
- VST3_000017 (expected T3): found T1
- MT3_000000 (expected T3): found (none)
- MT3_000001 (expected T3): found (none)
- MT3_000002 (expected T3): found (none)
- MT3_000003 (expected T3): found T2
- MT3_000005 (expected T3): found T2
- MT3_000006 (expected T3): found T2
- MT3_000007 (expected T3): found (none)
- MT3_000008 (expected T3): found (none)
- MT3_000010 (expected T3): found (none)
- MT3_000011 (expected T3): found (none)
- MT3_000012 (expected T3): found T2
- MT3_000014 (expected T3): found T2
- MT3_000016 (expected T3): found T1;T2
- MT3_000017 (expected T3): found (none)
- MT3_000019 (expected T3): found T2
- NEG_000009 (expected NON_CLONE): found T2
- NEG_000011 (expected NON_CLONE): found T2
- NEG_000020 (expected NON_CLONE): found T2
- NEG_000028 (expected NON_CLONE): found T2;T3
- NEG_000035 (expected NON_CLONE): found T2
