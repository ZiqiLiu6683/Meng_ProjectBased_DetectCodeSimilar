# Evidence-First Region-Based Code Clone Detector

This repository contains a research prototype for detecting and explaining code
clones in Java programs. The current system reports **matched source regions**
rather than assigning one clone type to an entire file pair. It combines source
analysis, WALA bytecode/graph analysis, SMT-based equivalence checks, and bounded
dynamic evidence.

The project started as a token fingerprinting and Winnowing detector. Those
implementations remain in the repository for comparison, but the semantic region
pipeline is the current main path.

## Highlights

- Region-level T1, T2, and T3 clone evidence with source-line traceability.
- WALA IR, CFG, call-graph, and system-dependence-graph analysis.
- Cross-method region discovery through call and dependence edges.
- Name-independent node descriptors and Weisfeiler-Lehman-style semantic hashes.
- SMT-backed `T4_CONFIRMED` results for supported integer method summaries.
- Bounded dynamic input/output checks reported as evidence, not proof.
- Optional project/classpath context plus conservative dependency stubs for files
  that are valid only inside a larger build.
- Content-addressed Java 17 compilation/stub/failure cache for bulk pair evaluation.
- Source-only fallback when compilation or WALA analysis is unavailable.
- React/Vite web interface with streamed, real pipeline progress.
- Evaluation tooling for the in-house dataset, long-code cases, robustness
  experiments, and BigCloneBench/BigCloneEval-style scoring.

## Current Pipeline

The main semantic pipeline is implemented by
[`WalaNextPipelineRunner`](code-sim/src/semantic/java/com/ziqi/codesim/next/semantic/WalaNextPipelineRunner.java).

1. Compile both Java inputs under an exact Java 17 contract. Resolution uses
   real project/classpath artifacts first, then conservative generated dependency
   context when enabled; immutable artifacts are reused by source hash.
2. Build WALA class hierarchies, IR, CFGs, call graphs, and SDGs.
3. Convert WALA statements and dependence edges into internal semantic graphs.
4. Build structural descriptors and match high-confidence seed nodes.
5. Grow seeds into aligned regions, including regions that cross method calls.
6. Classify source-level T1, T2, and T3 evidence.
7. Try SMT equivalence on supported method summaries.
8. For unresolved method pairs, optionally collect bounded dynamic evidence.
9. Remove substantially redundant regions and return traceable decisions.

If compilation or WALA fails, the runner falls back to the source-only evidence
pipeline. Reports and the web API expose whether the semantic region backend or
the fallback path produced the result.

Generated stubs are compiled into a separate WALA `Extension` context. They are
never clone candidates and never edit the input source. Stub-assisted executions
may use WALA for T1--T3 region analysis, but SMT and dynamic T4 evidence are
disabled because generated dependencies are not semantic truth. Batch schema 4.0 records the analysis mode,
cache key/hit, Java release, generated-stub count, first compiler diagnostics,
stage durations, and fallback reason for both inputs.
Successful bytecode, generated dependency context, and deterministic compiler
failures are reused; they are rebuilt only when source, classpath fingerprints,
Java release, or generator version changes.
Stub generator version 3 additionally models missing wildcard-import packages,
type-qualified static fields, and directly observed members inherited from a
missing superclass or interface. Unsupported Java syntax and unresolved type
semantics still fail closed to the source fallback.

## Result Labels

| Label | Meaning |
| --- | --- |
| `T1` | The compared region is identical after removing layout and comments. |
| `T2` | The region matches after identifier/literal normalization without statement edits. |
| `T3` | The regions retain substantial syntactic structure but contain statement-level edits. |
| `T4_CONFIRMED` | The supported symbolic summaries were proven equivalent by SMT. |
| `T4_DYNAMIC_EVIDENCE` | Deterministic sampled executions produced equal observed outputs; this is not a proof. |
| `POSSIBLE_T4` | Structural or cross-method evidence suggests semantic similarity, but verification is unresolved. |
| `NON_CLONE` | The available evidence is insufficient for a clone decision. |
| `INCONCLUSIVE` | Analysis could not produce a reliable decision. |

CFG similarity is supporting evidence only; it does not independently promote a
region to a clone type. The modern web interface deliberately avoids a single
file-level clone label because different parts of a file pair can have different
relationships.

## Output Granularity

The pipeline emits clone types at **two** scales, and they do not mean the same
thing. Anything that counts clones must pick one.

| Scale | Field | What carries the type | Contiguous? |
| --- | --- | --- | --- |
| Region | `RegionDecision.type` | one grown region of aligned statements | no — see `segments` |
| **Sub-region** | `SubRegion.type` | one run of aligned **statement pairs** inside a region | no — runs on each side |

A region carries exactly **one** type, produced by the strict T1→T2→T3→T4
cascade. Sub-regions apply the same cascade per aligned statement pair and
coalesce the results into uniformly-typed runs, so one region may expose several.

**The sub-region scale is the one to quote for type accuracy, and it is measured.**
When a single file pair contains two edits of different kinds — the normal case
in real plagiarism — region-level typing measures **57.7 %** correct while
sub-region typing measures **89.8 %** (615 pairs, see the evaluation snapshot).
The reason is structural rather than a tuning problem: one region holds one type,
so a T2 edit and a T3 edit falling inside the same grown region can only be
reported as one of them.

Note the two scales currently disagree about which is authoritative:

- the **web interface** already colours each line by its **sub-region** type,
  falling back to the region type only where no breakdown exists;
- `FileLevelAggregator` still aggregates `regionTypeCounts`, per-type coverage and
  `dominantType` from the **region** type.

So what a user sees is sub-region granularity while the summary counts are region
granularity. Reconciling the two changes detector output and would need its own
measurement; it has deliberately not been done inside the frozen evaluation.

## Requirements

- JDK 17 exactly (the Maven build rejects other major versions)
- Maven
- Node.js and npm for the modern web interface
- Python 3 for evaluation scripts

The semantic Maven profile uses WALA 1.7.1, JavaSMT 6.0.0, and SMTInterpol.
JavaParser and APTED support the source/AST analysis layers.

## Build and Test

Run Maven from the Java project directory:

```bash
cd code-sim
mvn -Psemantic-analysis clean test
```

The `semantic-analysis` profile adds `src/semantic/java`,
`src/semantic-test/java`, and their WALA/SMT dependencies. Running Maven without
this profile only builds the source/AST portion of the project.

Generated reports under `code-sim/target` are build artifacts and may be older
than later source edits, so rerun the command above before relying on them for a
release.

## Run the Command-Line Detector

From `code-sim`:

```bash
mvn -Psemantic-analysis \
  -Dexec.mainClass=com.ziqi.codesim.next.semantic.WalaNextPipelineMain \
  -Dexec.args="path/to/Left.java path/to/Right.java --report breakdown --view both" \
  exec:java
```

Available output options include:

- `--json` or `--report json` for machine-readable output.
- `--report breakdown` for the evidence breakdown.
- `--view method|block|both` to select breakdown views.
- `--show-all` to include decisions normally hidden from the concise report.

The two-file interface remains the default. A file that depends on an already
built Maven/Gradle project can opt into context without changing the comparison
unit:

```bash
mvn -Psemantic-analysis \
  -Dexec.mainClass=com.ziqi.codesim.next.semantic.WalaNextPipelineMain \
  -Dexec.args="Left.java Right.java \
    --left-project-root /work/left-project \
    --right-project-root /work/right-project \
    --left-classpath /extra/left.jar \
    --right-classpath /extra/right.jar" \
  exec:java
```

Project roots are searched for common compiled-output and dependency directories
(`target/classes`, Gradle `build/classes`, `lib`, `libs`, and related paths).
CodeSim does not execute an arbitrary project's build scripts. Build the project
first or pass an explicit platform-separated classpath. Use `--no-stubs` when an
ablation must reject every file that lacks real context. The default two-file
product mode enables conservative Stub assistance.

For repository-to-repository discovery, the project scanner indexes normalized
method structure, retrieves bounded Top-K file-pair candidates, and sends only
those candidates through the same pairwise detector:

```bash
mvn -Psemantic-analysis \
  -Dexec.mainClass=com.ziqi.codesim.next.semantic.project.ProjectScanMain \
  -Dexec.args="/work/project-a /work/project-b /work/project-scan.jsonl \
    --method-top-k 8 --file-pair-limit 100" \
  exec:java
```

This scanner is a product mode, not yet a paper-level directory-recall claim.
Its retrieval recall and independent precision must be evaluated separately.
Dynamic execution is off by default for project scans and requires the explicit
`--enable-dynamic` flag.

## Run the Web Interface

Build the frontend first:

```bash
cd code-sim/web-ui
npm ci
npm run build
cd ..
```

Start the Java backend from `code-sim`:

```bash
mvn -Psemantic-analysis \
  -Dexec.mainClass=com.ziqi.codesim.next.semantic.web.RegionWebServer \
  exec:java
```

Open <http://localhost:8080>. The server exposes `POST /api/analyze`, streams
pipeline stages using Server-Sent Events, and serves the production frontend
from `web-ui/dist`.

For frontend development, keep the Java server on port 8080 and run:

```bash
cd code-sim/web-ui
npm run dev
```

Vite serves the UI at <http://localhost:5173> and proxies `/api` to the Java
backend.

## Security Note

The dynamic evidence tier compiles and executes the compared Java code with
bounded, deterministic test inputs. Do not enable this tier for untrusted input.
Disable it with:

```bash
-Dcodesim.skipDynamic=true
```

The BigCloneBench experiment runner uses this property because benchmark source
code must be treated as untrusted. The dynamic tier currently uses 64 generated
samples, supports a limited set of Java value types, and applies per-invocation
timeouts. Matching samples are evidence only and do not establish general
semantic equivalence.

## Project Layout

```text
.
├── code-sim/
│   ├── src/main/java/          # Source/AST evidence and the region type recognizer
│   ├── src/semantic/java/      # WALA, SDG, SMT, dynamic, and semantic region pipeline
│   ├── src/test/java/          # Source/AST/region tests
│   ├── src/semantic-test/java/ # WALA/semantic-profile tests
│   ├── src/legacy/java/        # Superseded generations, not built by default (-Plegacy)
│   ├── src/legacy-semantic/java/    # Superseded WALA-dependent code (-Plegacy)
│   ├── src/legacy-test/java/        # Tests for the above (-Plegacy)
│   ├── src/legacy-semantic-test/java/
│   ├── web-ui/                 # React, TypeScript, Vite, and Tailwind frontend
│   ├── evaluation/             # Labeled in-house, long-code, and robustness datasets
│   ├── scripts/                # Dataset, experiment, shard, and scoring tools
│   └── results/                # Experiment summaries and outputs
├── code-sim-c/                 # Early C prototype
├── report/                     # LaTeX M.Eng. report source
└── outputs/                    # Generated presentation artifacts
```

The large legacy [`WebAppMain`](code-sim/src/main/java/com/ziqi/codesim/web/WebAppMain.java)
contains an older embedded interface. New UI work should target
[`RegionWebServer`](code-sim/src/semantic/java/com/ziqi/codesim/next/semantic/web/RegionWebServer.java)
and `code-sim/web-ui`.

The C implementation is an early placeholder and currently returns a constant
similarity score. It is not equivalent to the Java detector.

## Evaluation Snapshot

### Formal region-corpus run (`code-sim/results/formal-v1/`) — the current headline results

6,615 pairs over three strata, built by known mutation injection into IBM Project
CodeNet Java250 so that ground truth is exact to the line. **Every pair ran the
compiled Phase A/B path**: `status=ok`, `analysisMode=SOURCE_PLUS_WALA_SMT`,
**zero fallback**, `stubs=0`.

**Sub-region type accuracy, 5,000 single-edit pairs** (Wilson intervals on the
design-effect-corrected sample, clustered by CodeNet problem):

| Operator | N | Type correct | 95 % CI | Median IoU |
| --- | ---: | ---: | --- | ---: |
| `t2_change_string_literal` | 499 | 99.8 % | [98.7, 100] | 1.000 |
| `t3_wrap_statement` | 501 | 99.6 % | [98.4, 99.9] | 1.000 |
| `t2_rename_local` | 948 | 99.1 % | [98.1, 99.5] | 1.000 |
| `t1_reindent` | 499 | 99.0 % | [97.5, 99.6] | 0.357 |
| `t2_change_int_literal` | 501 | 98.4 % | [96.7, 99.2] | 1.000 |
| `t1_add_eol_comment` | 503 | 78.9 % | [74.8, 82.5] | 0.062 |
| `t1_add_block_comment` | 500 | 24.4 % | [20.5, 28.7] | 0.056 |
| `t1_add_blank_line` | 498 | 24.1 % | [20.2, 28.4] | 0.059 |

An IoU of 1.000 means the mutated line was identified **exactly**, not merely
overlapped. Unchanged code is recognised as T1 in 98.1 % of 10,026 references.

**`t1_add_blank_line` and `t1_add_block_comment` are untestable at this scale and
their percentages are not detection rates.** Sub-regions are built from statement
extents, and a blank or comment-only line belongs to no statement, so no
sub-region can correspond to one. Of 798 such references, 195 matched and
**195/195 were incidental** coverage by a sub-region spanning three lines or more.
Report them beside `mARI` and `mSIL` as untestable, not as a weakness.

**Statement insert/delete are reported as two arms** because the operator itself
was redefined mid-run; the corpus, not the detector, produced the earlier errors:

| Operator | Arm | N | Type correct |
| --- | --- | ---: | ---: |
| `t3_insert_statement` | original | 392 | 88.8 % |
| `t3_insert_statement` | **corrected** | 96 | **100.0 %** |
| `t3_delete_statement` | original | 399 | 89.0 % |
| `t3_delete_statement` | corrected | 100 | 85.0 % |

Insert confirms the corpus-artifact explanation with non-overlapping intervals: a
plain `int x = 5;` normalises to what every int declaration normalises to, so the
statement-level LCS paired it with an existing declaration and reported a rename —
correctly.

Delete's fix did **not** take, and **the delete figure should be read stratified
rather than as a single number**. Splitting all five batches by whether the
deleted statement had a Type-2-normalised twin in its file:

| Deleted statement | N | Type correct | 95 % CI |
| --- | ---: | ---: | --- |
| **no normalised twin** | **327** | **99.4 %** | [97.8, 99.8] |
| has a normalised twin | 158 | 65.8 % | — |

The old arm's no-twin share is 67 % and the corrected arm's is 68 %, so the
"prefer a unique-shaped statement" change moves the mix hardly at all — that,
rather than an occasional fallback, is why neither arm's headline number moved.
Deleting one of two statements that normalise identically leaves a file whose
history the ground truth cannot uniquely recover, so the twinned stratum is a
limit of the corpus, not a detector error. **Quote 99.4 % with the twinned
stratum and its share reported beside it.**

**Specificity, 1,000 hard negatives** (different problems, matched on token length
and structural complexity, sharing under 30 consecutive tokens). Protocol §4.1
requires both false-positive definitions to be reported separately:

| Definition | FP | Specificity |
| --- | ---: | ---: |
| reference-range (covers ≥ 70 % of both files) | 2 / 1000 | **99.8 %** |
| strict product (any region ≥ 6 lines) | 180 / 1000 | 82.0 % |
| — of which claim a syntactic type T1/T2/T3 | 30 / 1000 | 97.0 % |

With the positives: sensitivity 100 %, balanced accuracy 91 %, **MCC 0.890**.
Precision is tabulated only at stated clone prevalences, never from the corpus
mixture.

**The 18 % strict figure is an upper bound.** At least 20 of those 180 emit a
region holding the same competitive-programming fast-I/O template on both sides —
real code two authors copied from a shared source. One case is an SMT-proved
match between two Fisher–Yates shuffle methods differing only in variable names.
That case also proves a corpus limitation: the builder rejects pairs sharing ≥ 30
consecutive tokens and this pair shares 15, so **no token-identity filter can
produce a clone-free negative stratum** — renaming breaks token identity while
leaving the clone intact.

**Two edits per pair, 615 pairs** — the measurement that motivates the sub-region
scale:

| Two edits in one pair | Region level | Sub-region level |
| --- | ---: | ---: |
| different clone types (398 pairs) | **57.7 %** | **89.8 %** |
| same clone type (217 pairs) | 91.3 % | 91.0 % |

All 219 region-level type errors come from mixed-type pairs; none from same-type
pairs, with error shapes exactly as a one-type-per-region model predicts
(`T2 → T3` 86, `T1 → T2` 72, `T1 → T3` 58 — always the later cascade type taking
the whole region).

Full method, every decision with its evidence, all four ground-truth correction
rounds including the wrong attempts, and every retracted conclusion:
[`完整实验报告.md`](code-sim/results/formal-v1/完整实验报告.md). Frozen configuration
in [`FREEZE.md`](code-sim/results/formal-v1/FREEZE.md).

### BigCloneBench smoke set — superseded, kept for the record

The 140-pair smoke run below **did not test the semantic pipeline** and its
numbers must not be quoted. Only 22 of 140 pairs completed the full semantic
path; 118 used the source-only fallback, and the scorer had no notion of fallback
mode, so the table describes the fallback path far more than the compiled one.
This is the specific failure the formal run above was built to avoid, which is
why every one of its 6,615 pairs records `analysisMode`.

| Category | Detection | Correct type / rejection |
| --- | ---: | ---: |
| T1 | 95% | 95% |
| T2 | 100% | 95% |
| VST3 | 100% | 65% |
| ST3 | 100% | 100% |
| MT3 | 15% | 15% |
| Negative | — | 100% rejection |

**Treat the fallback rate as a property of this dataset, not of the detector.**
These pairs are synthetic wrappers: each BigCloneBench fragment was pasted into
a generated `public class LeftInput { ... }`, so a fragment that referenced a
field of its original class cannot compile, and the diagnostic-driven stub
generator cannot repair it (it supplies missing *external* dependencies, and is
forbidden from editing the input source). A re-measurement in July 2026 with
stub assistance enabled still reached only 26 of 140, with the dominant
compiler error being `cannot find symbol: variable X location: class LeftInput`.
The clean-room pipeline (`scripts/experiments/bcb_cleanroom.py`) exists to
remove exactly this artifact by feeding the complete original IJaDataset files
byte-for-byte; WALA eligibility must be re-measured there before any figure is
quoted.

### In-house 55-pair set

The current local full semantic run classified 36 of 47 determinate expectations
correctly (76.60%); eight inconclusive expectations are excluded from that
denominator. T1, T2, and T3 cases are substantially stronger than weak semantic
T4 cases, and the run includes one dynamic-evidence false positive. Its generated
summary is under `code-sim/results/experiments/inhouse55/` and is not versioned.

These are research results, not production guarantees. Thresholds and dataset
composition should be reported alongside any quoted accuracy.

## Historical Implementations

The repository retains earlier stages for reproducibility:

- Tokenization, 64-bit rolling hashes, Winnowing, Jaccard, and containment.
- JavaParser/APTED file and method analysis.
- A staged heuristic classifier with file-level T1/T2/T3/T4-weak decisions.
- discovRE-inspired basic-block distance, KNN candidate indexing, and approximate
  maximum-common-subgraph matching.

These generations are not compiled by the default build. They live unchanged
under `src/legacy/java` and `src/legacy-semantic/java` (git history preserved
across the move) and are built and tested with the `legacy` profile:

```bash
cd code-sim
mvn -Plegacy,semantic-analysis clean test
```

Nothing on the current Phase A/B path references them, so excluding them changes
no detector behaviour; it only keeps the default build, the IDE index, and the
shaded jar free of superseded code. The shaded jar manifest now names
`com.ziqi.codesim.next.NextPipelineMain` (the source-only region pipeline);
use the explicit semantic main classes shown above for the current detector.

## Current Limitations

- The current end-to-end semantic pipeline targets Java.
- WALA still requires bytecode, but the compiler coordinator can now use real
  project/classpath context or conservative dependency stubs before falling back.
- Project context must already be built; CodeSim does not run untrusted Maven or
  Gradle build logic automatically.
- Generated stubs improve WALA eligibility but cannot reproduce dependency
  behaviour, so dependent calls remain unknown for strict semantic proof.
- The repository scanner uses bounded approximate retrieval; exhaustive
  directory-level recall is not implied.
- SMT verification covers a restricted subset of integer arithmetic and returns
  unknown/unsupported for loops, nonlinear expressions, and other unsupported
  constructs.
- Dynamic sampling cannot prove equivalence and must not run untrusted code.
- Region growth and classification thresholds are research heuristics rather
  than a learned calibration model.
- BigCloneBench moderately and strongly modified T3 cases remain the weakest
  evaluated category — but see the evaluation snapshot: that measurement ran
  overwhelmingly on the source-only fallback and does not describe the semantic
  pipeline.
- **The two output scales disagree about which is authoritative.** The web
  interface colours by sub-region while `FileLevelAggregator` counts by region.
  See *Output Granularity*.
- **Region suppression compares bounding boxes.** `AcceptedRegionSelector`
  computes containment from `beginLine`/`endLine`, 58 % of regions consist of more
  than one run, and 58.9 % of accepted regions are suppressed — so some
  suppressions rest on an overlap that does not exist in the content. Left
  unchanged during the frozen evaluation because fixing it changes detector
  behaviour.
- **`NON_CLONE` cannot mean "original work".** It is the cascade's fall-through
  for a candidate Phase A already proposed; code with no counterpart never becomes
  a candidate. There is no output meaning "this run of statements is new", so the
  distinction a reviewer most wants — edited versus newly written — is not
  expressible today.
- **`NON_CLONE` decisions are invisible** unless `-Dcodesim.emitRejectedRegions=true`
  is set, so the number of rejected candidates is not recorded by default.
- Blank-line and comment-only insertions cannot be attributed to a sub-region at
  all, because such a line belongs to no statement. This is a scale limit, not a
  detection failure; see the evaluation snapshot.
- The report and May 2026 progress slides are not yet synchronized with every
  part of the current semantic implementation.

## Documentation and Data

- [`Full experiment report`](code-sim/results/formal-v1/完整实验报告.md) — the formal run end to
  end: design and why, frozen configuration, every fault hit, all four ground-truth correction
  rounds with the wrong attempts kept in, results for all three strata, limitations, and retracted
  conclusions. Written long on purpose so a figure can be re-derived without re-running anything.
- [`Frozen configuration`](code-sim/results/formal-v1/FREEZE.md) — code revision, corpus hash,
  thresholds, runner settings, scorer hashes, and six amendments.
- [`Decision and evidence log`](code-sim/docs/decision-log.md) — what was decided, what was
  measured, and which earlier conclusions were retracted once data arrived. Read this first when
  asking why something is the way it is.
- [`Evaluation dataset guide`](code-sim/evaluation/README.md)
- [`Paper evaluation protocol`](code-sim/evaluation/paper_evaluation_protocol.md)
- [`Algorithm and threshold audit`](code-sim/evaluation/algorithm_threshold_audit.md)
- [`Robustness experiment guide`](code-sim/evaluation/robustness/README.md)
- [`BigCloneBench Windows runbook`](code-sim/scripts/experiments/RUNBOOK_WINDOWS.md)
- Local M.Eng. report source: `report/` (Git-ignored)

Some older documentation uses `T4_WEAK` and describes a static-only pipeline.
That terminology belongs to the earlier staged detector; the current evidence
model distinguishes SMT-confirmed T4, dynamic evidence, and unresolved possible
T4 results.
