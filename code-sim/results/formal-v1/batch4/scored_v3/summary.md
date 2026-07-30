# Region corpus scoring summary

Clone intervals: c-match = minimum-side reference coverage >= 0.70, scored against REGIONS.
Mutations and untouched runs: capture = any overlap (protocol §6), scored against SUB-REGIONS.
Primary match chosen by boundary quality without consulting its type.


## Clone intervals vs regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | t1_add_blank_line | 99 | 97.0% | 97.0% | 100.0% | 0.410 |
| T1 | t1_add_block_comment | 101 | 94.1% | 94.1% | 100.0% | 0.360 |
| T1 | t1_add_eol_comment | 101 | 99.0% | 99.0% | 100.0% | 0.400 |
| T1 | t1_reindent | 101 | 93.1% | 93.1% | 100.0% | 0.382 |
| T2 | t2_change_int_literal | 100 | 98.0% | 98.0% | 100.0% | 0.350 |
| T2 | t2_change_string_literal | 99 | 99.0% | 99.0% | 100.0% | 0.478 |
| T2 | t2_rename_local | 99 | 100.0% | 100.0% | 100.0% | 0.462 |
| T3 | t3_delete_statement | 100 | 95.0% | 95.0% | 100.0% | 0.350 |
| T3 | t3_insert_statement | 99 | 98.0% | 97.0% | 99.0% | 0.375 |
| T3 | t3_wrap_statement | 101 | 97.0% | 97.0% | 100.0% | 0.414 |

## Mutations vs sub-regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | t1_add_blank_line | 99 | 20.2% | 20.2% | 100.0% | 0.061 |
| T1 | t1_add_block_comment | 101 | 26.7% | 26.7% | 100.0% | 0.053 |
| T1 | t1_add_eol_comment | 101 | 73.3% | 73.3% | 100.0% | 0.062 |
| T1 | t1_reindent | 126 | 90.5% | 90.5% | 100.0% | 0.318 |
| T2 | t2_change_int_literal | 102 | 98.0% | 98.0% | 100.0% | 1.000 |
| T2 | t2_change_string_literal | 99 | 100.0% | 100.0% | 100.0% | 1.000 |
| T2 | t2_rename_local | 193 | 100.0% | 100.0% | 100.0% | 1.000 |
| T3 | t3_delete_statement | 100 | 98.0% | 92.0% | 93.9% | 1.000 |
| T3 | t3_insert_statement | 99 | 99.0% | 85.9% | 86.7% | 1.000 |
| T3 | t3_wrap_statement | 101 | 100.0% | 100.0% | 100.0% | 1.000 |

## Untouched runs vs sub-regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | none | 2006 | 97.9% | 97.9% | 99.9% | 0.333 |

## Confusion (kind, expected -> primary matched prediction)

| Kind | Expected | Predicted | N |
| --- | --- | --- | ---: |
| CLONE_INTERVAL | T1 | NOT_MATCHED | 17 |
| CLONE_INTERVAL | T1 | T1 | 385 |
| CLONE_INTERVAL | T2 | NOT_MATCHED | 3 |
| CLONE_INTERVAL | T2 | T2 | 295 |
| CLONE_INTERVAL | T3 | NOT_MATCHED | 10 |
| CLONE_INTERVAL | T3 | T1 | 1 |
| CLONE_INTERVAL | T3 | T3 | 289 |
| MUTATION | T1 | NOT_MATCHED | 192 |
| MUTATION | T1 | T1 | 235 |
| MUTATION | T2 | NOT_MATCHED | 2 |
| MUTATION | T2 | T2 | 392 |
| MUTATION | T3 | NOT_MATCHED | 3 |
| MUTATION | T3 | T2 | 19 |
| MUTATION | T3 | T3 | 278 |
| UNTOUCHED | T1 | NOT_MATCHED | 42 |
| UNTOUCHED | T1 | T1 | 1963 |
| UNTOUCHED | T1 | T2 | 1 |
