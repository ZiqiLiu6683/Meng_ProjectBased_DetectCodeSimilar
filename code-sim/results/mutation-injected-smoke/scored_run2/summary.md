# Strict region-aware scoring summary

Primary c-match threshold: 0.70 on both sides.
Primary matched prediction is chosen by boundary quality without consulting its type.
Errors and missing results remain in the denominator.

| Group | N | Detection / correct rejection | 95% cluster CI | Typed recall | Conditional type accuracy | Median min IoU | Errors/missing | Strict product FP |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| T1 | 33 | 30.30% | [15.15%, 45.45%] | 30.30% | 100.00% | 0.803 | 7 | 0 |
| T2 | 22 | 40.91% | [22.73%, 63.64%] | 22.73% | 55.56% | 0.833 | 5 | 0 |
| T3 | 21 | 33.33% | [14.29%, 52.38%] | 33.33% | 100.00% | 0.800 | 5 | 0 |

## Execution provenance

| Analysis mode | N | Share | Successful | Errors/missing | Median wall ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| MISSING | 17 | 22.37% | 0 | 17 | 0 |
| SOURCE_PLUS_WALA_SMT_DYNAMIC | 59 | 77.63% | 59 | 0 | 241096 |
