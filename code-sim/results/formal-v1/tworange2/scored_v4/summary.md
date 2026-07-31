# Region corpus scoring summary

Clone intervals: c-match = minimum-side reference coverage >= 0.70, scored against REGIONS.
Mutations and untouched runs: capture = any overlap (protocol §6), scored against SUB-REGIONS.
Primary match chosen by boundary quality without consulting its type.


## Clone intervals vs regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | t1_add_blank_line | 18 | 88.9% | 55.6% | 62.5% | 0.105 |
| T1 | t1_add_block_comment | 24 | 91.7% | 62.5% | 68.2% | 0.197 |
| T1 | t1_add_eol_comment | 29 | 82.8% | 17.2% | 20.8% | 0.182 |
| T1 | t1_reindent | 25 | 88.0% | 28.0% | 31.8% | 0.234 |
| T2 | t2_change_int_literal | 27 | 92.6% | 74.1% | 80.0% | 0.171 |
| T2 | t2_change_string_literal | 22 | 100.0% | 86.4% | 86.4% | 0.217 |
| T2 | t2_rename_local | 23 | 78.3% | 52.2% | 66.7% | 0.265 |
| T3 | t3_delete_statement | 22 | 81.8% | 81.8% | 100.0% | 0.233 |
| T3 | t3_insert_statement | 27 | 88.9% | 88.9% | 100.0% | 0.180 |
| T3 | t3_wrap_statement | 19 | 94.7% | 94.7% | 100.0% | 0.234 |

## Mutations vs sub-regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | t1_add_blank_line | 18 | 22.2% | 22.2% | 100.0% | 0.033 |
| T1 | t1_add_block_comment | 24 | 16.7% | 16.7% | 100.0% | 0.052 |
| T1 | t1_add_eol_comment | 29 | 72.4% | 72.4% | 100.0% | 0.042 |
| T1 | t1_reindent | 25 | 92.0% | 92.0% | 100.0% | 0.269 |
| T2 | t2_change_int_literal | 27 | 96.3% | 96.3% | 100.0% | 1.000 |
| T2 | t2_change_string_literal | 22 | 100.0% | 100.0% | 100.0% | 1.000 |
| T2 | t2_rename_local | 35 | 88.6% | 82.9% | 93.5% | 0.667 |
| T3 | t3_delete_statement | 22 | 90.9% | 77.3% | 85.0% | 1.000 |
| T3 | t3_insert_statement | 27 | 88.9% | 88.9% | 100.0% | 1.000 |
| T3 | t3_wrap_statement | 19 | 100.0% | 100.0% | 100.0% | 1.000 |

## Untouched runs vs sub-regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | none | 396 | 90.4% | 90.4% | 100.0% | 0.238 |

## Confusion (kind, expected -> primary matched prediction)

| Kind | Expected | Predicted | N |
| --- | --- | --- | ---: |
| CLONE_INTERVAL | T1 | NOT_MATCHED | 12 |
| CLONE_INTERVAL | T1 | T1 | 37 |
| CLONE_INTERVAL | T1 | T2 | 27 |
| CLONE_INTERVAL | T1 | T3 | 20 |
| CLONE_INTERVAL | T2 | NOT_MATCHED | 7 |
| CLONE_INTERVAL | T2 | T2 | 51 |
| CLONE_INTERVAL | T2 | T3 | 14 |
| CLONE_INTERVAL | T3 | NOT_MATCHED | 8 |
| CLONE_INTERVAL | T3 | T3 | 60 |
| MUTATION | T1 | NOT_MATCHED | 44 |
| MUTATION | T1 | T1 | 52 |
| MUTATION | T2 | NOT_MATCHED | 5 |
| MUTATION | T2 | T1 | 2 |
| MUTATION | T2 | T2 | 77 |
| MUTATION | T3 | NOT_MATCHED | 5 |
| MUTATION | T3 | T2 | 3 |
| MUTATION | T3 | T3 | 60 |
| UNTOUCHED | T1 | NOT_MATCHED | 38 |
| UNTOUCHED | T1 | T1 | 358 |
