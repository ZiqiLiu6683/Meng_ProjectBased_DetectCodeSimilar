# AST Pipeline vs Evidence-first Pipeline Test Design

## Purpose

This evaluation compares the original AST-based clone classifier with the new evidence-first pipeline. The goal is not only to check whether a file pair receives one final clone type, but also to test whether the system can show affected regions, region-level evidence types, coverage, and limitations.

The old AST pipeline is evaluated as a single-label classifier. The new evidence-first pipeline is evaluated as a region-evidence reporter: it reports dominant region type, affected coverage in each file, relationship shape, selected regions, tags, and whether the case should be reviewed.

## Test Sets

| Test set | Size | Average file length | Purpose |
| --- | ---: | ---: | --- |
| Short benchmark | 55 pairs | small examples | Reproduce the previous AST test and compare behavior category by category. |
| Long-code benchmark | 36 pairs | about 218 lines per file | Respond to professor feedback by testing much longer files with repeated methods, branches, helper extraction, and unrelated long code. |

## Evaluation Categories

| Category | What it tests | Why it matters |
| --- | --- | --- |
| T1 | Same code after formatting/comment changes | Basic exact/near-exact clone handling. |
| T2 | Renamed classes, methods, variables, or literals | Checks whether renaming is handled without confusing structure. |
| T3 | Added/deleted/modified statements | Main weakness of the previous AST result. |
| Internal Function Call | Inline logic moved into a helper method | Directly addresses the professor's question about internal calls. |
| One-to-many / many-to-one | Methods extracted or merged | Tests whether matching can survive method-boundary changes. |
| Call chain | Main logic split into multiple helper calls | Tests deeper internal-call structure. |
| Semantic Clone | Recursion vs iteration, data-structure swap, API-equivalent logic | Current limitation: no dynamic/semantic proof yet. |
| Non-clone | Unrelated programs with generic loops/API overlap | Measures false positives. |

## Metrics

| Pipeline | Main metrics |
| --- | --- |
| AST pipeline | Predicted clone type, type pass/fail, predicted scope, runtime. |
| Evidence-first pipeline | Dominant region type, relationship shape, affected coverage in both files, selected region count, tags, runtime. |

For T4-style rows, the evidence-first pipeline is treated as exploratory instead of strict pass/fail. The current source-only pipeline can expose helper/call-chain evidence, but it does not yet prove semantic Type-4 equivalence.

## Short Benchmark Results: 55 Pairs

| Category | Size | AST result | Evidence-first result | Main observation |
| --- | ---: | --- | --- | --- |
| T1 | 5 | 5/5 | 5/5 | Both pipelines handle near-identical code. |
| T2 | 5 | 5/5 | 4/5 | Evidence-first keeps one case as T1 because the normalized region is fully identical. |
| T3 | 8 | 1/8 | 8/8 | Major improvement: statement edits are now separated from pure renaming. |
| Internal Function Call | 8 | 2/8 strict T4-like | exploratory: 7 T3-like, 1 non-clone | New pipeline exposes edited/partial evidence but still does not prove T4. |
| One-to-many / many-to-one | 8 | 4/8 | exploratory: 8 T3-like | New pipeline shows mixed/partial region evidence across changed method boundaries. |
| Call chain | 5 | 3/5 | exploratory: 4 T3-like, 1 non-clone | Some call-chain evidence is visible, but inter-procedural expansion is still needed. |
| Semantic Clone | 8 | 3/8 | 6 non-clone, 2 T3-like | Both pipelines still need dynamic/semantic checking for true semantic equivalence. |
| Non-clone | 8 | 5/8 | 7/8 | False positives are reduced on short unrelated examples. |

## Long-code Benchmark Results: 36 Pairs

| Category | Size | AST result | Evidence-first result | Main observation |
| --- | ---: | --- | --- | --- |
| Long T1 | 6 | 6/6 | 6/6 | Both pipelines remain stable on longer exact-style clones. |
| Long T2 | 6 | 6/6 | 6/6 | Both pipelines handle long renamed code. |
| Long T3 | 6 | 0/6 | 6/6 | Evidence-first improves strongly on long statement-edit cases. |
| Long Internal Helper | 6 | 0/6 | exploratory: 6 T3-like mixed | Helper extraction is visible as connected edited regions, but not semantic proof. |
| Long Call Chain | 6 | 0/6 | exploratory: 6 T3-like mixed | The pipeline exposes connected region evidence but still needs inter-procedural reasoning. |
| Long Non-clone | 6 | 0/6 | 0/6; all mixed T3-like | Important limitation: long unrelated files with common workflow structure can still produce broad region evidence. |

## Key Findings

1. The evidence-first pipeline clearly improves T3 detection. In the short benchmark, T3 improves from 1/8 to 8/8. In the long benchmark, T3 improves from 0/6 to 6/6.

2. The new pipeline provides more useful output for method-boundary changes. Internal helper, extracted-method, and call-chain cases are not simply hidden as wrong final labels; they appear as region evidence with relationship shape and affected coverage.

3. The new pipeline should not currently be presented as a complete T4 detector. It can show possible T4-related evidence, but semantic equivalence still needs inter-procedural expansion, PDG/data-flow reasoning, dynamic checks, or theorem/dynamic semantic validation.

4. Long non-clone detection is now the main limitation. The long-code benchmark shows that generic business-workflow structure can cause broad mixed T3-like evidence even when the programs are unrelated. This suggests the next improvement should focus on stronger non-clone gating, common-structure filtering, and semantic/domain consistency checks.

## Recommended Comparison Slide

| Claim | Evidence |
| --- | --- |
| New pipeline fixes the previous T3 weakness. | Short T3: AST 1/8, evidence-first 8/8. Long T3: AST 0/6, evidence-first 6/6. |
| New pipeline gives better explainability. | It reports region types, coverage, relationship shape, selected regions, and clickable code/CFG evidence. |
| New pipeline is not yet a semantic T4 solution. | Helper/call-chain rows are exploratory; they produce T3-like/partial/mixed evidence rather than confirmed semantic equivalence. |
| Next technical focus is false-positive control on long code. | Long non-clone rows are currently over-reported as mixed T3-like evidence. |

## Output Files

- `evaluation/results/ast_55_compare_results.csv`
- `evaluation/results/next_55_compare_results.csv`
- `evaluation/results/next_55_compare_summary.md`
- `evaluation/results/ast_long_compare_results.csv`
- `evaluation/results/next_long_compare_results.csv`
- `evaluation/results/next_long_compare_summary.md`
