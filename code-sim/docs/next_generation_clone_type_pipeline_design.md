# Next Generation Clone Type Recognition Pipeline

Date: 2026-06-07

This document records the current design discussion for the next pipeline. The
goal is not only to decide whether two files are clones, but to identify the
clone type of each matched region, preserve the evidence path, and aggregate
the region results into a file-level report.

## Core Idea

The file pair should not receive one broad clone type immediately. A real file
can contain several different relationships at the same time: exact copied
methods, renamed methods, near-miss regions with inserted statements, helper
extraction, unrelated code, and possible semantic similarity.

The new pipeline is therefore segment-first:

```text
Input file pair
  -> Stage 0: Multi-view evidence extraction
  -> Stage 1: Segment construction
  -> Stage 2: Multi-view candidate discovery
  -> Stage 3: Candidate merge and ranking
  -> Stage 4: Region type recognition
  -> Stage 5: File-level aggregation
  -> Stage 6: User report
```

## Design Principles

1. Preserve raw evidence. Normalized, hashed, and vectorized views are
   additional views, not replacements for raw tool output.
2. Do not classify the whole file before finding regions.
3. Candidate discovery is not type judgment. KNN and other filters only propose
   region pairs for later strict approval.
4. Type recognition is strict and ordered for syntactic clones:
   `T1 -> T2 -> T3`.
5. Type-4 is not a leftover category. It requires independent semantic,
   structural, or dynamic positive evidence.
6. Tags must be preserved even when they do not decide the final type. For
   example, a T3 region can still have `renaming_detected`.
7. File-level output is an aggregation of region-level results, not a single
   raw classifier label.

## Stage 0: Multi-view Evidence Extraction

Stage 0 collects all evidence from the existing AST/source pipeline, the
CFG/WALA pipeline, and external tools. It does not decide clone type.

| Evidence item | Source | Meaning | Used by |
| --- | --- | --- | --- |
| `rawSourceText` | Input files | Complete original source text. | Report, provenance, raw comparison |
| `rawTokenSequence` | Lexer/parser | Original tokens with identifiers, literals, keywords, operators, and order preserved. | T1 support, rename detection, diff trace |
| `commentFreePrettyText` | Parser/formatter or NiCad-style pretty printer | Comments removed and formatting normalized, while identifiers and literals remain intact. | T1 exact approval |
| `t1ComparableSequence` | Source pipeline | Sequence used for T1 comparison after comment and whitespace normalization. | T1 exact approval |
| `identifierOccurrences` | AST/source pipeline | Identifier names, scopes, positions, and token indices. | Rename mapping |
| `literalOccurrences` | AST/token pipeline | String, numeric, boolean, and null literals with locations. | Literal-change tags |
| `typeOccurrences` | AST/source pipeline | Type names and declarations. | Type-change tags |
| `renameMap` | Scope-aware token/AST alignment | Mapping such as `count -> total`. | T2 approval, `renaming_detected` tag |
| `renameConflicts` | Rename analysis | Cases where one name maps inconsistently to multiple names. | T2 rejection reason |
| `t2NormalizedSequence` | Identifier/literal/type abstraction or consistent renaming | Sequence after Type-2-style normalization. | T2 exact approval, T3 syntactic similarity |
| `statementList` | AST statement extraction | Statements with kind, text, parent, method, and source range. | T3 edit script |
| `statementEditScript` | Statement-level diff | Matched, inserted, deleted, and modified statements. | T3 approval and report |
| `methodList` | Current AST pipeline | Methods with signatures, bodies, token counts, line ranges. | Segment construction |
| `methodPairCandidates` | Current Stage3/Stage4 pipeline | Existing method-pair candidates and scores. | Candidate discovery/ranking |
| `coverageEvidence` | Current Stage4 pipeline | Matched and unmatched method/file coverage. | File aggregation and scope |
| `oldStage4Prediction` | Current classifier | Current output such as `T2` or `NON_CLONE`. | Debug comparison only |
| `walaRawIR` | WALA backend | Raw IR/instruction output, preserved without semantic rewriting. | Provenance, CFG evidence |
| `cfgBlocks` | WALA CFG | Basic blocks with ids, instructions, and source hints when available. | CFG visualization, T4 evidence |
| `cfgEdges` | WALA CFG | Control-flow predecessor/successor edges, branches, loops. | CFG shape comparison |
| `cfgBlockAlignment` | CFG pipeline | Matched block pairs and distances. | Structural evidence |
| `callGraphEvidence` | WALA call graph | Caller/callee relations and internal helper calls. | Helper extraction and call-chain tags |
| `rawWalaHashVector` | CFG pipeline | Hash-vector view of raw WALA output. | KNN candidate narrowing |
| `numericCfgVector` | discovRE-style feature extraction | Numeric CFG/block/instruction counts. | KNN candidate narrowing |
| `externalNiCadEvidence` | NiCad | Exact, renamed, and near-miss clone evidence with configured thresholds. | T1/T2/T3 baseline evidence |
| `dynamicEvidence` | Generated tests and execution | Inputs, outputs, matched outputs, mismatches, exceptions. | T4 confirmation evidence |

Stage 0 output should be an evidence package. Every derived value should keep
its source and transformation record.

## Stage 1: Segment Construction

Stage 1 builds comparable regions. It must not be method-only, because one side
may extract a method while the other side keeps the logic inline.

Region kinds:

| Region kind | Purpose |
| --- | --- |
| `FILE` | Whole-file context. |
| `CLASS` | Class or interface region. |
| `METHOD` | Full method including signature and body. |
| `METHOD_BODY_REGION` | Method body without signature, useful when matching ordinary code blocks. |
| `BLOCK` | AST block or CFG basic block. |
| `BLOCK_SEQUENCE_REGION` | Consecutive blocks that form a local logical unit. |
| `STATEMENT_WINDOW_REGION` | Consecutive statements, with configurable window sizes. |
| `CONTROL_REGION` | If/loop/try-catch/switch region. |
| `CFG_PATH` | A path through the CFG. |
| `CALL_EXPANDED_REGION` | Caller plus selected internal callee bodies. |
| `INLINE_EXPANDED_REGION` | Caller where helper calls are expanded into callee body summaries. |

Important cross-granularity matches:

```text
METHOD <-> METHOD
METHOD <-> METHOD_BODY_REGION
METHOD <-> BLOCK_SEQUENCE_REGION
METHOD <-> STATEMENT_WINDOW_REGION
METHOD <-> CALL_EXPANDED_REGION
BLOCK_SEQUENCE_REGION <-> CALL_EXPANDED_REGION
CFG_PATH <-> CFG_PATH
```

Each region should preserve source, AST, CFG, and call-context views when
available.

## Stage 2: Multi-view Candidate Discovery

Stage 2 proposes candidate region pairs. It should not create isolated vector
spaces for each region kind, because that would miss cross-granularity matches
such as `METHOD <-> BLOCK_SEQUENCE_REGION`.

The better design is:

```text
all regions
  -> multiple evidence-specific vector views
  -> KNN/search per view
  -> merged candidate pool
```

Vector views:

| View | Contents | Best for |
| --- | --- | --- |
| `rawTokenVector` | Raw token hashes/ngrams. | T1 and raw-copy candidates |
| `normalizedTokenVector` | T2-normalized token hashes/ngrams. | T2/T3 candidates |
| `statementVector` | Statement kinds and normalized statement hashes. | T3 candidates |
| `cfgNumericVector` | CFG numeric features such as block, edge, branch, loop, instruction counts. | Structural candidates |
| `rawWalaHashVector` | Hashes from raw WALA instruction text/classes. | Raw tool-output-preserving CFG candidates |
| `callContextVector` | Caller/callee and helper expansion context. | Internal call/helper candidates |

Candidate discovery channels:

```text
EXACT_TEXT_SCAN
NORMALIZED_AST_SCAN
STATEMENT_DIFF_SCAN
CFG_KNN_SCAN
RAW_WALA_HASH_KNN_SCAN
CALL_EXPANSION_SCAN
NICAD_SCAN
DYNAMIC_SCAN
```

Stage 2 output example:

```json
{
  "candidateId": "C17",
  "leftRegion": "A.countValid",
  "rightRegion": "B.countValid + B.isValid",
  "candidateSources": [
    {"channel": "NORMALIZED_AST_SCAN", "rank": 3},
    {"channel": "CFG_KNN_SCAN", "rank": 1},
    {"channel": "CALL_EXPANSION_SCAN", "rank": 1}
  ]
}
```

## Stage 3: Candidate Merge and Ranking

Stage 3 removes duplicate candidates and ranks them for later analysis and
report display. Ranking is not final clone type judgment.

Merge and ranking signals:

| Signal | Meaning |
| --- | --- |
| Evidence source diversity | Candidate found by multiple independent views. |
| Region coverage | More covered source lines/statements means higher report priority. |
| CFG alignment availability | Candidate has explainable block matches. |
| Statement edit availability | Candidate can explain exact insert/delete/modify operations. |
| Call-expanded evidence | Candidate may explain internal helper extraction. |
| Constructor/trivial penalty | Low-value constructors or trivial methods should not dominate. |
| Containment relation | Keep both large and small candidates when they explain different patterns. |

## Stage 4: Region Type Recognition

Stage 4 applies strict approval to each candidate region pair.

### T1 Exact Clone Approval

Pass condition:

```text
t1ComparableSequence(left) == t1ComparableSequence(right)
```

This means comment and formatting differences are removed, but identifiers,
literals, keywords, operators, and token order are preserved.

Output:

```text
finalRegionType = T1
tags += exact_copy
```

### T2 Rename-only Clone Approval

Pass condition:

```text
T1 failed
AND t2NormalizedSequence(left) == t2NormalizedSequence(right)
AND statementEditScript has inserted = 0, deleted = 0, modified = 0
```

Output:

```text
finalRegionType = T2
tags += renaming_detected / literal_changed / type_changed when present
```

If T2 fails but a rename map exists:

```text
tags += renaming_detected
continue to T3
```

### T3 Near-miss Clone Approval

Pass condition:

```text
T1 failed
AND T2 failed
AND statementEditScript contains inserted OR deleted OR modified statements
AND syntacticSimilarity >= 0.50
```

The similarity band follows the BigCloneBench/BigCloneEval convention:

```text
VST3: 0.90 <= similarity < 1.00
ST3:  0.70 <= similarity < 0.90
MT3:  0.50 <= similarity < 0.70
WT3/T4 boundary: 0.00 <= similarity < 0.50
```

Output:

```text
finalRegionType = T3
strength = VST3 | ST3 | MT3
tags += statement_inserted / statement_deleted / statement_modified
tags += renaming_detected if renameMap exists
```

### T4 Semantic Clone Approval

T4 is independent semantic approval, not the result of all earlier checks
failing. It requires positive structural or behavioral evidence.

Confirmed T4:

```text
T1/T2/T3 are not approved as syntactic clones
AND dynamic behavior evidence passes on generated tests
AND CFG/call evidence supports the relation
```

Possible T4 candidate:

```text
T1/T2/T3 are not approved as syntactic clones
AND CFG/call evidence supports the relation
AND dynamic evidence is insufficient to confirm equivalence
```

No semantic positive evidence:

```text
finalRegionType = NON_CLONE
```

The report must state that dynamic agreement on generated tests is evidence,
not a mathematical proof.

## Stage 5: File-level Aggregation

Stage 5 summarizes region-level results into a file-level relationship. It
does not overwrite individual region decisions.

File-level metrics:

| Metric | Meaning |
| --- | --- |
| `matchedCoverageLeft` | Fraction of left file covered by clone regions. |
| `matchedCoverageRight` | Fraction of right file covered by clone regions. |
| `dominantRegionType` | Highest-coverage/highest-priority region type. |
| `regionTypeDistribution` | Count and coverage of T1/T2/T3/T4/NON_CLONE regions. |
| `mixedTypeSummary` | Whether multiple clone types appear. |
| `helperExtractionSummary` | Whether call-expanded matches were found. |
| `unrelatedCodeRatio` | Code not covered by accepted clone regions. |
| `overallRelationship` | User-facing file-pair relation. |

Suggested file-level relationships:

```text
FULL_FILE_T1
FULL_FILE_T2
FULL_FILE_T3
PARTIAL_T2
PARTIAL_T3
MIXED_CLONE_TYPES
HELPER_EXTRACTION_CLONE
POSSIBLE_SEMANTIC_RELATION
MOSTLY_NON_CLONE
NON_CLONE
```

## Stage 6: User Report

The report should show:

```text
File summary
Region matches
Decision path for each accepted region
Tags
Statement insert/delete/modify details
Rename/literal/type maps
CFG block and edge alignment
Call-expanded relation
Dynamic evidence
Suggested action per region
```

Suggested actions:

| Type | Suggestion |
| --- | --- |
| T1 | Consider extracting shared code or removing duplicate copy. |
| T2 | Review naming/literal/type differences before extraction. |
| T3 | Review changed statements before refactoring. |
| T4 confirmed | Validate business intent, then consider behavior-level refactoring. |
| Possible T4 candidate | Inspect semantic evidence and dynamic mismatches before changing code. |
| Mixed file | Inspect region-level matches instead of relying on one file label. |
| Non-clone | No clone action recommended. |

## References and Design Sources

The design intentionally separates paper-backed thresholds from engineering
choices made for this project.

### Paper-backed or tool-backed sources

- Clone type taxonomy T1/T2/T3/T4: widely used in clone detection surveys and
  evaluation work, including Roy, Cordy, and Koschke's survey tradition.
- NiCad: supports pretty printing, blind and consistent renaming, filtering,
  abstraction, and near-miss thresholds such as 0%, 10%, 20%, and 30%.
  Source: https://www.researchgate.net/publication/221219568_The_NiCad_clone_detector
- BigCloneBench/BigCloneEval: divides Type-3 and Type-4 boundary clones by
  syntactic similarity into VST3, ST3, MT3, and WT3/T4 ranges.
  Source: https://github.com/jeffsvajlenko/BigCloneEval
- BigCloneEval paper: documents the same ST3/MT3/WT3/T4 ranges.
  Source: https://clones.usask.ca/pubfiles/articles/SvajlenkoBigCloneEvalICSME2016.pdf
- SourcererCC: token-based large-scale clone detection for Type-1/2/3
  candidate filtering, with configurable similarity thresholds.
  Source: https://github.com/Mondego/SourcererCC
- Oreo: highlights the "Twilight Zone" between Type-3 and Type-4, supporting
  the decision to treat weak syntactic clones and semantic candidates carefully.
  Source: https://arxiv.org/abs/1806.05837
- WALA: Java/JavaScript static analysis framework for IR, CFG, and call graph
  extraction.
  Source: https://github.com/wala/WALA

### Project design choices

- Segment-first analysis instead of direct file-level type classification.
- Multi-view candidate discovery instead of one merged vector.
- Cross-granularity matching such as `METHOD <-> BLOCK_SEQUENCE_REGION`.
- Region-level type recognition followed by file-level aggregation.
- Tags preserved independently of final type.
- Current CFG KNN used for candidate narrowing, not final type judgment.

## Design Review: Risks and Open Problems

The current design is stronger than the previous method-only classifier, but it
has several risks that must be handled carefully.

1. Region explosion.
   Multi-granularity regions can grow quickly, especially statement windows and
   call-expanded regions. The implementation needs budgets, max expansion
   depth, and duplicate removal.

2. Cross-granularity false positives.
   Allowing `METHOD <-> STATEMENT_WINDOW_REGION` can find useful hidden clones,
   but it can also create many weak local matches. Stage 3 must rank these, and
   Stage 4 must reject them unless strict type evidence passes.

3. KNN score misuse.
   KNN distance is only a candidate-selection signal. It must not become a
   final clone type threshold unless backed by evaluation.

4. T1/T2 exact approval depends on deterministic normalization.
   If pretty printing or tokenization is unstable across languages or parser
   modes, exact approval can become unreliable. The comparable sequence must be
   deterministic and versioned.

5. Statement edit script quality.
   T3 depends on accurate inserted/deleted/modified statement detection. A weak
   diff algorithm could mislabel moved statements or nested control structures.
   The diff output must preserve statement identity, parent context, and source
   ranges.

6. Scope-aware renaming is required.
   A naive token rename map can falsely treat unrelated identifiers as
   consistent renaming. The rename map should use scope/binding information when
   available.

7. BigCloneBench thresholds are region-level syntactic bands.
   The 90/70/50 bands should be applied to comparable clone regions, not to a
   whole file with unrelated code.

8. T4 confirmation is evidence-based, not proof.
   Dynamic tests can support semantic equivalence, but generated tests do not
   prove equivalence. Reports must show input coverage and mismatches.

9. Helper expansion needs strict limits.
   Expanding callers with callees can solve internal function call cases, but
   recursive calls, polymorphism, and library calls can create large or
   ambiguous regions.

10. File-level aggregation can hide important minority regions.
    The report must show both dominant type and all accepted region matches.
    A small T1 security-sensitive clone should not disappear under a broad
    mostly non-clone summary.

11. Language portability is uneven.
    Java has strong WALA support. JavaScript support and future languages may
    need different parsers or backends. The evidence schema should remain
    backend-neutral.

12. External tool integration can be brittle.
    NiCad or other tools should be external evidence providers, not hard
    dependencies that block the whole pipeline when unavailable.

13. Current implementation gaps.
    The current project already has AST/source evidence, method candidates,
    WALA raw output, CFG features, and KNN components. It still needs robust
    segment construction, statement edit scripts, cross-granularity candidate
    merge, and dynamic behavior evidence to fully realize this design.

## Short Implementation Order

1. Implement the Stage 0 evidence schema and serialize it to JSON.
2. Add deterministic T1/T2 comparable sequences.
3. Add statement-level edit scripts for T3.
4. Build Stage 1 regions, starting with method, method body, block sequence,
   statement window, and call-expanded regions.
5. Add Stage 2 multi-view candidate discovery using existing AST and CFG KNN
   outputs.
6. Implement Stage 4 strict region type approval.
7. Implement Stage 5 file aggregation and Stage 6 report output.
8. Add external NiCad evidence as a baseline provider.
9. Add dynamic behavior evidence for semantic confirmation.
