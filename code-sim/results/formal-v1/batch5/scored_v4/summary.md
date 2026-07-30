# Region corpus scoring summary

Clone intervals: c-match = minimum-side reference coverage >= 0.70, scored against REGIONS.
Mutations and untouched runs: capture = any overlap (protocol §6), scored against SUB-REGIONS.
Primary match chosen by boundary quality without consulting its type.


## Clone intervals vs regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | t1_add_blank_line | 100 | 97.0% | 97.0% | 100.0% | 0.357 |
| T1 | t1_add_block_comment | 100 | 96.0% | 96.0% | 100.0% | 0.375 |
| T1 | t1_add_eol_comment | 100 | 97.0% | 97.0% | 100.0% | 0.364 |
| T1 | t1_reindent | 101 | 98.0% | 98.0% | 100.0% | 0.381 |
| T2 | t2_change_int_literal | 100 | 97.0% | 97.0% | 100.0% | 0.400 |
| T2 | t2_change_string_literal | 100 | 98.0% | 98.0% | 100.0% | 0.471 |
| T2 | t2_rename_local | 100 | 97.0% | 97.0% | 100.0% | 0.538 |
| T3 | t3_delete_statement | 100 | 96.0% | 96.0% | 100.0% | 0.333 |
| T3 | t3_insert_statement | 100 | 100.0% | 100.0% | 100.0% | 0.408 |
| T3 | t3_wrap_statement | 99 | 99.0% | 99.0% | 100.0% | 0.390 |

## Mutations vs sub-regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | t1_add_blank_line | 100 | 21.0% | 21.0% | 100.0% | 0.050 |
| T1 | t1_add_block_comment | 100 | 26.0% | 26.0% | 100.0% | 0.051 |
| T1 | t1_add_eol_comment | 100 | 82.0% | 82.0% | 100.0% | 0.062 |
| T1 | t1_reindent | 101 | 100.0% | 100.0% | 100.0% | 0.357 |
| T2 | t2_change_int_literal | 100 | 99.0% | 99.0% | 100.0% | 1.000 |
| T2 | t2_change_string_literal | 100 | 100.0% | 100.0% | 100.0% | 1.000 |
| T2 | t2_rename_local | 204 | 97.5% | 97.5% | 100.0% | 1.000 |
| T3 | t3_delete_statement | 100 | 98.0% | 85.0% | 86.7% | 1.000 |
| T3 | t3_insert_statement | 96 | 100.0% | 100.0% | 100.0% | 1.000 |
| T3 | t3_wrap_statement | 99 | 99.0% | 99.0% | 100.0% | 1.000 |

## Untouched runs vs sub-regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | none | 2009 | 98.1% | 98.0% | 99.9% | 0.333 |

## Confusion (kind, expected -> primary matched prediction)

| Kind | Expected | Predicted | N |
| --- | --- | --- | ---: |
| CLONE_INTERVAL | T1 | NOT_MATCHED | 12 |
| CLONE_INTERVAL | T1 | T1 | 389 |
| CLONE_INTERVAL | T2 | NOT_MATCHED | 8 |
| CLONE_INTERVAL | T2 | T2 | 292 |
| CLONE_INTERVAL | T3 | NOT_MATCHED | 5 |
| CLONE_INTERVAL | T3 | T3 | 294 |
| MUTATION | T1 | NOT_MATCHED | 171 |
| MUTATION | T1 | T1 | 230 |
| MUTATION | T2 | NOT_MATCHED | 6 |
| MUTATION | T2 | T2 | 398 |
| MUTATION | T3 | NOT_MATCHED | 3 |
| MUTATION | T3 | T2 | 13 |
| MUTATION | T3 | T3 | 279 |
| UNTOUCHED | T1 | NOT_MATCHED | 39 |
| UNTOUCHED | T1 | T1 | 1969 |
| UNTOUCHED | T1 | T2 | 1 |
