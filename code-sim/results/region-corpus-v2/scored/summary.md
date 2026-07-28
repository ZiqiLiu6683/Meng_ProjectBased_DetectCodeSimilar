# Region corpus scoring summary

Clone intervals: c-match = minimum-side reference coverage >= 0.70, scored against REGIONS.
Mutations and untouched runs: capture = any overlap (protocol §6), scored against SUB-REGIONS.
Primary match chosen by boundary quality without consulting its type.


## Clone intervals vs regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | t1_add_blank_line | 20 | 90.0% | 10.0% | 11.1% | 0.194 |
| T1 | t1_add_block_comment | 20 | 85.0% | 5.0% | 5.9% | 0.207 |
| T1 | t1_add_eol_comment | 20 | 95.0% | 10.0% | 10.5% | 0.233 |
| T1 | t1_reindent | 20 | 100.0% | 0.0% | 0.0% | 0.183 |
| T2 | t2_change_int_literal | 20 | 100.0% | 5.0% | 5.0% | 0.217 |
| T2 | t2_change_string_literal | 20 | 100.0% | 100.0% | 100.0% | 0.226 |
| T2 | t2_rename_local | 20 | 85.0% | 85.0% | 100.0% | 0.290 |
| T3 | t3_delete_statement | 20 | 100.0% | 100.0% | 100.0% | 0.181 |
| T3 | t3_insert_statement | 20 | 100.0% | 100.0% | 100.0% | 0.215 |
| T3 | t3_wrap_statement | 20 | 95.0% | 95.0% | 100.0% | 0.146 |

## Mutations vs sub-regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | t1_add_blank_line | 20 | 15.0% | 15.0% | 100.0% | 0.062 |
| T1 | t1_add_block_comment | 20 | 25.0% | 25.0% | 100.0% | 0.059 |
| T1 | t1_add_eol_comment | 20 | 60.0% | 60.0% | 100.0% | 0.063 |
| T1 | t1_reindent | 20 | 100.0% | 100.0% | 100.0% | 0.227 |
| T2 | t2_change_int_literal | 20 | 90.0% | 90.0% | 100.0% | 1.000 |
| T2 | t2_change_string_literal | 20 | 100.0% | 100.0% | 100.0% | 1.000 |
| T2 | t2_rename_local | 20 | 85.0% | 65.0% | 76.5% | 0.429 |
| T3 | t3_delete_statement | 20 | 100.0% | 90.0% | 90.0% | 1.000 |
| T3 | t3_insert_statement | 20 | 100.0% | 85.0% | 85.0% | 1.000 |
| T3 | t3_wrap_statement | 20 | 100.0% | 10.0% | 10.0% | 0.099 |

## Untouched runs vs sub-regions

| Expected | Operator | N | Matched | Type correct | Conditional type accuracy | Median IoU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| T1 | none | 336 | 94.9% | 94.6% | 99.7% | 0.216 |

## Confusion (kind, expected -> primary matched prediction)

| Kind | Expected | Predicted | N |
| --- | --- | --- | ---: |
| CLONE_INTERVAL | T1 | NOT_MATCHED | 6 |
| CLONE_INTERVAL | T1 | T1 | 5 |
| CLONE_INTERVAL | T1 | T2 | 33 |
| CLONE_INTERVAL | T1 | T3 | 36 |
| CLONE_INTERVAL | T2 | NOT_MATCHED | 3 |
| CLONE_INTERVAL | T2 | T1 | 1 |
| CLONE_INTERVAL | T2 | T2 | 38 |
| CLONE_INTERVAL | T2 | T3 | 18 |
| CLONE_INTERVAL | T3 | NOT_MATCHED | 1 |
| CLONE_INTERVAL | T3 | T3 | 59 |
| MUTATION | T1 | NOT_MATCHED | 40 |
| MUTATION | T1 | T1 | 40 |
| MUTATION | T2 | NOT_MATCHED | 5 |
| MUTATION | T2 | T1 | 4 |
| MUTATION | T2 | T2 | 51 |
| MUTATION | T3 | T1 | 18 |
| MUTATION | T3 | T2 | 5 |
| MUTATION | T3 | T3 | 37 |
| UNTOUCHED | T1 | NOT_MATCHED | 17 |
| UNTOUCHED | T1 | T1 | 318 |
| UNTOUCHED | T1 | T2 | 1 |
