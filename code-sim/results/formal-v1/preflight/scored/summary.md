# Region corpus scoring summary

Clone intervals: c-match = minimum-side reference coverage >= 0.70, scored against REGIONS.
Mutations and untouched runs: capture = any overlap (protocol §6), scored against SUB-REGIONS.
Primary match chosen by boundary quality without consulting its type.


## Clone intervals vs regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | t1_add_blank_line | 4 | 100.0% | 100.0% | 100.0% | 0.351 |
| T1 | t1_add_block_comment | 5 | 80.0% | 80.0% | 100.0% | 0.402 |
| T1 | t1_add_eol_comment | 6 | 100.0% | 100.0% | 100.0% | 0.591 |
| T1 | t1_reindent | 3 | 100.0% | 100.0% | 100.0% | 0.283 |
| T2 | t2_change_int_literal | 6 | 100.0% | 100.0% | 100.0% | 0.335 |
| T2 | t2_change_string_literal | 5 | 100.0% | 100.0% | 100.0% | 0.593 |
| T2 | t2_rename_local | 5 | 60.0% | 60.0% | 100.0% | 0.682 |
| T3 | t3_delete_statement | 6 | 100.0% | 100.0% | 100.0% | 0.417 |
| T3 | t3_insert_statement | 4 | 100.0% | 100.0% | 100.0% | 0.596 |
| T3 | t3_wrap_statement | 6 | 83.3% | 83.3% | 100.0% | 0.667 |

## Mutations vs sub-regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | t1_add_blank_line | 4 | 25.0% | 25.0% | 100.0% | 0.059 |
| T1 | t1_add_block_comment | 5 | 20.0% | 20.0% | 100.0% | 0.026 |
| T1 | t1_add_eol_comment | 6 | 66.7% | 66.7% | 100.0% | 0.060 |
| T1 | t1_reindent | 3 | 100.0% | 100.0% | 100.0% | 0.243 |
| T2 | t2_change_int_literal | 6 | 100.0% | 100.0% | 100.0% | 1.000 |
| T2 | t2_change_string_literal | 5 | 100.0% | 100.0% | 100.0% | 1.000 |
| T2 | t2_rename_local | 5 | 60.0% | 60.0% | 100.0% | 0.500 |
| T3 | t3_delete_statement | 6 | 100.0% | 100.0% | 100.0% | 1.000 |
| T3 | t3_insert_statement | 4 | 100.0% | 100.0% | 100.0% | 1.000 |
| T3 | t3_wrap_statement | 6 | 83.3% | 0.0% | 0.0% | 0.438 |

## Untouched runs vs sub-regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | none | 100 | 92.0% | 92.0% | 100.0% | 0.313 |

## Confusion (kind, expected -> primary matched prediction)

| Kind | Expected | Predicted | N |
| --- | --- | --- | ---: |
| CLONE_INTERVAL | T1 | NOT_MATCHED | 1 |
| CLONE_INTERVAL | T1 | T1 | 17 |
| CLONE_INTERVAL | T2 | NOT_MATCHED | 2 |
| CLONE_INTERVAL | T2 | T2 | 14 |
| CLONE_INTERVAL | T3 | NOT_MATCHED | 1 |
| CLONE_INTERVAL | T3 | T3 | 15 |
| MUTATION | T1 | NOT_MATCHED | 9 |
| MUTATION | T1 | T1 | 9 |
| MUTATION | T2 | NOT_MATCHED | 2 |
| MUTATION | T2 | T2 | 14 |
| MUTATION | T3 | NOT_MATCHED | 1 |
| MUTATION | T3 | T1 | 5 |
| MUTATION | T3 | T3 | 10 |
| UNTOUCHED | T1 | NOT_MATCHED | 8 |
| UNTOUCHED | T1 | T1 | 92 |
