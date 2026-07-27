# Region-level scoring summary

c-match threshold: 0.70 minimum-side reference coverage.
Primary matched prediction chosen by boundary quality without consulting its type.
Regions from pairs with no result stay in the denominator.

| Expected | N | Detection | Typed recall | Conditional type accuracy | Median min IoU | Median min boundary precision |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| T2 | 100 | 93.00% | 11.00% | 11.83% | 0.189 | 0.189 |
| T3 | 100 | 94.00% | 94.00% | 100.00% | 0.151 | 0.151 |

## Control: same scoring on the bounding box (begin..end)

Reported because the difference is the point: a prediction that merely ENCLOSES the
reference scores full coverage here while covering almost none of it.

| Expected | N | Detection | Median min IoU |
| --- | ---: | ---: | ---: |
| T2 | 100 | 97.00% | 0.133 |
| T3 | 100 | 100.00% | 0.113 |

## Type confusion (expected -> primary matched prediction)

| Expected | Predicted | N |
| --- | --- | ---: |
| T2 | NOT_DETECTED | 7 |
| T2 | T2 | 11 |
| T2 | T3 | 82 |
| T3 | NOT_DETECTED | 6 |
| T3 | T3 | 94 |

## Execution provenance

| Analysis mode | Regions |
| --- | ---: |
| SOURCE_PLUS_WALA_SMT | 200 |
