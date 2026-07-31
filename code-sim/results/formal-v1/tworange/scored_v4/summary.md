# Region corpus scoring summary

Clone intervals: c-match = minimum-side reference coverage >= 0.70, scored against REGIONS.
Mutations and untouched runs: capture = any overlap (protocol §6), scored against SUB-REGIONS.
Primary match chosen by boundary quality without consulting its type.


## Clone intervals vs regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | t1_add_blank_line | 93 | 95.7% | 37.6% | 39.3% | 0.207 |
| T1 | t1_add_block_comment | 106 | 93.4% | 51.9% | 55.6% | 0.182 |
| T1 | t1_add_eol_comment | 94 | 95.7% | 45.7% | 47.8% | 0.203 |
| T1 | t1_reindent | 105 | 96.2% | 49.5% | 51.5% | 0.207 |
| T2 | t2_change_int_literal | 96 | 91.7% | 70.8% | 77.3% | 0.207 |
| T2 | t2_change_string_literal | 95 | 92.6% | 65.3% | 70.5% | 0.230 |
| T2 | t2_rename_local | 107 | 89.7% | 65.4% | 72.9% | 0.250 |
| T3 | t3_delete_statement | 97 | 81.4% | 81.4% | 100.0% | 0.179 |
| T3 | t3_insert_statement | 103 | 93.2% | 90.3% | 96.9% | 0.193 |
| T3 | t3_wrap_statement | 98 | 93.9% | 93.9% | 100.0% | 0.211 |

## Mutations vs sub-regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | t1_add_blank_line | 93 | 25.8% | 25.8% | 100.0% | 0.047 |
| T1 | t1_add_block_comment | 106 | 23.6% | 23.6% | 100.0% | 0.048 |
| T1 | t1_add_eol_comment | 94 | 75.5% | 75.5% | 100.0% | 0.045 |
| T1 | t1_reindent | 105 | 97.1% | 97.1% | 100.0% | 0.222 |
| T2 | t2_change_int_literal | 96 | 95.8% | 95.8% | 100.0% | 1.000 |
| T2 | t2_change_string_literal | 95 | 97.9% | 97.9% | 100.0% | 1.000 |
| T2 | t2_rename_local | 183 | 94.5% | 90.2% | 95.4% | 1.000 |
| T3 | t3_delete_statement | 97 | 94.8% | 80.4% | 84.8% | 1.000 |
| T3 | t3_insert_statement | 103 | 92.2% | 92.2% | 100.0% | 1.000 |
| T3 | t3_wrap_statement | 98 | 96.9% | 96.9% | 100.0% | 1.000 |

## Untouched runs vs sub-regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | none | 1685 | 93.1% | 92.8% | 99.6% | 0.222 |

## Confusion (kind, expected -> primary matched prediction)

| Kind | Expected | Predicted | N |
| --- | --- | --- | ---: |
| CLONE_INTERVAL | T1 | NOT_MATCHED | 19 |
| CLONE_INTERVAL | T1 | T1 | 185 |
| CLONE_INTERVAL | T1 | T2 | 101 |
| CLONE_INTERVAL | T1 | T3 | 93 |
| CLONE_INTERVAL | T2 | NOT_MATCHED | 26 |
| CLONE_INTERVAL | T2 | T2 | 200 |
| CLONE_INTERVAL | T2 | T3 | 72 |
| CLONE_INTERVAL | T3 | NOT_MATCHED | 31 |
| CLONE_INTERVAL | T3 | T1 | 2 |
| CLONE_INTERVAL | T3 | T2 | 1 |
| CLONE_INTERVAL | T3 | T3 | 264 |
| MUTATION | T1 | NOT_MATCHED | 176 |
| MUTATION | T1 | T1 | 222 |
| MUTATION | T2 | NOT_MATCHED | 16 |
| MUTATION | T2 | T1 | 8 |
| MUTATION | T2 | T2 | 350 |
| MUTATION | T3 | NOT_MATCHED | 16 |
| MUTATION | T3 | T2 | 14 |
| MUTATION | T3 | T3 | 268 |
| UNTOUCHED | T1 | NOT_MATCHED | 116 |
| UNTOUCHED | T1 | T1 | 1563 |
| UNTOUCHED | T1 | T2 | 6 |
