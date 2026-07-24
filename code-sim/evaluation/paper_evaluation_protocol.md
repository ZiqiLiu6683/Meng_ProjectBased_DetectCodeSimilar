# Paper Evaluation Protocol

Status: draft for preregistration
Protocol version: 0.1
Drafted: 2026-07-21

## 1. Evaluation target

The evaluated product accepts two Java source files and reports zero or more
matched source-region pairs. Each reported region carries a clone-type evidence
label. The product is not a corpus-wide clone search engine and does not receive
an unlabeled repository from which it must discover all candidate file pairs.

The evaluation therefore makes two deliberately separate claims:

1. **Pairwise detection and localization:** given two files, can the system find
   a benchmark reference clone region and recover its boundaries?
2. **Region-level type attribution:** when the system emits a region, is the
   evidence label assigned to that region correct?

BigCloneEval's coverage rule is used as a scoring convention. Results from this
pairwise task must not be presented as directly equivalent to corpus-search
recall reported by tools that must first discover candidate pairs in a large
repository.

## 2. Evidence hierarchy and dataset roles

| ID | Dataset or experiment | Primary role | Main claim |
| --- | --- | --- | --- |
| E0 | Existing in-house and generated cases | Development and regression only | Engineering correctness; not headline evidence |
| E1 | BigCloneBench / BigCloneEval | Real-world T1--T3 reference-region detection | BCEval-style coverage recall, type recovery, localization |
| E2 | Mutation and Injection Framework v1.0 | Controlled synthetic T1--T3 transformations | Per-operator sensitivity and mutation capture |
| E3 | SemanticCloneBench | Human-curated semantic clones | T4 sensitivity and evidence-tier distribution |
| E4 | GPTCloneBench | Larger semantic-clone stress set | T4 scale and model-generated-code external validity |
| E5 | Project CodeNet Java250 | Executable same-problem and hard-negative pairs | T4 scale, specificity, performance, and input-size analysis |
| E6 | Ablations and pairwise baselines | Contribution analysis | Which pipeline components cause each gain or cost |
| E7 | Performance and reliability sweeps | Operational behavior | Latency, throughput, memory, fallback, and determinism |

The July 2026 wrapper-fragment BigCloneBench run is quarantined as an engineering
pilot. It must not be used as paper evidence or mixed with the clean-room run.
Every final headline result comes from a frozen code revision, official-input
lock, label-free execution manifest, separate reference table, configuration,
and scoring implementation.

## 3. Research questions

### RQ1: Pairwise detection and localization

How often does the system return a region pair that covers at least 70% of both
sides of a reference clone, and how accurately does that prediction localize the
reference boundaries?

### RQ2: Region-level T1--T3 type attribution

Among c-matched reference regions, how accurately does the system distinguish
T1, T2, and T3? Among all emitted regions in an independently annotated sample,
how precise are the assigned region labels?

### RQ3: T4 effectiveness

How often does the system identify externally labeled semantic clones, which
evidence tier produces the result, and how often does it reject hard semantic
non-clones?

### RQ4: Pipeline coverage and failure behavior

What proportion of inputs complete compilation, WALA graph analysis, SMT, and
dynamic analysis? What performance is obtained end-to-end, on the WALA-eligible
subset, and on the source-only fallback subset?

### RQ5: Performance and scalability

How do end-to-end latency, stage time, peak memory, and throughput scale with
source lines, tokens, methods, graph size, and parallel worker count?

### RQ6: Component contribution

What accuracy and cost changes result from disabling structural WALA analysis,
SMT verification, dynamic evidence, region growth, or candidate prefiltering?

### RQ7: Reproducibility and robustness

Are outputs deterministic under repeated runs, and are conclusions stable across
the pinned cloud environment and supported JDK configurations?

## 4. Fixed scoring definitions

For a ground-truth region pair `G = (G_left, G_right)` and a predicted region
pair `P = (P_left, P_right)`, calculate on each side:

- reference coverage: `|G intersect P| / |G|`;
- boundary precision: `|G intersect P| / |P|`;
- intersection over union: `|G intersect P| / |G union P|`.

The official-style c-match is:

`min(reference_coverage_left, reference_coverage_right) >= 0.70`.

Raw-line coverage is reported for BigCloneEval comparability. Substance-line
and token-level IoU are reported in parallel to prevent a whole-file prediction
from appearing to have good localization merely because it contains the entire
reference region.

If several predictions c-match one reference pair, the primary matched
prediction is selected deterministically by maximum minimum-side IoU, followed
by maximum minimum-side boundary precision, then stable candidate ID. This
prevents an evaluation from searching all overlapping predictions only for the
one with the desired type label.

Primary metrics are:

- **Detection recall:** c-matched reference pairs / all reference pairs.
- **Typed recall:** reference pairs whose primary matched prediction has the
  correct type family / all reference pairs.
- **Conditional type accuracy:** correctly typed primary matches / detected
  reference pairs.
- **Localization:** median and distribution of minimum-side boundary precision
  and IoU.
- **Type confusion matrix:** T1, T2, T3, strict T4, and no detection.

An optimistic `any-correct-overlap` score may be retained as a diagnostic only;
it is not a headline result.

### 4.1 Negative-pair scoring

Two negative definitions are reported because they answer different questions:

1. **Reference-range false positive:** a predicted clone covers at least 70% of
   both benchmark negative reference ranges. This is closest to the existing
   BigCloneEval-style scorer.
2. **Strict product false positive:** the system emits any clone region meeting
   the preregistered minimum clone size. This reflects the region-reporting
   product but can count a real small clone inside a pair labeled non-clone at
   full-function level. A stratified manual audit is therefore required.

Report specificity, false-positive rate, balanced accuracy, MCC, and precision
under explicitly stated clone prevalences. Do not report precision from an
artificial positive/negative mixture as though it were population precision.

### 4.2 Region-level annotation

BigCloneBench labels the benchmark reference pair, not every smaller region
inside it. It cannot by itself validate the claim that every emitted subregion
has the correct type. A locked, stratified sample of emitted regions will be
independently labeled by two annotators, balanced across predicted type, dataset,
backend mode, and overlap status. Report raw agreement, Cohen's kappa, the
adjudication process, and per-type precision with 95% confidence intervals.

Recommended target: at least 800 annotated regions, with at least 150 candidate
regions for each of T1, T2, T3, and T4 where the output distribution permits.

Only one human annotator is currently available. The annotation tool will hide
the detector prediction and evidence scores until a human label is committed.
An AI assistant may independently propose a label and explanation, but it is not
reported as a second human annotator. All human/AI disagreements are reviewed by
the human annotator. A locked random subset of at least 15% is relabeled by the
human after a delay and used to report intra-rater agreement. The thesis must
describe this as single-human, AI-assisted annotation and list it as a validity
limitation; it must not report human-human Cohen's kappa.

## 5. BigCloneBench experiment (E1)

Use the official BigCloneEval database schema, official minimum-size filters,
and `BOTH` similarity (`min(line similarity, token similarity)`). Report T1,
T2, VST3, ST3, and MT3 separately. WT3/T4 is not used as reliable T4 ground
truth.

The primary end-to-end run includes every outcome:

- full WALA semantic region pipeline;
- source-only fallback;
- analysis error or timeout.

The same raw run is then stratified into:

- WALA-completed cases;
- compile-ineligible cases;
- WALA/graph/region failures after successful compilation;
- source-only fallback cases.

No case-specific source repair is permitted in the primary experiment. The
product receives the complete original IJaDataset Java files byte-for-byte;
BCB method intervals are scoring references only. Cutting methods, adding
imports, wrapping fragments in generated classes, or copying them into renamed
inputs is prohibited. Any dependency reconstruction or generated-stub
experiment is a separately named secondary study.

The primary runner must freeze both `skip_dynamic=true` and
`disable_stubs=true`. The strict scorer rejects a run configuration without
either flag and rejects any schema-4 result whose compilation provenance reports
`mode=STUBBED`. A secondary stub-assisted run may use the identical frozen
manifest, but it receives a different `config_id` and is never substituted into
the primary denominator.

The clean-room dataset has two immutable tables. `executions.csv` contains only
unique file pairs, source hashes, and dataset identity, with no ground-truth
labels or ranges. `references.csv` maps one or more official BCB function-pair
references to each execution. This prevents answer leakage and executes a
repeated original file pair only once. A dataset lock hashes the two untouched
official distribution archives, extracted H2 database, compatible H2 jar, both
tables, deterministic query exports, and all referenced source files.

Because many BCB pairs share functions and functionalities, confidence intervals
must not assume that every pair is independent. Use a cluster bootstrap over
functionalities or connected components of the function-pair graph. Report
macro averages across categories in addition to pair-weighted micro averages.

## 6. Mutation and Injection Framework experiment (E2)

Use the official open-source MIF v1.0 release and record the exact release,
configuration, source corpus, operators, seeds, and artifact checksums. MIF is a
supplementary controlled T1--T3 benchmark, not the primary real-world dataset
and not a T4 benchmark.

Target the published Java scale where the official artifacts permit it:

- 250 source fragments;
- 15 default mutation operators;
- 10 injection locations per mutant;
- function-level and block-level experiments;
- approximately 75,000 Java mutant systems in total.

Run two pairwise adaptations:

1. **Isolated pair:** original fragment versus mutant fragment. This measures
   transformation recognition with minimal contextual distraction.
2. **Contextual injection pair:** donor file versus the subject-system file that
   contains the injected clone. This measures localization in unrelated code.

The manifest must retain the official clone intervals and mutation interval.
In addition to c-match, require a `mutation-capture` result whose prediction
overlaps the changed lines. Report per-operator detection recall, mutation-capture
recall, type recovery, localization, compile eligibility, fallback, and runtime.

MIF does not guarantee that all injected systems compile. Compilation eligibility
is an experimental result, not an exclusion silently applied after observing
detector output. Report end-to-end and compile-eligible results separately.

## 7. T4 experiments (E3--E5)

Keep the output evidence tiers separate:

- **Strict T4:** `T4_CONFIRMED` only; symbolic proof succeeded within the
  supported program subset.
- **Observed-behavior evidence:** `T4_DYNAMIC_EVIDENCE`; useful evidence but not
  proof.
- **Candidate only:** `POSSIBLE_T4_CANDIDATE`; reported separately and not
  counted as strict T4 success.

Primary T4 tables must show the full distribution across these tiers, T1--T3,
non-clone, fallback, error, and timeout. A secondary operational sensitivity may
combine strict and dynamic T4, but it must be named explicitly.

SemanticCloneBench provides the principal human-curated semantic-clone evidence.
GPTCloneBench supplies scale and a distinct model-generated-code domain. Results
from these datasets must not be merged without separate tables.

For CodeNet Java250, use the 75,000 deduplicated accepted programs as follows:

- same-problem positive pairs: within each of the 250 problems, form a seeded
  matching so each program is used at most once in this stratum;
- different-problem hard negatives: match programs by token length and basic
  structural complexity while requiring different problem IDs;
- keep problem ID and source program ID in the manifest;
- cluster statistical inference by problem.

Same-problem accepted solutions are a functional-equivalence proxy, not manually
verified region-clone ground truth. CodeNet is therefore used for semantic scale,
specificity, and performance, while the curated datasets carry the stronger T4
validity claim.

Dynamic execution of external corpus code is prohibited in the ordinary batch
JVM. If dynamic evidence is evaluated, each pair must run under an external
supervisor in an isolated sandbox with network disabled, read-only inputs,
temporary disposable storage, process/memory/CPU limits, and a hard timeout.

## 8. Baselines and ablations (E6)

All baselines must receive the same two input files and must be scored with the
same reference intervals. Corpus-search numbers from another paper may be cited
as context but cannot be used as a direct paired statistical comparison.

Minimum internal baselines:

- Winnowing/token fingerprinting;
- legacy AST/APTED pipeline;
- source-only evidence-first pipeline.

Target external baselines, subject to reproducible installation and licensing:

- NiCad for T1--T3;
- SourcererCC for token-based scalable detection;
- one reproducible semantic-code representation baseline for T4, selected and
  frozen before final execution.

Planned ablations:

- A0: full eligible pipeline;
- A1: source-only;
- A2: WALA structural regions with SMT and dynamic disabled;
- A3: WALA plus SMT, dynamic disabled;
- A4: WALA plus SMT plus sandboxed dynamic evidence;
- A5: exact versus approximate candidate prefilter on the performance subset.

Use paired McNemar tests for correctness differences on identical cases, with
Holm correction for multiple baseline comparisons. Report effect sizes and
confidence intervals, not p-values alone.

## 9. Performance and scalability (E7)

Primary performance time is end-to-end wall time from reading the two files to
serializing the complete result. Also record compilation, graph construction,
SMT, dynamic, region growth, classification, and serialization separately.

For every pair record at least:

- source bytes, physical LOC, substance LOC, and tokens on both sides;
- methods, WALA instructions/nodes, graph edges, seed pairs, candidates, and
  emitted regions where available;
- analysis mode and stage outcome;
- wall time, process CPU time, timeout, and error class;
- peak process RSS or an externally measured equivalent;
- JDK, OS/container, CPU model, memory, code revision, configuration ID, and
  dataset artifact ID.

Use two performance protocols:

1. **Latency protocol:** fixed CPU allocation, one measured worker, explicit JVM
   warm-up, repeated stratified cases, p50/p90/p95/p99 and bootstrap intervals.
2. **Throughput protocol:** fixed manifest on 1, 2, 4, 8, 16, and then feasible
   higher worker counts. Report speedup, parallel efficiency, total CPU time,
   memory, and completed pairs per hour.

Model empirical scaling against tokens, methods, candidate pairs, and graph
nodes/edges using log-log plots and robust regression. Do not claim a better
asymptotic complexity merely because more shards or a binary-search-like data
structure is used.

## 10. Required result provenance

Batch schema 4.0 now records the execution mode, side-specific compilation mode,
immutable compilation key/cache hit, generated-stub count, Java release, first
compiler diagnostics, per-stage duration, and normalized fallback stage/reason.
The final paper runner must retain these fields and complete the remaining
resource/graph measurements below:

- `schema_version` and `config_id`;
- `dataset_id`, `artifact_sha256`, and manifest-row identity;
- `code_commit` and dirty-worktree status;
- left and right source SHA-256;
- `compile_left`, `compile_right`, and normalized compiler-error category
  (implemented; preserve in every schema migration);
- explicit analysis mode including standalone, project-context, stub-assisted,
  source fallback, error, and timeout outcomes;
- `fallback_stage` and normalized `fallback_reason` (implemented);
- final status and duration for every pipeline stage (implemented);
- input-size and graph-size fields;
- process CPU, memory, worker, shard, and retry metadata;
- full untruncated predictions and reference intervals.

Retries must retain the original attempt and attempt number. Resume logic must
distinguish a completed success, a completed failure, and a truncated row rather
than treating every observed pair ID as permanently complete.

## 11. Statistical reporting

- Report point estimates with 95% cluster-bootstrap confidence intervals.
- Report per-category macro results before micro-averaged totals.
- Use paired tests for comparisons on the same manifest.
- Publish full confusion matrices, not accuracy alone.
- Report errors, timeouts, and fallback as outcomes; never remove them from the
  primary denominator.
- When showing conditional WALA-only accuracy, always show the WALA eligibility
  rate beside it.
- Preserve raw outputs so every aggregate table can be regenerated.

## 12. Freeze and execution gates

Before the expensive run, freeze in Git or a timestamped release:

1. research questions and primary metrics;
2. dataset versions, licenses, checksums, manifests, and seeds;
3. code revision and all thresholds;
4. runner and result schema;
5. scoring implementation and scorer tests;
6. exclusion, timeout, retry, and failure policies;
7. container image digest and cloud hardware configuration;
8. analysis scripts and planned tables/figures.

The full run must not begin until a plumbing-only preflight passes:

- all manifest paths, checksums, labels, and reference ranges validate;
- every attempted case produces exactly one parseable attempt record;
- backend mode and failure provenance are present on every result;
- coverage/type/localization scorer unit tests pass;
- resume is idempotent and does not hide previous failures;
- a stratified 1% run completes without schema loss and provides a credible time
  and memory estimate.

The preflight is used to inspect infrastructure, not to tune accuracy against the
locked final labels.

## 13. Decisions and remaining open items before preregistration

Resolved decisions:

1. The algorithm and thresholds may change. The July 2026 BCB run is development
   and diagnostic evidence, not a final blind test.
2. One human annotator is available. Annotation will be single-human and
   AI-assisted with blinded human labeling and delayed intra-rater relabeling.
3. T4 uses tiered reporting with strict `T4_CONFIRMED` as the primary result;
   dynamic evidence and possible candidates remain separate.

Remaining open items:

1. The external T4 baseline and its calibration split.
2. Cloud provider, maximum wall-clock window, and whether cost is genuinely
   unconstrained or only secondary to rigor.
