# Scoring summary

results: `results/experiments/inhouse55/final_pipeline.jsonl`  labels: `results/experiments/inhouse55/labels.csv`

| Expected | Pairs | Hits | Accuracy | Errors | Avg ms | Median ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| NON_CLONE | 8 | 7 | 87.50% | 0 | 13676 | 14731 |
| T1 | 5 | 5 | 100.00% | 0 | 2590 | 2346 |
| T2 | 5 | 5 | 100.00% | 0 | 2379 | 2372 |
| T3 | 8 | 8 | 100.00% | 0 | 2382 | 2366 |
| T4_WEAK | 21 | 11 | 52.38% | 0 | 9066 | 2540 |

**Overall (determinate labels): 36/47 = 76.60%**

INCONCLUSIVE pairs (excluded, 8):
- java_semantic_recursion_iteration_01: found T4_DYNAMIC_EVIDENCE
- java_semantic_recursive_max_02: found POSSIBLE_T4_CANDIDATE
- java_semantic_set_bool_03: found (none)
- java_semantic_sort_stream_04: found (none)
- java_semantic_loop_set_05: found T4_DYNAMIC_EVIDENCE
- java_semantic_dfs_06: found (none)
- java_semantic_reverse_07: found T4_DYNAMIC_EVIDENCE
- java_semantic_count_08: found T4_DYNAMIC_EVIDENCE

## Misses
- java_t4_inline_helper_01 (expected T4_WEAK): found T3
- java_t4_helper_inline_02 (expected T4_WEAK): found T3
- java_t4_inline_guard_03 (expected T4_WEAK): found T3
- java_t4_inline_max_07 (expected T4_WEAK): found T3
- java_t4_extract_method_01 (expected T4_WEAK): found T1;T3
- java_t4_merge_methods_02 (expected T4_WEAK): found T1;T3
- java_t4_extract_join_07 (expected T4_WEAK): found T3
- java_t4_merge_join_08 (expected T4_WEAK): found T3
- java_t4_call_chain_01 (expected T4_WEAK): found T3
- java_t4_call_chain_auth_02 (expected T4_WEAK): found T3
- java_nonclone_string_05 (expected NON_CLONE): found T4_DYNAMIC_EVIDENCE
