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
- Content-addressed Java 17 compilation/stub cache for bulk pair evaluation.
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
never clone candidates, never edit the input source, and never supply executable
semantics for a strict T4 proof. Batch schema 4.0 records the analysis mode,
cache key/hit, Java release, generated-stub count, first compiler diagnostics,
stage durations, and fallback reason for both inputs.

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
experiment must reject every file that lacks real context.

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
│   ├── src/main/java/          # Winnowing, AST/staged, and source-only region analysis
│   ├── src/semantic/java/      # WALA, SDG, SMT, dynamic, and semantic region pipeline
│   ├── src/test/java/          # Source/AST/region tests
│   ├── src/semantic-test/java/ # WALA/semantic-profile tests
│   ├── web-ui/                 # React, TypeScript, Vite, and Tailwind frontend
│   ├── evaluation/             # Labeled in-house, long-code, and robustness datasets
│   ├── scripts/                # Dataset, experiment, shard, and scoring tools
│   └── results/                # Local experiment summaries and outputs (Git-ignored)
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

### BigCloneBench smoke set

The current local smoke run contains 140 pairs: 20 each for T1, T2, VST3, ST3,
and MT3, plus 40 negative pairs. Scoring uses a BigCloneEval-style 70% region
coverage rule.

| Category | Detection | Correct type / rejection |
| --- | ---: | ---: |
| T1 | 95% | 95% |
| T2 | 100% | 95% |
| VST3 | 100% | 65% |
| ST3 | 100% | 100% |
| MT3 | 15% | 15% |
| Negative | — | 100% rejection |

Only 22 of the 140 pairs completed the full semantic path; 118 used the
source-only fallback. These figures therefore evaluate the end-to-end system,
not WALA alone. The local generated artifacts are under
`code-sim/results/bcb_smoke/`; this results directory is intentionally ignored
by Git.

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

The Maven shade configuration still points its executable JAR manifest at the
legacy `AstMain`. Use the explicit semantic main classes shown above for the
current detector.

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
  evaluated category.
- The report and May 2026 progress slides are not yet synchronized with every
  part of the current semantic implementation.

## Documentation and Data

- [`Evaluation dataset guide`](code-sim/evaluation/README.md)
- [`Robustness experiment guide`](code-sim/evaluation/robustness/README.md)
- [`BigCloneBench Windows runbook`](code-sim/scripts/experiments/RUNBOOK_WINDOWS.md)
- Local M.Eng. report source: `report/` (Git-ignored)

Some older documentation uses `T4_WEAK` and describes a static-only pipeline.
That terminology belongs to the earlier staged detector; the current evidence
model distinguishes SMT-confirmed T4, dynamic evidence, and unresolved possible
T4 results.
