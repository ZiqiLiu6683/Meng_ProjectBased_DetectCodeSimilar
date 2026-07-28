# Formal run freeze — region corpus (E2-adapted)

Frozen 2026-07-28T05:01:42Z, ahead of the expensive run, per
`evaluation/paper_evaluation_protocol.md` §12. Nothing listed here changes while the run is in
progress. If any of it has to change, the run restarts and this file is superseded rather than
edited.

## 1. Code revision

| | |
| --- | --- |
| commit | `0e0da86cdc3e71216d6430924d70870348a02f85` |
| branch | `codex/paper-eval-cloud` |
| dirty worktree | **false** |
| JDK | OpenJDK 17.0.18 (the Maven enforcer rejects any other major version) |
| Spoon (generator only) | 11.5.0 |

The generator and the system under test share this one commit, as §10 requires. `run_shards.py`
stamps `code_commit` and `dirty_worktree` onto every result, so a run that drifts off this revision
is visible in its own output rather than discovered later.

## 2. Corpus

| | |
| --- | --- |
| source | IBM Project CodeNet, Java250 (arXiv 2105.12655) |
| archive | `Project_CodeNet_Java250.tar.gz` |
| archive SHA-256 | `48004d058cf52300f77d7a26fa1b6a2ff14bec3f05ed68835a13147990e61316` |
| files | 75,000 |
| role | E5 in the protocol; used here as the seed supply for the E2-adapted mutation corpus |

## 3. Strata and sample sizes

| Stratum | Pairs | Ranges/pair | Purpose |
| --- | ---: | ---: | --- |
| positive, main | 5,000 | 1 | per-operator sensitivity; one mutation per pair keeps the region type unambiguous |
| positive, multi-relationship | 600 | 2 | can several relationships in one pair be separated? Supply-capped |
| negative | 1,000 | — | specificity; different-problem pairs sharing < 30 consecutive tokens |

Sizing is not the supply ceiling but a precision target. For a proportion, the 95 % half-width is
`1.96·sqrt(p(1-p)/n)`. The unit of analysis is the operator (§6 asks for per-operator recall), and
with one range per pair each pair contributes one mutation reference, so 5,000 pairs give 500 raw
samples per operator across ten operators. Clustering by CodeNet problem (§7) with 250 problems
gives `m ≈ 20` and, at an assumed ICC of 0.05, `DEFF ≈ 2.0`, so the effective per-operator sample is
about 250 and the half-width at p = 0.9 is **≈ 4 %**.

The negative stratum is sized the same way: the pilot measured 81 % correct rejection, so 1,000
pairs give a half-width of ≈ 2.4 %.

**ICC is assumed, not measured.** The realised value is computed from the run and reported; if it is
materially higher than 0.05 the confidence intervals widen and that is stated rather than hidden.

## 4. Generator configuration

| | |
| --- | --- |
| source SHA-256 | `0ed9248fa564d0ed9a97140a7e5218eae6a8135e7f7713cd3d3f11967540f607` |
| negatives source SHA-256 | `0534febfb81ff275f7d4187206d84185a450c70398186c2042bf628dc8387ae5` |
| range size | 6 lines (matches the scorer's `--min-region-lines` default) |
| padding | 3 untouched lines on both sides, inside the same method |
| RNG seed | per batch, recorded with the batch |
| operator assignment | balanced schedule, shuffled once with `seed ^ 0x5eed` |

Ten operators stand in for the MIF families, each type-safe by construction:

| Type | Operator | MIF counterpart |
| --- | --- | --- |
| T1 | `t1_add_eol_comment`, `t1_add_blank_line`, `t1_add_block_comment`, `t1_reindent` | mCC_EOL, mCF_A, mCC_BT, mCW_A |
| T2 | `t2_rename_local`, `t2_change_int_literal`, `t2_change_string_literal` | mSRI, mRL_N, mRL_S |
| T3 | `t3_insert_statement`, `t3_delete_statement`, `t3_wrap_statement` | mIL, mDL, mML |

**Declared deviation from MIF:** the placeholders are made type-correct. The TXL originals express
their edit with undeclared symbols — `mSIL` inserts `X1`, `mML` wraps in `if (X==Y)` — which cannot
survive compilation and were measured at 0 % and 2.5 % survival. `mARI` and `mSIL` have no
equivalent here at all, because inconsistent renaming and an undeclared placeholder ARE those
operators; they are reported as untestable on a compilation-based detector rather than redefined.
Applicability predicates are narrowed (no insertion after a `return`, no deletion of a value still
read, no wrapping of a statement containing `return`), which is not a further deviation: MIF
operators carry applicability predicates too.

## 5. Detector thresholds (unchanged by this work)

| Threshold | Value | Where |
| --- | --- | --- |
| minimum region | 2 aligned pairs, 2 substantive | `RegionGrower()` default |
| T1 | T1-comparable token sequences 100 % identical | `NextRegionTypeRecognizer` |
| T2 | T2-normalised sequences identical AND empty edit script | same |
| T3 | edit script non-empty AND syntactic similarity ≥ 0.50 | `BIGCLONEBENCH_T3_MIN_SYNTACTIC_SIMILARITY` |
| structural region coverage | 2.0 | `MIN_STRUCTURAL_REGION_COVERAGE` |
| dynamic tier | **disabled** (`-Dcodesim.skipDynamic=true`) | §5, §7 |

Disabling the dynamic tier costs nothing here and was verified, not assumed: over the 59 pairs
completed both with and without it, **0 detection and 0 type verdicts differed**.

## 6. Runner configuration

| | |
| --- | --- |
| workers | 2 |
| heap | `-Xmx1600m` per worker |
| shards | 4 per batch |
| max attempts | 1 |
| compile cache | fresh directory per batch, so every batch starts cold |
| stubs | enabled (default); measured `stubs=0` throughout, the corpus being self-contained |

Two workers at 1600 MB is a hardware constraint, not a preference: the host has **8 GB**, and an
earlier run at 4 workers × 4 GB oversubscribed it by 2× and was killed by the OS. Worker count must
be sized against physical RAM.

## 7. Scoring

| | |
| --- | --- |
| script | `scripts/experiments/score_region_corpus.py` |
| SHA-256 | `602f7f997a9057e925f602cc5e951d480c81519b86b1cddad141182f8788b84c` |
| tests | `scripts/experiments/tests/test_score_region_corpus.py` |
| tests SHA-256 | `d5e302ef33f201e23a60f5c4149375d529fe63f5392e67dc32e7fa3f0b7d57ff` |
| test count | 14, all passing |

Recorded separately from the code commit so a scoring defect can be repaired and the immutable raw
outputs rescored without re-running the product (§10).

Rules, both preregistered:

- **clone interval → region**, c-match = min-side reference coverage ≥ 0.70 (§4)
- **mutation interval → sub-region**, capture = any overlap (§6), and the type must match
- **untouched run → sub-region**, expected T1

Predictions are read from `segments`, never from the bounding box; the bounding-box computation is
reported beside it as a control, because the difference is itself a finding. The primary match is
chosen by boundary quality without consulting its type, so the scorer cannot search overlapping
predictions for whichever carries the expected label.

The tests were themselves verified by mutation: three rules were deliberately broken — matching by
type, applying the overlap rule to clone intervals, and ignoring `segments` — and each break failed
its own test and only its own.

## 8. Exclusion, timeout and failure policy

- Every attempted case stays in the denominator. Errors, timeouts and fallbacks are outcomes, not
  exclusions (§11).
- Generation-side drops are results in their own right and are reported per reason and per operator.
  No filter is applied after seeing detector output.
- `max-attempts 1`: a failed shard is reported, not retried, so a retry can never mask a failure.
- A batch is scored only when its record count equals its manifest row count.

## 9. Preflight (passed 2026-07-28)

50 pairs, 1 % of the main stratum, on this exact configuration:

| Gate | Result |
| --- | --- |
| one parseable record per case | 50 / 50; 0 missing, 0 duplicate, 0 extra |
| provenance on every result | all fields present; 8 stages carry status on all 50 |
| analysis mode | 50 / 50 `SOURCE_PLUS_WALA_SMT`, zero fallback |
| resume idempotent | re-run produced 50 records again, 0 duplicated, 0 lost |
| scorer end to end | all three reference kinds scored, no anomaly |
| timing | median 49.4 s, mean 49.9 s per pair |

At 49.9 s and two workers: 1,000 pairs ≈ 6.9 h, 5,000 ≈ 34.7 h, all 6,600 ≈ 45.8 h. The run is
split into batches of ~1,000 so a failure costs one batch rather than the whole corpus.

## 10. Known limitations carried into the run

Stated here so they are not discovered as surprises in the results:

- **Suppression uses bounding boxes.** `AcceptedRegionSelector.overlapRatio` computes containment
  from `beginLine`/`endLine`, and 58.9 % of accepted regions are suppressed. Since 58 % of regions
  are non-contiguous, some of those suppressions rest on an overlap that does not exist in the
  content. Deliberately unchanged for this run: fixing it alters detector behaviour, which needs its
  own measurement.
- **NON_CLONE decisions are invisible.** They are filtered before selection and emitted only under
  `-Dcodesim.emitRejectedRegions=true`, so the number of rejected candidates is not recorded.
- **NON_CLONE cannot mean "original work".** It is the cascade's fall-through for a candidate pair
  Phase A proposed; code with no counterpart never becomes a candidate. There is no output for "this
  run of statements is new", so a reviewer's most useful distinction — edited versus newly written —
  is not expressible.
- **Layout mutations are only partly measurable at statement scale.** A blank line or comment line
  is not a statement, so no sub-region corresponds to it; the pilot captured 15–25 % of those.
  Structural, not a detection failure.
- **`t3_wrap_statement` is captured but rarely typed T3.** The wrapped statement's tokens are
  unchanged, so at statement granularity it still matches and is typed T1; the added `if (true) {`
  is the T3 part.
- **The two scales share vocabulary.** A region typed T2 and a sub-region typed T2 do not mean the
  same thing. Naming is unresolved and must be settled before write-up.
- **ICC is assumed at 0.05** for sizing, and measured afterwards.
