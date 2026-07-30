# Decision and Evidence Log

Chronological record of what was decided, what was measured, and what turned out to be wrong.
Newest entries at the top. The point of this file is to make a later "why did we do it this way?"
answerable without re-deriving anything.

**Evidence grades** used throughout:

| Grade | Meaning |
| --- | --- |
| **measured** | backed by a run whose data is in hand |
| **code-verified** | read in source or git history; impact not measured |
| **inferred** | judgement, no data |
| **unknown** | cannot be determined from what is available |

**Before reporting any performance or accuracy comparison, answer three questions.** Each was
violated at least once in the session below, and each violation produced a result that looked
correct:

1. Are both sides the same build?
2. Is the cache / intermediate-artifact state the same on both sides?
3. If the measurement were broken, would my check notice?

---

## 2026-07-29 — GT round 4 applied, and two layout operators declared untestable

### Round 4: split a mutation reference only when the operator's effect is scattered

**Decision (user-sanctioned).** `split_mutation_runs.py` now splits a mutation reference into
contiguous runs only for operators whose effect is intrinsically scattered — `t2_rename*` alone at
present. Every other operator keeps a single interval, wrap included, since
`correct_wrap_intervals.py` has already reduced that one to the added statement's own line.

**Why round 3 was wrong.** Round 3 split *every* operator. A rename touches its declaration and each
use, so the unsplit interval also claimed the untouched lines between them and the scorer matched a
T1 gap — that part was right, 88.3 % → 99.3 %. But re-indentation shifts one continuous span, and
the line diff breaks at unchanged lines inside it (blank lines), so splitting fragmented a single
edit into pieces, some landing where the detector correctly sees no change. Cost: `t1_reindent`
99.3 % → 94.8 %.

**The justification is the operator's nature, not the resulting number.** This was stated explicitly
when putting the rule to the user, because three GT rounds had already moved figures and a fourth
that happens to raise one is exactly the shape of a result-fitted rule. The rename/reindent
distinction exists in the operator definition, independent of any score.

**measured.** Applied to all four batches (64 / 45 / 55 / 59 references split), rescored from the
preserved raw output into `scored_v4/`. `t1_reindent` **94.8 % → 98.7 %** [97.0, 99.5]; **no other
operator's figure moved.** That one-sided effect is itself the check: a rule that improved several
numbers at once would have been suspect.

Batches 1–4 mutation scale, `scored_v4/`, N = 4336, DEFF 1.10:

| ✅ 98.3–99.8 %, IoU 1.000 | Explained low figures |
| --- | --- |
| `t3_wrap_statement` 99.8 %, `t2_change_string_literal` 99.7 %, `t2_rename_local` 99.5 %, `t1_reindent` 98.7 %, `t2_change_int_literal` 98.3 % | `t3_delete_statement` 89.0 % / `t3_insert_statement` 88.8 % (corpus artefact, fixed in generator, batch 5 validates); `t1_add_eol_comment` 78.2 %, `t1_add_blank_line` 24.9 %, `t1_add_block_comment` 24.0 % (untestable, below) |

Failures decompose as *never captured* 16.6 % vs *captured, typed wrongly* 1.8 %, and 691 of the 718
misses are the two untestable layout operators.

### Blank lines and block comments are untestable at sub-region scale, not poorly detected

**measured, and it falsifies my own earlier framing.** `FREEZE.md` §10 called layout mutations "only
partly measurable" and left ~24 % looking like a weak detection rate. The actual measurement: of 798
blank-line and block-comment references, 195 matched, and **195/195 (100 %) were covered
incidentally** by a sub-region spanning ≥ 3 lines (median 8, p90 12). Not one match came from an
element that pointed at the inserted line.

The mechanism is structural. Sub-regions are derived from statement extents; a blank line or a
comment-only line belongs to no statement, so no sub-region can ever correspond to one. The 24 % is
the probability that the insertion happened to land inside some multi-line statement — a property of
the corpus, carrying no information about the detector.

**Consequence for the write-up:** report these two beside `mARI` and `mSIL` as untestable on a
compilation-based detector. Their percentages must not appear as detection rates. It is also correct
product behaviour — adding only a blank line or a comment highlights nothing, because no code
changed. `t1_add_eol_comment` (78.2 %) is the partly escaping case: the comment attaches to a real
statement, whose extent usually covers it.

### Retracted: "`t3_wrap_statement` is captured but rarely typed T3"

Carried in `FREEZE.md` §10 as a known limitation. **False.** 99.8 % type correct over 402
references. The original 7.3 % was a ground-truth defect in three successive forms (§ round 1 in
`HANDOFF.md`), including one correction that made things *worse* (57.5 %) because `max(added)` was
the closing brace past the whole loop. The detector was right the entire time. Amended in
`FREEZE.md` rather than edited away, so the wrong claim and its correction sit together.

### Where the four GT versions live

`regions_left.csv` (as generated) → `_v2.csv` (round 1, wrap) → `_v3.csv` (round 4, rename runs;
file name kept, content re-derived from `_v2`), scored into `scored/`, `scored_v2/`, `scored_v3/`
(round 3), `scored_v4/` (round 4). **Quote `scored_v4/`.** Raw detector output was never regenerated
for any round — §10/§11 require rescoring the preserved output, and keeping the scorer hash separate
from the code commit is what makes that possible.

---

## 2026-07-27 — What NON_CLONE actually means, and the first negative stratum

### Finding: NON_CLONE is "this proposed correspondence is not a clone", not "this code is original"

**code-verified.** There is exactly one production site,
`NextRegionTypeRecognizer.java:208`, and it is the **fall-through at the end of the cascade**: T1
fails → T2 fails → T3 fails → no SMT/dynamic proof → no cross-method marker → NON_CLONE, with the
path text *"T4 not approved: no independent semantic-equivalence proof or structural region evidence
is attached to this candidate."*

It is therefore a verdict **about a candidate pair Phase A already proposed**. Code that has no
counterpart at all never forms a candidate and never enters the cascade, so it can never be labelled
NON_CLONE. The system has no output that means "this run of statements is original work".

Two consequences:

- The donor block below could not have been matched however well the detector performed — the
  reference asked for a label the output cannot express. That was a defect in the reference.
- **NON_CLONE decisions are invisible in every run made so far.** `AcceptedRegionSelector` filters
  them before selection and the JSON emits them only under `-Dcodesim.emitRejectedRegions=true`, so
  the number of candidates rejected has never been recorded. Blind spot; the switch exists.

### Retracted: "a run of unrelated code will make region growth stop"

**measured on 20 pairs, hypothesis falsified.** A verified-portable donor block was spliced into B
at a random in-method point, so those lines have no counterpart in A.

| | without donor | with donor |
| --- | ---: | ---: |
| pairs yielding exactly one region | 82% | **85%** |
| donor lines falling inside a predicted region | — | **20 / 20** |

Growth does not stop, because the code on *both sides* of the donor still corresponds; the donor is
simply passed over. Splitting a region needs the correspondence itself to break, which inserting
unrelated code into one side does not achieve.

**But the sub-region breakdown isolates it exactly.** For `R00000`, donor at right 5–10:
`T1(4) T3(5-10) T1(11-13) T2(14-17) T1(18-27,29) T3(31)` — the donor is its own run, to the line.
It is typed T3 because our rule types one-sided statements as T3, which cannot distinguish "one
statement was edited here" from "these six lines are entirely new". That distinction is what a
plagiarism reviewer most wants; it is not currently expressible. Open item.

The donor path is kept, off by default, with this result recorded at the switch.

### Decision: the negative stratum is pairs of unrelated files, verified rather than assumed

100 pairs from different CodeNet problems, accepted only when the longest run of **consecutive**
shared tokens stays under 30 — above the measured scaffolding ceiling (p90 = 27). Accepted pairs
share median 15, max 28. 105 attempts produced 100 pairs; 3 were rejected for sharing ≥ 30.

**measured**, all 100 completing the full pipeline (`SOURCE_PLUS_WALA_SMT`, zero fallback):

| | value |
| --- | ---: |
| correct rejection (no region emitted) | **81 / 100** |
| strict-product FP (any region emitted) | **19 / 100** |
| **claimed a syntactic type (T1/T2/T3)** | **2 / 100** |

Emitted on negatives: `POSSIBLE_T4_CANDIDATE` 21, T2 1, T3 1; median true coverage 11 lines.

The 19% and the 2% answer different questions and both must be reported. Every one of the 21
possible-T4 regions came from the same deliberate branch (`NextRegionTypeRecognizer.java:197`): a
cross-method aligned region is a structural fact Phase A established and *"must not be silently
dropped by a similarity number"*, so it is surfaced as a possible clone. On unrelated competition
code the shared IO scaffolding is enough to align across methods. This is the cost of that rule, not
a defect, and §7 already requires `POSSIBLE_T4_CANDIDATE` to be reported separately.

### Retracted: "pairs sharing more scaffolding are the ones that produce false positives"

**inferred, then measured and disproved.** Shared consecutive tokens: **16 median in the pairs that
produced a false positive, 15 in those that did not.** Whether a pair misfires is not driven by how
much boilerplate it shares but by whether that boilerplate happens to form a cross-method aligned
region. Raising the token threshold when selecting negatives would therefore **not** reduce the
false-positive rate — that lever does not work.

---

## 2026-07-27 — A region is not a contiguous span, and not one relationship

Two additive changes to the region output. Neither alters a verdict: the full 100-pair corpus was
re-run after each, and with the new field removed the results are **byte-identical to the previous
run in all 100 pairs**. Tests stayed at 81/81 across every checkpoint.

### Finding: `beginLine`/`endLine` is a bounding box, and 58% of regions are not contiguous

**code-verified then measured.** A region is built from a SET of aligned lines
(`NextEvidenceExtractor.alignedRegion` → `outermostStatementsOverlapping(cu, spanLines)`), and its
span is `min`/`max` over the selected statements (`SourceSpan.union` is literally
`min(begin), max(end)`). The tokens the clone type is decided on come from those statements only —
never from whatever else falls inside the box.

This resolved a contradiction that looked like a detector bug: `R00010` reported lines 12–100 as
T1 "exact copy, 89 lines" while lines 48–50 inside it contained renamed identifiers. Both are true,
because 48–50 was never in the region. Exposing the runs shows what it really is:

| | value |
| --- | ---: |
| reported box | 12–100 (89 lines) |
| actual content | 12–17, 22–23, 96–100 (**13 lines**) |
| overstatement | **6.8×** |

Across 156 emitted regions: bounding box median 36 lines, true coverage median 28, overstatement
median 1.1× but **p90 2.0× and max 8.7×**, and **58% of regions consist of more than one run**.

**Change:** `CodeRegion.segments` carries the runs; `beginLine`/`endLine` are unchanged and still
the bounding box. Both serializers emit it, the SPA highlights by it, and the region label shows the
true line count when it differs from the box. `AcceptedRegionSelector.overlapRatio` still computes
suppression from bounding boxes — **deliberately not changed here**, because that would alter
detector behaviour rather than reporting; 58.9% of accepted regions are suppressed, so the effect
is worth measuring before deciding. Open item.

**Consequence for scoring:** comparing a dense reference range against a sparse bounding box
inflates coverage and deflates IoU at the same time. `score_regions.py` now scores the runs and
reports the bounding-box computation beside it as a control:

| | box | segments |
| --- | ---: | ---: |
| T2 detection | 97% | 93% |
| T3 detection | 100% | 94% |
| T2 median IoU | 0.133 | 0.189 |
| T3 median IoU | 0.113 | 0.151 |
| T3 conditional type accuracy | 94% | 100% |

### Finding: one region routinely holds several relationships, and only the last one was reported

**measured.** Of 156 emitted regions, **92 (59%) carried both rename evidence and statement edits**,
and all 92 were reported as T3 alone. Region growth is maximal — it stops where the two sides stop
corresponding, not where the KIND of difference changes — so a region spanning a renamed run, an
inserted statement and 25 identical lines is genuinely T3 by the cascade, and the T1 and T2
structure inside it had no way to surface.

**Change:** `RegionDecision.subRegions` applies the **same** T1→T2→T3 cascade to one aligned
statement pair at a time and coalesces neighbouring runs of the same type. No new classification
rule is introduced, which matters for both the contract and the write-up:

- the alignment is the existing LCS over `normalizedStatementTexts` that already produces the edit
  script, so a statement whose identifiers were merely renamed is MATCHED — exactly why renaming
  alone leaves the edit script empty and keeps a region at T2;
- the per-pair test is the same pair of comparisons the region-level cascade makes;
- a statement present on only one side is T3, since a statement-level edit is what separates T3
  from T2;
- sub-regions are statement runs inside a method, so the region-level contract still holds — no
  syntactic type is scoped to a declaration.

The region keeps its own type and it remains authoritative. **A count of clones must use one scale
or the other**: a T3 region containing T3 sub-regions is one finding, not several.

Result on the same corpus: every region now carries a breakdown, median 5 sub-regions (max 11), and
**67% of regions contain more than one type inside**.

| Scale | verdicts | T1 | T2 | T3 |
| --- | ---: | ---: | ---: | ---: |
| region | 156 | 50 | **11** | 94 |
| sub-region | 636 | 392 | **150** | 94 |

Renaming was being detected all along — `renameEvidence.detected` was true — but only 11 regions
were ever typed T2. At statement scale there are 150. **The evidence existed; the granularity hid it.**

**Naming, unresolved:** a region typed T2 means the whole region differs only by identifiers; a
sub-region typed T2 means that run does, while the region around it may differ otherwise. The two
must not share a word in the write-up. Deferred.

### Defects found in my own work while making these changes

Recorded because each would have produced a wrong result silently:

- `mvn ... | tail` reports **tail's** exit status, so a failed compile still printed "OK". Every
  build check now tests `$?` directly.
- `statementLinesOf` first paired the *own* line span (nested statements excluded) with statement
  texts built from `tokens()`, which *includes* nested statements. The three construction sites use
  two different text conventions and each needs its matching span convention.
- `statementTexts` and `normalizedStatementTexts` are filled behind two independent `isBlank`
  filters and `statementLines` behind a third. Today they cannot disagree — the T1/T2 views select
  identical tokens and `normalizeT2` never returns blank, both code-verified — but nothing enforces
  it, so `CodeRegion` now throws on a length mismatch rather than mislabelling every later
  sub-region.
- `score_regions.py` had a module-level `lines_of(segments)` shadowed by a local `lines_of(path)`.
  Correct by accident of scoping; renamed.

---

## 2026-07-27 — Spoon operators, the negative stratum, and a valid timing baseline

### Decision: Spoon is a supplementary instrument; official MIF stays as E2

Restated here because the reasoning is easy to lose: `paper_evaluation_protocol.md` §6 requires the
official MIF v1.0 release with recorded version and checksums so published MIF numbers stay
comparable, and the 81% generation / 46% injection drop rates are recorded as **results**, not
defects. A type-safe reimplementation cannot preserve `mSIL` (an undeclared placeholder *is* the
operator) or `mARI` (inconsistent renaming *is* the operator), so 100% compilation across all 15
operators is unreachable without silently redefining them.

Spoon's defensible role is the gap official MIF structurally cannot cover on a compilation-based
detector: `mSIL` (0% survival) and `mML` (≈2.5%) leave two of five T3 operators unexercised.

**The stronger argument for Spoon is not the compile rate.** TXL operators choose their own site and
`gen_mutants.py` recovers it afterwards by diffing; Spoon can place a known edit at a *chosen*
statement range. The region-level contract requires T1–T3 ground truth to be statement-level and
boundary-free, which needs exactly that ability.

### Finding: Spoon's sniper printer is edit-local, which is the property the ground truth needs

**measured**, 39 of 39 files. The first probe asked the wrong question — byte-fidelity against the
original file, which sniper fails (0/60: `class Main{` becomes `class Main {`). That does not
matter: both sides of a pair are Spoon output, so unchanged code only has to match *the other side*.
The property that does matter is edit locality, and renaming one local variable changed exactly the
lines mentioning that identifier — 3.9 lines changed, 3.9 expected, zero extra noise.

### Finding: type-safe operators reach 100% survival; every residual failure was a Java rule

**measured** on 200 seeds (the 7 that do not compile standalone excluded first, so all figures are
mutation-induced only).

| Operator | MIF counterpart | before fixes | after fixes | TXL original |
| --- | --- | ---: | ---: | ---: |
| `ins` | `mIL` | 98.5% | **100.0%** | ~36% (10/28) |
| `del` | `mDL` | 96.0% | **100.0%** | ~91% (10/11) |
| `wrap` | `mML` | 96.0% | **97.5%** | **~1% (2/200)** |
| rename | `mSRI` | 100.0% | 100.0% | ~71% (10/14) |

Zero compile failures across 600 mutations after the fixes. The three residual causes were all
definite-assignment or reachability rules, not a failure of Spoon's type model:

- `ins` → `unreachable statement`: anchored after a `return`.
- `del` → `variable X might not have been initialized`: the safety check covered declarations but
  not *assignments* to a variable declared without an initialiser.
- `wrap` → `missing return statement`: javac does not treat `if (true)` as guaranteeing execution
  (unlike `while (true)`), so wrapping a method's only `return` leaves it able to complete normally.

Each fix narrows where the operator applies. MIF operators already carry applicability predicates,
so this is **not** an additional deviation to declare; the only declared deviation remains
"placeholder made type-correct". `wrap`'s residual 2.5% is applicability (five seeds whose
top-level statements are all declarations or contain `return`), not compilation.

**Retracted: "Spoon 96.5% vs TXL 19%".** The 19% is an aggregate over 15 operators while the Spoon
figure covered renaming only. The comparison above is per-operator.

### Retracted: "without a region-level negative, precision cannot be measured"

**Own invention, not a protocol requirement.** §4 defines localisation as minimum-side **boundary
precision** (`|G∩P|/|P|`) and IoU — over-reach measured on positives alone — and §4.1 defines both
false-positive definitions at pair level. `score_results_v2.py` already emits
`boundary_precision_left/right` and `iou_left/right` (lines 274–277, 321–322), **code-verified**, so
no new mechanism is needed.

This retraction closed a line of work that had already been measured to be unproductive: a library
of portable donor blocks yielded 5 validated blocks from 6,000 files (~63 projected over the full
corpus) and all of them were constant-table initialisation (`week[0]="SUN"`, `hashMap.put(...)`).
That is a **systematic bias**, not bad luck: requiring every variable read to be declared inside the
block excludes anything that processes input, leaving only literal setup. Three probe defects were
found and fixed along the way (Spoon models a comment as a `CtStatement`, so runs of commented-out
code passed every check; "not a declaration" was too lax a definition of substantive; the output
directory was never cleared, so stale blocks from earlier runs survived alongside current ones).

### Finding: unrelated CodeNet files share ~16 tokens of scaffolding, and same-problem pairs share no more

**measured** over 400 different-problem and 188 same-problem whole-file pairs, using the longest run
of *consecutive* shared tokens (token-set overlap is useless here — it is high for any two Java
files).

| | median | p90 | p99 | max |
| --- | ---: | ---: | ---: | ---: |
| different-problem | 16 | 27 | 36 | 59 |
| same-problem | 17 | 29 | 44 | 51 |

The two distributions being identical says the shared runs are not problem logic, and inspection
confirms it directly: `Scanner sc = new Scanner ( System . in ) ;` (11 tokens),
`class Main { public static void main ( String [ ] args ) { Scanner` (15 tokens).

An earlier block-level measurement (median 4, max 5 shared tokens) **does not carry over** to whole
files and was not used.

**Decision:** the negative stratum is different-problem pairs with a longest shared run below **30
tokens** — above the scaffolding ceiling (p90 = 27), and 95.8% of different-problem pairs qualify.
Residual scaffolding still exceeds `RegionGrower`'s floor of two substantive aligned pairs, so the
detector may legitimately report a template region inside a pair labelled non-clone. §4.1 already
prescribes the handling: report both false-positive definitions and audit the strict-product tier.

### Finding: `--skip-dynamic` changes no syntactic verdict, and 76/76 complete

**measured**, `results/mutation-injected-smoke/run3-skipdyn`, cold cache
(`cache=miss, stubs=0, mode=STANDALONE` on all 152 sides), 2 workers × `-Xmx1600m`:

| | value |
| --- | ---: |
| completed | **76 / 76**, all `SOURCE_PLUS_WALA_SMT`, 0 fallback |
| median wall | **40.8 s** / pair |
| p90 / max | 54.9 s / 999 s (heavy tail) |
| total pair work | 102 min → ~51 min wall at 2 workers |
| runner logs | **0 MB** (36 GB with the dynamic tier enabled) |

| Stage | median | share |
| --- | ---: | ---: |
| `regions` | 34.2 s | **87.0%** |
| `smt` | 2.45 s | 6.2% |
| `graph` | 2.24 s | 5.7% |
| `compile` (both sides) | 0.16 s | 0.4% |

Comparing the 59 pairs completed by both this run and `run2` (dynamic enabled): **0 differences in
detection and 0 in type**. This is the empirical confirmation of the claim in
`WalaNextPipelineRunner.java:231` that the syntactic categories never need the dynamic tier, so
freezing `skip_dynamic=true` for T1–T3 costs nothing.

**Retracted: "median 234 s/pair, so skipping the dynamic tier saves only 3%".** That measurement ran
4 JVMs at `-Xmx4g` on an **8 GB** machine — a 2× heap oversubscription — and reported swap pressure,
not pipeline cost. The same oversubscription later killed a run outright. Machine memory was never
checked before choosing `--workers`/`--xmx`; `run_shards.py` defaults to `4g` per shard, so worker
count must always be sized against physical RAM.

**Unknown:** how much of the ~6× improvement comes from skipping the dynamic tier versus from
removing memory pressure. Two variables changed together, and isolating them needs a dynamic-enabled
run at 2 workers, which hangs on the `DynamicEquivalenceChecker` thread-pool leak.

Revised planning figure, from the mean of 80.5 s/pair at 2 workers: 1,000 pairs ≈ 11 h, 2,000 ≈ 22 h.
**`regions` is the constraint on corpus size** — not seed supply (~12,900 usable) and not operator
survival (~100%).

### Finding: the region growth loops are bounded

**code-verified**, after an out-of-memory kill raised the question. `growFrom`'s
`while (!frontier.isEmpty())` only grows the frontier through `tryAdd`, whose `alignment` and
`usedRight` sets are monotonic and bounded by the graph node count; `reachableTargets`'s expansion
enqueues only when `depth < MAX_BRIDGE_DEPTH` and the recorded depth improves. Neither can diverge.
The only non-terminating path in the system remains the known `DynamicEquivalenceChecker` thread
leak, which `--skip-dynamic` avoids.

### Finding: seed availability is not the constraint

**measured** with Spoon over 400 CodeNet files. A first probe required all mutated ranges inside one
method (largest method: median 19 lines, p90 34) and found only 2.3% usable — but that constraint
was **self-imposed and wrong**: the contract forbids a syntactic type from *being* a method, not
from living in different methods. Counting each method's capacity for padded 6-line ranges:

| design | usable | projected over 75,000 files |
| --- | ---: | ---: |
| 2 ranges × 6 lines, 3-line padding | 39.0% | ~29,250 |
| **3 ranges × 6 lines, 3-line padding** | **17.3%** | **~12,937** |
| 4 ranges × 6 lines, 3-line padding | 8.5% | ~6,375 |

**Retracted: "~9,300 files have ≥3 methods".** That came from a regex method counter that also
matched `if (…) {`, `for (…) {` and `catch (…) {`. Spoon's AST count gives median 1 method per file,
p75 = 1, p90 = 5.

The 6-line range is also confirmed against the detector rather than assumed: `RegionGrower`'s
emission floor is `minPairs=2, minSubstantivePairs=2` **graph node pairs** (`RegionGrower()` default,
code-verified), far below 6 lines, and `score_results_v2.py --min-region-lines` defaults to 6. The
earlier "≥6 lines, ≥50 tokens" figure was `bcb_extract.py`'s filter for selecting BCB *references*,
not the detector's threshold.

---

## 2026-07-27 — Injection corpus: first Phase A/B run, and a ground truth that violated the contract

### Finding: a compilable corpus does make Phase A/B run — zero fallback

**measured** on `results/mutation-injected-smoke/run2`, 59 of 76 injected pairs completed
(the run was stopped early, see the disk finding below):

| | value |
| --- | ---: |
| `analysisMode = SOURCE_PLUS_WALA_SMT_DYNAMIC` | **59 / 59** |
| `fallbackStage` non-empty | 0 |
| `compile_left` / `compile_right` SUCCESS | 59 / 59 |
| generated stubs used | **0** (`mode=STANDALONE`) |
| median wall time | 241 s / pair |

This is the first run in the project where every scored pair went through the compiled path.
The contrast is with `results/bcb_smoke`, where **118 of 140 (84%)** took the source-only
fallback — those pairs never exercised the system under test at all.
Cause of the difference, **code-verified**: CodeNet submissions are self-contained single files,
so nothing has to be stubbed.

Three questions: both arms are the same build (`phaseab-full76`, one `run_shards` invocation,
`target/classes` rebuilt at 21:33 with the working-tree `StubGenerator`); compile cache was cold
for both (`cache=miss` on every row); a broken measurement would have shown up as a missing or
empty `analysisMode`, which the scorer counts separately as `MISSING` — and it did, for the 17
pairs that never ran.

### Retracted: "the 44% detection rate is a scoring-convention artifact"

**inferred, then disproved by re-reading the region-level contract.**

Strict c-match (min-side reference coverage ≥ 0.70) over the pairs that actually ran:

| | T1 | T2 | T3 |
| --- | ---: | ---: | ---: |
| ran | 26 | 17 | 16 |
| c-matched | 10 (38.5%) | 9 (52.9%) | 7 (43.8%) |
| conditional type accuracy | 100% | 55.6% | 100% |

Every undetected pair had a predicted region **strictly inside** the reference range
(e.g. GT `(8,21)` vs predicted `(13,18)`). I concluded the reference block was too coarse and
that the metric was penalising boundary definition rather than detection, and I quoted an
`any-correct-overlap` figure of 96.6% (57/59) as the "real" number.

Both moves were wrong:

1. `paper_evaluation_protocol.md` §4 already states that `any-correct-overlap` is **a diagnostic
   only, not a headline result**. Quoting it as the corrected detection rate was exactly the
   substitution the protocol forbids.
2. The actual defect is the ground truth, not the metric. `inject_mutants.py` labels the whole
   injected nested static class as one region, i.e. a **boundary-aligned, class-scoped** reference
   for a syntactic type. The region-level contract requires the opposite: T1/T2/T3 are
   region-level only and must never be scoped to a method (or any other declaration boundary),
   because Phase A is designed to grow regions **without** function boundaries. The detector
   emitting `CALL_EXPANDED_REGION` sub-regions was correct behaviour; the reference was the thing
   in breach.

**Consequence:** T1–T3 reference ranges must be statement-level sub-method ranges. The mutation
operators already edit at that granularity — the class-block reference was introduced by the
injection wrapper, not by the operators.

Also retracted, from the same reasoning: the proposal to assign one clone type per *method*
(T2 in method A, T3 in method B, untouched methods labelled T1). That reproduces, inside the
ground truth, the same method-scoped syntactic typing that was already found and disabled in the
discovRE/kNN channel.

### Finding: host template code breaks the injection premise on CodeNet

**measured.** `inject_mutants.py` assumes "the only thing the two files have in common is the
injected fragment". On CodeNet that assumption is false. Of the 57 pairs with any overlap,
**36 emitted regions outside the reference range, median 77 lines**. Inspected content is
competition boilerplate shared by unrelated submissions:

```java
while (st == null || !st.hasMoreElements())
    st = new StringTokenizer(br.readLine());     // FastReader template
MyScanner sc = new MyScanner();                  // IO template
```

These are **real clones between the two hosts**, correctly reported. The reference table lists
only the injected fragment, so the ground truth is incomplete and no false-positive figure
computed against it is valid. Union-of-regions IoU makes this visible: it scores *worse*
(27.1% ≥ 0.70) than best-single-region (54.2%), because the union absorbs the template regions.

Sampled contamination rate: of 4,000 CodeNet files in the 40–400 line band, **525 contain a
recognisable IO/fast-reader template and 466 do not** — 47% clean. Whole-file token-set Jaccard
between "clean" files still has median 0.383, but that figure is **not evidence of
contamination**: identifier-set overlap is high for any two Java files. Contiguous common token
runs are the quantity a clone detector responds to and were not measured before this direction
was set aside.

### Finding: the dynamic tier must not run over corpus code — thread leak, not just crashes

**measured**, reproduced identically in two independent runs (both died on `mCC_EOL_00003`).

Two distinct failures, both from `-Dcodesim.skipDynamic` being left at its default:

1. **Blocked/failed execution.** `DynamicEquivalenceChecker` invokes the subject's `main()`.
   Every CodeNet submission reads stdin, so the sampled invocation either throws
   `java.io.IOException: Stream closed` at `Main$FastReader.next` or blocks forever.
2. **Non-terminating JVM.** `jstack` on a shard that had already written all 19/19 records showed
   thread pools numbered to `pool-358` — one per dynamic check, never shut down — each a
   non-daemon thread still consuming CPU. The shards ran a further ~2 hours at 100–280% CPU after
   their work was complete and had to be killed; `run_shards` exited 1 as a result.

`paper_evaluation_protocol.md` §5 already freezes `skip_dynamic=true` for the primary runner and
§7 prohibits dynamic execution of external corpus code in the ordinary batch JVM. The protocol was
not consulted before the run. Both failures are fully avoided by honouring it.

### Finding: WALA debug output is an operational hazard at batch scale

**measured.** Four shards wrote **36 GB** of `got NEW <...> in Node: <...>` call-graph tracing in
roughly one hour, taking free disk from 57 GiB to 13 GiB. The string is assembled at runtime
(`"got " + instanceKey`), is not present as a literal in any classpath jar, and `BatchPairMain`
has no quiet flag — so it cannot be switched off from the outside.

Mitigation used, **measured** as effective: an external watchdog truncating each shard log in
place above 200 MB. `run_shards` opens logs with `O_APPEND`, and truncation under `O_APPEND`
frees the space immediately and restarts the next write at offset 0 with no sparse hole
(verified: 1,000,000 → 0 → 100 bytes). Over the second run it fired 202 times and free disk stayed
flat. The logs themselves were deleted afterwards; they are regenerable debug output, not code.

### Decision: Spoon is evaluated as a *supplementary* instrument, not a replacement for MIF

**measured** on 200 CodeNet seeds, scope-aware Type-2 renaming via Spoon 11.5.0
`CtRenameLocalVariableRefactoring`:

| | value |
| --- | ---: |
| pairs kept | **193 / 200 (96.5%)** |
| dropped because the *mutation* broke compilation | **0** |
| dropped because the *seed* does not compile standalone | 7 |
| regions per pair | 3.7 |
| lines per region | 2.0 |

A prerequisite was measured first, because the whole approach depends on it. Spoon regenerates
source from its model, so the question is not fidelity to the original file — both sides of a pair
are Spoon output — but **edit locality**: does a local edit change only local text? With the
sniper printer, renaming one local variable changed exactly the lines mentioning that identifier
in **39 of 39** files (3.9 lines changed, 3.9 expected, zero extra noise). Unmodified code is
therefore byte-identical between the two sides.

**The headline comparison "96.5% vs TXL's 19%" is not apples-to-apples and is retracted.** The
19% is an aggregate over 15 TXL operators; the Spoon figure covers renaming only. The fair
comparison is against the TXL operators of the same kind: `mSRI` ≈ 71% (10 kept / 14 tried),
`mARI` ≈ 7% (10 / 137). TXL's six T1 operators were already 100%.

Why Spoon does not replace MIF:

- §6 requires the official MIF v1.0 release with recorded version and checksums, so published
  MIF results stay comparable. A reimplementation is a different instrument.
- The 81% generation drop and 46% injection drop are recorded as **results** — properties of the
  operators and of the injection method. Engineering them away deletes a finding rather than
  producing one.
- Some MIF operators are compilation-breaking *by definition* (`mSIL` inserts an undeclared
  placeholder; `mARI`'s whole point is inconsistent renaming). No type-aware reimplementation can
  preserve their definition and also compile. 100% compilation across all 15 operators is
  therefore **not achievable**, and claiming it would mean silently redefining the operators.

Spoon's defensible role is to cover what official MIF structurally cannot test on a
compilation-based detector: `mSIL` (0% survival) and `mML` (≈2.5%) leave two of five T3 operators
effectively unexercised. That gap is worth its own reported experiment, with the deviation from
the MIF definitions stated explicitly.

### Finding: CodeNet seeds are too small for declaration-scoped region design

**measured** over 193 seeds: median **1 method** and **24 lines** per file; 75% have at most one
method. Filtering to ≥60 lines *and* ≥3 methods leaves 12% of the corpus (~9,300 files of 75,000).
This was measured while evaluating the method-scoped design that has since been retracted above;
it is retained because it also bounds any future design that needs multiple declarations per seed.

---

## 2026-07-26 — MIF mutation corpus

### Decision: seed corpus is CodeNet Java250, not IJaDataset or TheAlgorithms

**measured** compile rate, sampled, with the detector's own public-type renaming applied:

| Corpus | standalone | with `-sourcepath` |
| --- | ---: | ---: |
| IJaDataset (`bcb_reduced`) | 14% | 14% (no gain) |
| TheAlgorithms/Java | 78% | 100% |
| **CodeNet Java250** | **100%** | — |

IJaDataset fails on missing third-party jars (`junit.framework`, `org.apache.commons.logging`,
`org.osgi.framework`, …), not on missing siblings — which is why `-sourcepath` adds nothing there.
Its layout is one directory per *functionality*, so a file's real siblings are not co-located; this
is confirmed by BigCloneEval's own docs, not inferred.

TheAlgorithms was the first pick and was **wrong to choose without comparison**: it is a single
educational repository, has no citation, and gives no cross-project diversity. CodeNet Java250 is
citable (IBM, arXiv 2105.12655), already E5 in the evaluation protocol, 75,000 self-contained
files (58,252 in the 20–400 line band), 14.6 MB download, and same-problem solutions double as
semantic-clone material.

Qualitas.class was the stronger candidate on paper (real systems, two publications, a *compiled*
distribution) but **its host is offline** — three URLs return connection failure, not 404.

### Finding: several MIF operators cannot produce compilable code, by design

**measured** on 354 generated mutants: 286 dropped (81%) because the mutant does not compile.

| Operator | Transformation | Compiles? |
| --- | --- | --- |
| `mSIL` | `sc.nextInt()` → `sc.nextInt(X1)` | never — `X1` is undeclared |
| `mML` | `f(x);` → `if (X==Y) f(x);` | never — `X`, `Y` undeclared |
| `mARI` | renames a declaration **only** | rarely — uses are left dangling |
| `mSRI` | renames a declaration **and all uses** | yes |
| T1 operators (whitespace/comments/formatting) | layout only | always |

The framework targets **text-based** detectors (NiCad, SourcererCC), which never compile anything,
so an undeclared placeholder is harmless there. It is not harmless for a compilation-based
detector. `mARI` is the clearest case: inconsistent renaming is the *point* of the operator, and it
is unusable here for exactly that reason.

Consequence: the nominal "15 operators × 400 = 6,000 pairs" target is not reachable. T3 has 5
operators but only 3 usable ones. **Report generated-vs-kept counts; the drop rate is a property of
the operators, not a silent filter.**

### Decision: the generator checks compilability and drops non-compiling pairs

`scripts/experiments/gen_mutants.py` replaces `gen_mutants.sh`, which needs `shuf`, `mapfile` and
`timeout` and therefore only ran under WSL. The Python version also records the **mutated line
range on both sides**, giving this corpus a localisation ground truth that neither BigCloneBench
(pair-level ranges only) nor the in-house set (no ranges) provides.

### Retracted: "TXL's trailing newline corrupts the range annotation"

**inferred from a `diff` marker, then disproved.** `\ No newline at end of file` is diff-command
output, not a line-content difference: Python `splitlines()` treats `"a\nb"` and `"a\nb\n"`
identically. Checked all 300 pairs — ranges before and after normalisation are identical in every
one. **No change was made.** The 18 multi-hunk diffs are all `mSRI` doing consistent renaming
across every occurrence, which is correct behaviour.

---

## 2026-07-26 — Pipeline changes

### Decision: the pre-Phase-A discovRE/kNN channel is off by default

`-Dcodesim.legacyCfgChannels=false` is now the default; the switch and the classes stay so the
comparison is reproducible on a future corpus.

**measured** over 209 pairs (36 labelled clones, 140 negatives), same build (class fingerprint
verified identical before and after both arms), warm compile cache:

| | on | off |
| --- | ---: | ---: |
| pair-level recall | 36/36 | 36/36 |
| pair-level false positives | 0/140 | 0/140 |
| method-scoped T1/T2/T3 regions | 68 | **0** |
| Phase A regions emitted | 124 | **145** |
| graph stage total | 528 s | **370 s** (−30%) |
| end to end | 5551 s | **4523 s** (−18.5%) |

The region increase was not expected. Cause, **code-verified**: `AcceptedRegionSelector.kindPriority`
ranks `METHOD` (6) above `CALL_EXPANDED_REGION` (5), and `lowerPriorityContained` then suppressed
Phase A regions contained in a method candidate. Suppression fell from 77% to 52% of candidates.
The channel was not merely adding method-level output — it was **hiding region-level output**.

### Decision: Phase B method-pair candidates may only yield T4 or NON_CLONE

A candidate whose evidence is *only* `SEMANTIC_EQUIV_SCAN` or `DYNAMIC_EQUIV_SCAN` now skips the
T1/T2/T3 layers. Those channels say "these two METHODS behave alike" and make no syntactic claim;
running them through the syntactic cascade produced a method-scoped T2, which the region-level
result contract excludes. A candidate carrying *any* syntactic evidence still runs the full
cascade, so the strict T1→T2→T3→T4 order is unchanged.

- **measured**: re-running the same 209 pairs produced **byte-identical output** — 0 pairs changed.
  That proves *no regression*; it does **not** prove the fix works, because those runs used
  `-Dcodesim.skipDynamic=true` and never triggered the path.
- **measured** on 30 MIF pairs **with the dynamic tier enabled** (20 reached
  `SOURCE_PLUS_WALA_SMT_DYNAMIC`, dynamic stage SUCCESS on all 20): method-scoped syntactic
  verdicts on the WALA path = **0**. The 14 that remain all come from the source-only fallback,
  where method and window regions are the only thing available.

### Decision: candidate discovery no longer applies a similarity threshold

`NextCandidateDiscovery` gated proposals at the same 0.50 Type-3 boundary the recognizer uses, so a
region pair below it never became a candidate and could not even be reported as refused. That
contradicts the pipeline's own rule that discovery proposes and Stage 4 decides.

### Decision: region growth requires ≥2 aligned *substantive* nodes

**measured** on 1,183 grown regions: 599 projected to no source tokens, and every one of them fails
this floor — 356 with no source mapping at all, 243 with exactly one substantive node and a
one-line span. So the floor makes an accidental filter explicit rather than changing behaviour.

**Retracted along the way**: "58.6% of Phase A regions are lost at projection, including 26 real
T2 regions." **inferred, then disproved** — all 599 are degenerate single-node regions, the same
ones that produced 14 false positives on negatives. A1 and A3 were one problem, not two.

### Retracted: "replace the T3 text threshold with graph alignment"

The first scan (BCB smoke, **no negative samples at all**) recovered 3 missed MT3 pairs and showed
a clean `alignedFraction` gap. With 126 negatives added, **pure graph evidence produced a 100%
false-positive rate (126/126)** and the gap vanished — positives from 0.500, negatives from 0.503.

Corrected conclusion: graph evidence is an **additional necessary condition**, not a replacement.
Graph AND ≥2 substantive nodes AND text similarity ≥0.50 gives 36/36 recall with 0/126 false
positives, strictly better than text alone (2/126). Lowering the text floor to 0.25–0.40 recovers
the 3 MT3 pairs at the cost of 1/126 — **not adopted**: that trade rests on 5 data points and the
threshold was derived by looking at the test data.

---

## 2026-07-26 — Environment and datasets

- **TXL 10.8b** installed to `~/bin` (user-level, no sudo). Its bundled Gatekeeper step is obsolete
  on current macOS ("This operation is no longer supported"); the quarantine attribute had to be
  cleared separately.
- **MIF operators** extracted to `~/codesim-mif/mutators-java` (172 KB, self-contained: only needs
  `jtokens.grm` and `random.mod`, both alongside). The 3.1 GB clone was deleted.
- **`~/Downloads` is unreadable** to tooling under macOS privacy protection — `ls` returns empty
  and `cp` fails with "Operation not permitted" while `stat` succeeds. An empty listing there is
  **not** evidence that a file is absent; that mistake was made once in this session. Data was moved
  to `~/codesim-data/`.
- **BigCloneBench and IJaDataset are downloaded** (5.5 GB and 618 MB). Decision: BCB experiments run
  on the Windows machine (see `scripts/experiments/RUNBOOK_WINDOWS.md`), MIF on the Mac.

### Finding: WALA cannot analyse code whose dependencies do not resolve

**measured**, controlled experiment on WALA's **source** front-end (`com.ibm.wala.cast.java.ecj`):

| Input | Result |
| --- | --- |
| `Target.java` + `Helper.java` | call graph built, 8 nodes, 2.2 s |
| `Target.java` alone | `ClassHierarchyException` → NPE at `MethodInvocation.resolveMethodBinding()` |

So the source front-end removes the need to produce `.class` files but **not** the need for
resolvable bindings. Compilation success is a hard precondition for Phase A on both front-ends.

Setup notes if this is ever retried: WALA's own POM pairs `jdt.core-3.36.0` with `ecj-3.44.0`,
which are incompatible (`NoSuchMethodError` on `Scanner.getNextToken`); use matching 3.36.0. Needs
the full 31-jar Eclipse platform closure and WALA's own scope initialisation (`SourceDirCallGraph`).

### Decision: `-sourcepath` resolves real sibling sources before generating stubs

New `SOURCE_PATH_CONTEXT` compilation mode. Two-pass, because a single javac run would emit the
siblings into the analysed classes directory and WALA would load them as Application classes —
i.e. as clone candidates. Pass 1 compiles with `-sourcepath` into `sibling-classes/`; pass 2
recompiles the target alone against those into `classes/`; the target's own class files are then
stripped from the context directory.

**measured** on the fixture: `classes/` holds only `Target.class`, `sibling-classes/` only
`Helper.class`. Unlike stubs this context is real code, so it keeps full T4 eligibility.

**Not applicable to BigCloneBench** (`bcb_reduced` groups by functionality, so pointing a source
path at that directory would pull in unrelated projects' classes). `--project-root-depth` therefore
defaults to 0.
