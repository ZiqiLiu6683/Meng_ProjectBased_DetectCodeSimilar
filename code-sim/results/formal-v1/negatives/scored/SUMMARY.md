# Negative stratum — 1000 pairs

From `/Users/liuziqi/Documents/西大Graduate/Project_base/Code/Meng_ProjectBased_DetectCodeSimilar/code-sim/results/formal-v1/negatives/run/merged.jsonl`. Every manifest pair has a result.

Clustered by problem pair: ICC 0.1835, mean cluster 2.7, DEFF 1.31.

## The two §4.1 definitions, reported separately as required

| Definition | FP | Rate | Specificity | 95 % CI on specificity |
| --- | ---: | ---: | ---: | --- |
| reference-range (covers ≥ 70 % of both files) | 2 | 0.2 % | 99.8 % | [99.1, 100.0] |
| strict product (any region ≥ 6 lines) | 180 | 18.0 % | 82.0 % | [79.1, 84.6] |
| — of which claim a syntactic type T1/T2/T3 | 30 | 3.0 % | 97.0 % | [95.5, 98.0] |

Types claimed by the emitted regions: `POSSIBLE_T4_CANDIDATE` 155, `T3` 30, `T4_CONFIRMED` 1, `T2` 1

Largest emitted region (min side), over the 180 strict-FP pairs: median 17 lines, p90 47, max 84.

## Combined, on the strict product definition

| | |
| --- | ---: |
| true positives | 5000 |
| false negatives | 0 |
| true negatives | 820 |
| false positives | 180 |
| sensitivity (recall) | 100.0 % |
| specificity | 82.0 % |
| balanced accuracy | 91.0 % |
| MCC | 0.890 |

### Precision at stated clone prevalences

**Not** the precision of this corpus. §4.1 forbids reporting an artificial positive/negative mixture as population precision — the 5,000/1,000 split reflects how much compute was spent, not how often submissions are clones.

| Prevalence | Precision |
| ---: | ---: |
| 1 % | 5.3 % |
| 5 % | 22.6 % |
| 10 % | 38.2 % |
| 25 % | 64.9 % |
| 50 % | 84.7 % |

## Audit sample

`audit_sample.csv` holds the largest cases from each stratum claims syntactic type (30), emits region, no syntactic claim (150), covers >= 70 % of both files (2), clean (820). Largest rather than random: §4.1 wants the audit to test whether a strict false positive is a real small clone, and the biggest emissions are where that question has teeth.

