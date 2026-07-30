# Region corpus scoring summary

Clone intervals: c-match = minimum-side reference coverage >= 0.70, scored against REGIONS.
Mutations and untouched runs: capture = any overlap (protocol §6), scored against SUB-REGIONS.
Primary match chosen by boundary quality without consulting its type.


## Clone intervals vs regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | t1_add_blank_line | 99 | 99.0% | 99.0% | 100.0% | 0.400 |
| T1 | t1_add_block_comment | 99 | 97.0% | 97.0% | 100.0% | 0.375 |
| T1 | t1_add_eol_comment | 101 | 96.0% | 96.0% | 100.0% | 0.400 |
| T1 | t1_reindent | 100 | 97.0% | 97.0% | 100.0% | 0.400 |
| T2 | t2_change_int_literal | 100 | 98.0% | 97.0% | 99.0% | 0.414 |
| T2 | t2_change_string_literal | 101 | 97.0% | 97.0% | 100.0% | 0.500 |
| T2 | t2_rename_local | 100 | 98.0% | 98.0% | 100.0% | 0.500 |
| T3 | t3_delete_statement | 100 | 96.0% | 96.0% | 100.0% | 0.327 |
| T3 | t3_insert_statement | 100 | 95.0% | 95.0% | 100.0% | 0.353 |
| T3 | t3_wrap_statement | 100 | 98.0% | 98.0% | 100.0% | 0.405 |

## Mutations vs sub-regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | t1_add_blank_line | 99 | 25.3% | 25.3% | 100.0% | 0.059 |
| T1 | t1_add_block_comment | 99 | 27.3% | 27.3% | 100.0% | 0.067 |
| T1 | t1_add_eol_comment | 101 | 84.2% | 84.2% | 100.0% | 0.067 |
| T1 | t1_reindent | 100 | 99.0% | 99.0% | 100.0% | 0.357 |
| T2 | t2_change_int_literal | 100 | 98.0% | 98.0% | 100.0% | 1.000 |
| T2 | t2_change_string_literal | 101 | 100.0% | 100.0% | 100.0% | 1.000 |
| T2 | t2_rename_local | 211 | 99.1% | 99.1% | 100.0% | 1.000 |
| T3 | t3_delete_statement | 99 | 99.0% | 89.9% | 90.8% | 1.000 |
| T3 | t3_insert_statement | 97 | 97.9% | 88.7% | 90.5% | 1.000 |
| T3 | t3_wrap_statement | 100 | 99.0% | 99.0% | 100.0% | 1.000 |

## Untouched runs vs sub-regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | none | 2006 | 97.8% | 97.8% | 99.9% | 0.333 |

## Confusion (kind, expected -> primary matched prediction)

| Kind | Expected | Predicted | N |
| --- | --- | --- | ---: |
| CLONE_INTERVAL | T1 | NOT_MATCHED | 11 |
| CLONE_INTERVAL | T1 | T1 | 388 |
| CLONE_INTERVAL | T2 | NOT_MATCHED | 7 |
| CLONE_INTERVAL | T2 | T1 | 1 |
| CLONE_INTERVAL | T2 | T2 | 293 |
| CLONE_INTERVAL | T3 | NOT_MATCHED | 11 |
| CLONE_INTERVAL | T3 | T3 | 289 |
| MUTATION | T1 | NOT_MATCHED | 163 |
| MUTATION | T1 | T1 | 236 |
| MUTATION | T2 | NOT_MATCHED | 4 |
| MUTATION | T2 | T2 | 408 |
| MUTATION | T3 | NOT_MATCHED | 4 |
| MUTATION | T3 | T2 | 18 |
| MUTATION | T3 | T3 | 274 |
| UNTOUCHED | T1 | NOT_MATCHED | 44 |
| UNTOUCHED | T1 | T1 | 1961 |
| UNTOUCHED | T1 | T2 | 1 |
