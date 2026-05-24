# Code Similarity Evaluation Dataset

This folder contains a small, labeled evaluation dataset for the staged code
similarity pipeline. The goal is to test both final clone type classification
and specific code transformation patterns.

## Goals

- Keep standard clone labels: `T1`, `T2`, `T3`, `T4_WEAK`, `NON_CLONE`,
  `INCONCLUSIVE`.
- Add transformation labels, especially for internal function calls:
  `INLINE_TO_HELPER`, `HELPER_TO_INLINE`, `EXTRACT_METHOD`, `MERGE_METHODS`,
  and `CALL_CHAIN_REFACTOR`.
- Record known limitations explicitly, instead of hiding weak or ambiguous
  cases.
- Support later batch evaluation with accuracy, scope correctness, runtime,
  method counts, and pair matrix size.

## Metadata

`pairs.csv` is the source of truth. Each row describes one comparison pair.

- `pair_id`: unique comparison id.
- `language`: source language. The first version uses `JAVA`.
- `file_a`, `file_b`: paths relative to `code-sim/evaluation`.
- `expected_type`: expected clone type.
- `expected_scope`: expected scope type.
- `transformation_tag`: main transformation being tested.
- `difficulty`: `L1` easy through `L4` difficult.
- `expected_behavior`: short natural-language expectation.
- `limitation_tag`: known limitation touched by this pair.
- `notes`: extra context for reports.

## Clone Types

- `T1`: near-identical code after ignoring comments and whitespace.
- `T2`: same structure with renamed identifiers or literals.
- `T3`: similar code with inserted, deleted, reordered, or modified statements.
- `T4_WEAK`: weak semantic/API similarity evidence. This is not proof of full
  semantic equivalence.
- `NON_CLONE`: insufficient evidence for a clone relation.
- `INCONCLUSIVE`: evidence is too weak, mixed, or unavailable.

## Scope Types

- `FULL`: most method mass has a meaningful counterpart.
- `PARTIAL`: only part of one file is similar to the other.
- `MIXED`: strong and weak evidence coexist across methods.
- `UNKNOWN`: scope cannot be reliably determined.

## Transformation Tags

- `RENAMING`: variables, methods, or classes are renamed.
- `STATEMENT_INSERT_DELETE`: statements are inserted or deleted.
- `INLINE_TO_HELPER`: inline logic is moved into an internal helper method.
- `HELPER_TO_INLINE`: helper logic is expanded back into the caller.
- `EXTRACT_METHOD`: one method is split into multiple methods.
- `MERGE_METHODS`: multiple helpers are merged into one larger method.
- `CALL_CHAIN_REFACTOR`: one method becomes a chain of internal calls.
- `RECURSION_VS_ITERATION`: recursive and iterative implementations are compared.
- `API_EQUIVALENT_USAGE`: different APIs are used for a similar task.
- `DATA_STRUCTURE_SWAP`: the main data structure changes.
- `CONTROL_FLOW_REORDER`: equivalent or near-equivalent control flow is rewritten.
- `UNRELATED`: unrelated programs.
- `SIZE_STRESS`: larger inputs for runtime and stability testing.

## Limitation Tags

- `JAVA_ONLY_CURRENTLY`: only Java is wired into the current pipeline.
- `CALL_GRAPH_NOT_EXPANDED`: internal helper calls are not explicitly expanded
  into the caller representation yet.
- `T4_WEAK_NOT_SEMANTIC_PROOF`: static weak Type-4 evidence does not prove
  semantic equivalence.
- `THRESHOLDS_HEURISTIC`: Stage 4 thresholds are heuristic and not ML-calibrated.
- `S4_APTED_COST_GROWS_WITH_METHOD_PAIRS`: tree edit distance cost grows with
  method-pair count.
- `NO_DYNAMIC_EXECUTION_CHECK`: no runtime input/output behavior check yet.
- `NO_ML_CALIBRATION_YET`: no learned calibration model yet.
- `WEB_UI_INFORMATION_DENSE`: UI information hierarchy still needs improvement.

## Current Version

The current dataset version contains 55 Java file pairs across eight evaluation
projects:

- `T1`: 5 pairs
- `T2`: 5 pairs
- `T3`: 8 pairs
- `InternalHelper`: 8 pairs
- `MethodRefactor`: 8 pairs
- `CallChain`: 5 pairs
- `SemanticDifficult`: 8 pairs
- `NonClone`: 8 pairs

It intentionally includes difficult pairs so the report can show both
successful behavior and current limitations.
