# Strict region-aware scoring summary

Primary c-match threshold: 0.70 on both sides.
Primary matched prediction is chosen by boundary quality without consulting its type.
Errors and missing results remain in the denominator.

| Group | N | Detection / correct rejection | 95% cluster CI | Typed recall | Conditional type accuracy | Median min IoU | Errors/missing | Strict product FP |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| T1 | 33 | 33.33% | [18.18%, 48.48%] | 33.33% | 100.00% | 0.800 | 0 | 0 |
| T2 | 22 | 63.64% | [40.91%, 81.82%] | 45.45% | 71.43% | 0.819 | 0 | 0 |
| T3 | 21 | 38.10% | [19.05%, 57.14%] | 38.10% | 100.00% | 0.789 | 0 | 0 |

## Execution provenance

| Analysis mode | N | Share | Successful | Errors/missing | Median wall ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| SOURCE_PLUS_WALA_SMT | 76 | 100.00% | 76 | 0 | 40821 |
