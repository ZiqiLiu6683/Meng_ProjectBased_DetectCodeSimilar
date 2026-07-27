# Region-level scoring summary

c-match threshold: 0.70 minimum-side reference coverage.
Primary matched prediction chosen by boundary quality without consulting its type.
Regions from pairs with no result stay in the denominator.

| Expected | N | Detection | Typed recall | Conditional type accuracy | Median min IoU | Median min boundary precision |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| T2 | 100 | 97.00% | 11.00% | 11.34% | 0.133 | 0.133 |
| T3 | 100 | 100.00% | 94.00% | 94.00% | 0.113 | 0.113 |

## Type confusion (expected -> primary matched prediction)

| Expected | Predicted | N |
| --- | --- | ---: |
| T2 | NOT_DETECTED | 3 |
| T2 | T1 | 3 |
| T2 | T2 | 11 |
| T2 | T3 | 83 |
| T3 | T1 | 1 |
| T3 | T2 | 5 |
| T3 | T3 | 94 |

## Execution provenance

| Analysis mode | Regions |
| --- | ---: |
| SOURCE_PLUS_WALA_SMT | 200 |
