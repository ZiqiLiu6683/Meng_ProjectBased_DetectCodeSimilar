# Strict region-aware scoring summary

Primary c-match threshold: 0.70 on both sides.
Primary matched prediction is chosen by boundary quality without consulting its type.
Errors and missing results remain in the denominator.

| Group | N | Detection / correct rejection | 95% cluster CI | Typed recall | Conditional type accuracy | Median min IoU | Errors/missing | Strict product FP |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| T1 | 33 | 0.00% | [0.00%, 0.00%] | 0.00% | 0.00% | 0.000 | 7 | 0 |
| T2 | 22 | 0.00% | [0.00%, 0.00%] | 0.00% | 0.00% | 0.000 | 15 | 0 |
| T3 | 21 | 0.00% | [0.00%, 0.00%] | 0.00% | 0.00% | 0.000 | 11 | 0 |

## Execution provenance

| Analysis mode | N | Share | Successful | Errors/missing | Median wall ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| MISSING | 33 | 43.42% | 0 | 33 | 0 |
| SOURCE_PLUS_WALA_SMT_DYNAMIC | 43 | 56.58% | 43 | 0 | 234026 |
