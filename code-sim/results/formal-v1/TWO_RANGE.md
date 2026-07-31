# Two-range stratum — 615 pairs

Pooled from `tworange`, `tworange2`, scored from `scored_v4/`. 4147 references over 176 CodeNet problems.

**398 of 615 pairs drew DIFFERENT clone types for their two ranges** (65 %). That split is the experiment.

`t1_add_blank_line` and `t1_add_block_comment` are excluded: no sub-region can correspond to a line that belongs to no statement, so they are untestable at this scale rather than poorly detected. `--keep-layout` shows them.

## Does a second relationship of a different kind break the type verdict?

| Two edits in one pair | Scale | N | Type correct | 95 % CI |
| --- | --- | ---: | ---: | --- |
| different types | region | 657 | 57.7 % | [53.9, 61.4] |
| different types | sub-region | 709 | 89.8 % | [87.4, 91.9] |
| same type | region | 332 | 91.3 % | [87.7, 93.8] |
| same type | sub-region | 368 | 91.0 % | [87.7, 93.5] |

**Region-level typing loses 33.6 points when the two edits differ in kind; sub-region typing loses 1.2.**

A region carries one type, so when a T2 edit and a T3 edit fall inside the same grown region the cascade can only report one of them. The sub-region layer applies the same T1→T2→T3 cascade per aligned statement pair, which is why it does not degrade.

## Region-level type errors, by whether the pair was mixed

| Expected | Predicted | Mixed pair? | N |
| --- | --- | --- | ---: |
| T2 | T3 | yes | 86 |
| T1 | T2 | yes | 72 |
| T1 | T3 | yes | 58 |
| T3 | T1 | yes | 2 |
| T3 | T2 | yes | 1 |

