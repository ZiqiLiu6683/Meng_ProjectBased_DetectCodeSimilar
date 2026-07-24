# Algorithm and Threshold Audit

Status: development audit
Audited: 2026-07-21
Scope: the region-based source and WALA pipeline used by `BatchPairMain`

## 1. What the July 2026 benchmark actually ran

`BatchPairMain` constructs `WalaNextPipelineRunner`. For each pair, that runner:

1. reads both source files;
2. tries to compile both files independently;
3. on success, builds WALA representations and three candidate/evidence paths;
4. runs SMT over the cross product of extracted method summaries;
5. optionally runs dynamic checks for SMT-undecided pairs;
6. builds WALA/SDG region groups and projects them back to source statements;
7. classifies the projected source regions;
8. on any caught compile/WALA/runtime failure, reruns the source-only pipeline.

The BigCloneBench command used `-Dcodesim.skipDynamic=true`, so dynamic execution
was disabled. This was the correct security decision for untrusted benchmark
code and did not disable WALA, CFG, SDG, SMT, or source classification.

## 2. Current fixed values

These values were selected in source code; they were not supplied by the user at
benchmark time.

| Concern | Current value | Location or effect | Audit status |
| --- | ---: | --- | --- |
| BigCloneEval c-match | 0.70 | scorer only | External evaluation convention; retain |
| T3 syntactic floor | 0.50 | source discovery and type recognizer | Benchmark-derived; must be isolated from learned parameters |
| VST3/ST3 bands | 0.90 / 0.70 | diagnostic strength | External BCB convention; not a detector threshold |
| WALA block KNN | top 8 | method candidate provider | Requires ablation/calibration |
| Region seed KNN | off by default; k=4 if enabled | SDG seed matcher | Requires measured ablation |
| Minimum grown region | 2 aligned node pairs | region grower | Heuristic; requires calibration |
| Cross-method bridge depth | 6 | region grower | Heuristic; requires robustness test |
| Possible-T4 structural mass | 2.0 | source recognizer | Heuristic; candidate flag only |
| Region suppression overlap | 0.80 | accepted-region selector | Heuristic and asymmetric; redesign |
| CFG block-distance pruning | 0.50 | discovRE-inspired matcher | Paper-derived for another representation; recalibrate for Java/WALA |
| CFG search budget | 16 x max block count | approximate MCS | Heuristic; expose and measure truncation |
| Source statement window | max 6 statements | source-only proposal generation | Heuristic; redesign for long regions |
| Windows per size | max 64 | source-only proposal generation | Heuristic and order-dependent; redesign |
| File full/partial coverage | 0.80 / 0.20 | legacy file summary | Display/summary only; not a region verdict |
| Dynamic samples | 64 | dynamic checker | Secondary evidence; calibrate on dev data |
| Dynamic integer bound | 128 | dynamic checker | Secondary evidence; calibrate on dev data |
| Dynamic max array length | 8 | dynamic checker | Secondary evidence; calibrate on dev data |
| Dynamic invocation timeout | 500 ms | dynamic checker | Operational value; benchmark separately |
| SMT expression depth | 32 | summary extraction | Safety bound; report unsupported outcomes |
| SMT inline depth | 5 | summary extraction | Safety bound; report unsupported outcomes |
| WALA def-use depth | 12 | SDG construction | Safety bound; sensitivity test |

Candidate ranking also uses fixed weights `0.42`, `0.22`, `0.18`, `0.18`, plus
a `0.12` CFG bonus. These weights are not an accept/reject threshold, but they
affect which overlapping region survives the greedy selector and can therefore
change reported accuracy.

## 3. Wiring and validity findings

### 3.1 The aligned-region classifier is not wired into the production runner

`AlignedRegionClassifier` contains a WALA-alignment-specific T1/T2/T3 decision
using aligned fraction, node agreement, and unaligned substantive nodes. No
production call site uses this class.

The production runner instead converts each WALA region group into a set of
source lines with `RegionAlignmentExtractor`, reconstructs JavaParser statements,
and sends the result to `NextRegionTypeRecognizer`. The benchmark therefore did
use WALA for region proposal, but did not use the dedicated aligned-graph type
classifier described by that class's documentation.

This is a software-wiring issue, not a benchmark-sampling issue.

### 3.2 Backend failure is too broad

One catch block covers compilation, WALA hierarchy construction, raw extraction,
CFG analysis, SMT preparation, SDG construction, and region reconstruction. All
of these become the same source-only fallback mode. The current JSON result does
not preserve the exception or stage that caused the fallback.

### 3.3 WALA eligibility and optional context

Real project files often depend on sibling classes or third-party libraries.
Compiling each file alone without a project classpath will fail even when the
source is valid in its original project. This is expected for BCB fragments and
is not caused by the earlier manual sample selection.

The two-file contract now remains the default while each side may optionally
provide a project root or explicit classpath. A Java-17 compilation coordinator
tries real context before diagnostic-driven stubs and stores dependency stubs as
WALA Extension classes rather than clone candidates. Schema 4.0 reports
standalone, project-context, stub-assisted, and fallback execution separately.

This improves eligibility but does not turn generated dependency behaviour into
semantic truth. The primary BCB experiment disables stubs; stub-assisted
eligibility is a separately named secondary study.

### 3.4 Expensive single-file work is repeated for every pair

Java compilation and generated dependency context are now content-addressed and
reused across pairs and workers. Parsing, WALA hierarchy construction, raw
extraction, CFG analysis, method-summary extraction, and SDG construction are
still repeated each time a source appears in a pair. BigCloneBench reuses
functions in many pairs, so a future immutable `SourceArtifact` cache remains a
material performance requirement.

### 3.5 Method-pair work contains Cartesian products

SMT checks every extracted left summary against every extracted right summary.
Block prefiltering and some graph matching similarly compare cross-file
candidates. For files with `m` and `n` methods this introduces `m x n` work
before deeper graph costs. Binary search does not solve this; typed filtering,
hash indexing, caching, and bounded candidate retrieval are the relevant tools.

### 3.6 Greedy suppression can hide type conflicts

The selector keeps candidates in ranking order and suppresses a later region
when its overlap relative to itself reaches 0.80 on both sides. The overlap is
asymmetric, and regions with different type evidence can suppress one another.
This is inconsistent with a product claim about every detected region.

## 4. V3 algorithm target

### 4.1 Separate artifact extraction from pair comparison

Create an immutable `SourceArtifact` keyed by source SHA-256. It contains parsing,
tokens, AST/statement units, method signatures, and, when available, compiled and
WALA-derived features. Benchmark workers extract each unique source once and
reuse it across all pairs.

Report cold-cache latency and cached bulk throughput separately.

### 4.2 Use explicit capability modes

Every pair starts with source artifacts. WALA features augment them when both
sides are eligible:

- `SOURCE_ONLY`;
- `SOURCE_PLUS_WALA`;
- `SOURCE_PLUS_WALA_SMT`;
- `SOURCE_PLUS_WALA_SMT_DYNAMIC`.

WALA ineligibility must not erase successful source results and must not be
called an unqualified system failure. The exact eligibility/failure stage remains
part of the result.

### 4.3 Split proposal, classification, and selection

1. **Proposal:** generate source and optional WALA-aligned region pairs without
   assigning a final clone type.
2. **Classification:** apply mutually auditable T1, T2, T3, strict T4, dynamic,
   and possible-T4 rules to each proposed region.
3. **Selection:** merge true duplicates. Keep conflicting type evidence visible
   instead of allowing a rank weight to silently discard it.

### 4.4 Preserve exact semantic distinctions

- T1: exact after comment/layout normalization.
- T2: exact after identifier/literal normalization with no statement edits.
- T3: statement edits plus calibrated syntactic overlap above a frozen floor.
- `T4_CONFIRMED`: semantic equivalence proved on the supported subset.
- `T4_DYNAMIC_EVIDENCE`: sandboxed observations only.
- `POSSIBLE_T4_CANDIDATE`: triage output, not strict T4 success.

For WALA-aligned regions, retain both source-derived signals and graph-alignment
signals in the result. The development experiment will compare source-only type,
aligned-graph type, and a prespecified fusion rule before one rule is frozen.

### 4.5 Replace opaque ranking with auditable selection

Use reciprocal region overlap/IoU and evidence provenance. Deduplicate within the
same type and evidence tier. If near-identical regions receive different labels,
emit one conflict group containing both decisions for later adjudication rather
than silently choosing through arbitrary weights.

### 4.6 Bound semantic candidate work

Before SMT or dynamic comparison, require compatible observable signatures and
use canonical/coarse semantic-summary keys to retrieve a bounded candidate set.
The filter must be evaluated for candidate recall on development data before it
is allowed in the final configuration. Strict T4 verification remains the final
decision; a prefilter never becomes proof.

## 5. Calibration policy

No threshold is tuned on the final test partitions. Freeze deterministic,
grouped splits before optimization:

- current in-house data and July 2026 BCB outputs: development only;
- MIF: split by donor fragment, never individual injected pair;
- SemanticCloneBench: stable hash/group split;
- GPTCloneBench: final external stress test, not tuning data;
- CodeNet: split by problem ID, not individual program pair.

Minimize the number of free thresholds. Prefer externally defined values or
lexicographic rules where possible. For calibrated parameters, optimize a
preregistered development objective that gives detection, type, localization,
false positives, WALA eligibility, and cost separate weights. Retain the whole
development curve rather than reporting only the winning point.

## 6. Required development comparisons

Before selecting V3 defaults, run small locked development experiments for:

1. projected-source type versus aligned-graph type versus fused type;
2. current greedy suppression versus reciprocal-IoU duplicate grouping;
3. WALA top-k values and exact-versus-approximate candidates;
4. region minimum mass and bridge-depth sensitivity;
5. cached versus uncached extraction;
6. all-method SMT cross product versus typed indexed candidate retrieval;
7. source-only, WALA structural, WALA+SMT, and full sandboxed dynamic modes.

Only after these comparisons and scorer validation is the algorithm/configuration
eligible to be frozen for the expensive final run.
