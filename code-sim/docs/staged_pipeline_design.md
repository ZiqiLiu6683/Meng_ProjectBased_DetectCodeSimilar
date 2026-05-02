# Staged Pipeline Redesign for Code Similarity Detection

Date: 2026-05-02

This document records the v1 redesign of the code similarity detection pipeline. It is based on the April 27 design notes, but updates several parts of the original plan after reviewing the current implementation and discussing practical edge cases.

The main goal is to turn the current collection of similarity scores into a staged detection system:

```text
Stage 0: Input profiling and pipeline routing
Stage 1: Raw signal measurement
Stage 2: Method-pair feature extraction
Stage 3: Method correspondence and file-level diagnostics
Stage 4: Clone type classification
Stage 5: Structured report output
```

## Design Principles

1. Stage 1 only measures raw signals.
2. Stage 2 only describes one method pair.
3. Stage 3 performs cross-method matching and aggregation.
4. Stage 4 performs clone type and scope classification.
5. Stage 5 only reports results; it does not recompute or reclassify.
6. `NOT_APPLICABLE` and missing signals must be represented with explicit status fields, not as ordinary numeric zero.
7. Initial thresholds and weights are heuristic and configurable. They should be calibrated later using evaluation data.

## Stage 0: Input Profiling and Pipeline Routing

Stage 0 does not classify clone type. It builds an input profile and decides which later signals are meaningful.

### Responsibilities

- Validate whether the inputs can be parsed.
- Identify the comparison scenario.
- Set flags that affect later reliability and interpretation.
- Decide enabled, disabled, or weak signals.

### Primary Modes

```text
PARSE_FAILED
EMPTY_OR_TOO_SMALL
UNSUPPORTED_OR_MIXED_LANGUAGE
NO_METHOD_CLASS_CONTEXT
BCB_SINGLE_METHOD_FRAGMENT
SINGLE_METHOD_REAL_FILE
ONE_TO_MANY_METHOD
MULTI_METHOD_BALANCED
MULTI_METHOD_UNBALANCED
```

### Flags

```text
CLASS_CONTEXT_WEAK
CLASS_CONTEXT_STRONG
BOILERPLATE_HEAVY
TRIVIAL_METHOD_HEAVY
S4_COST_RISK
API_DENSE
ASYMMETRIC_FILE_SIZE
ASYMMETRIC_METHOD_PRESENCE
```

### Important Design Decisions

- Do not hard-skip methods with more than 150 AST nodes.
- Use `S4_COST_RISK` only as a performance warning or future guard.
- BCB-style fragments should treat S1 as weak because wrapper class context is often artificial.
- If one file has methods and the other does not, set `ASYMMETRIC_METHOD_PRESENCE`.

### Suggested Output

```text
Stage0Profile {
  primary_mode
  flags
  enabled_signals

  language_A, language_B
  parse_ok_A, parse_ok_B

  method_count_A, method_count_B
  class_count_A, class_count_B
  total_ast_nodes_A, total_ast_nodes_B
  max_method_nodes_A, max_method_nodes_B
  median_method_nodes_A, median_method_nodes_B

  non_method_token_count_A, non_method_token_count_B
  api_call_count_A, api_call_count_B

  routing_notes
}
```

## Stage 1: Raw Signal Measurement

Stage 1 computes raw signals. It does not select candidate pairs, compute best matches, classify clone type, or drop pairs by threshold.

### Key Decision

The first implementation should compute a full method-pair matrix:

```text
for every method A_i:
  for every method B_j:
    compute S2, S3, S4 on the same method pair
```

This avoids the instability of choosing a fixed number of candidate pairs before knowing the structure of the two files.

### File-Level Signals

```text
S1: non-method-body token winnowing
S5: API call vocabulary Jaccard
file_exact_normalized_match: exact signal for T1
```

`S5` must use a status:

```text
S5_status = APPLICABLE / NOT_APPLICABLE
S5_value
```

`file_exact_normalized_match` is used only for T1. It should be based on a normalization that removes comments and whitespace but preserves identifiers, literals, operators, keywords, and token order. It must not use identifier/literal abstraction, otherwise T1 and T2 become indistinguishable.

### Method Metadata

Method identity must support overloaded methods. Do not use method name alone.

```text
MethodInfo {
  method_id
  display_name
  method_name
  signature
  declaring_type
  occurrence_index
  token_count
  tree_size
  fingerprints
  subtree_hashes
}
```

### Pair Matrix

```text
MethodPairRawScore {
  methodA_id
  methodB_id
  sizeA
  sizeB

  S2
  S3
  S4
  S4_status
  ted_distance

  s3_intersection_count
  s3_count_A
  s3_count_B
}
```

`S4_status` should allow future performance guards:

```text
COMPUTED
FAILED
SKIPPED_COST
```

The first implementation can compute all S4 scores and mark them `COMPUTED`.

## Stage 2: Method-Pair Feature Extraction

Stage 2 converts each raw method pair into a feature vector. It does not choose best matches, merge pairs, aggregate file-level results, or classify clone type.

### Input

One `MethodPairRawScore` from Stage 1.

### Derived Features

```text
magnitude
token_structure_divergence
structural_exactness
token_exact_gap
spread
size_ratio
containment_A_in_B
containment_B_in_A
```

### Formulas

```text
magnitude = sqrt(S3^2 + S4^2) / sqrt(2)
```

```text
token_structure_divergence =
  if magnitude < EPS:
    0
  else:
    clamp((S3 - S4) / (magnitude * sqrt(2)), -1, 1)
```

```text
structural_exactness = clamp((S2 + EPS) / (S4 + EPS), 0, 1)
```

```text
token_exact_gap = clamp((S3 - S2) / (S3 + EPS), 0, 1)
```

```text
spread = max(S2, S3, S4) - min(S2, S3, S4)
```

```text
size_ratio = min(sizeA, sizeB) / max(sizeA, sizeB)
```

Token containment:

```text
containment_A_in_B = s3_intersection_count / s3_count_A
containment_B_in_A = s3_intersection_count / s3_count_B
```

If a fingerprint count is zero, set the corresponding containment to `0` and add `LOW_TOKEN_EVIDENCE`. Do not set empty-empty containment to `1`, because that can inflate matching for very small methods.

## Stage 3: Method Correspondence and File-Level Diagnostics

Stage 3 consumes the full method-pair feature matrix. It selects directional best matches, merges them into correspondences, and computes file-level diagnostics.

### Hybrid Match Score

For each method pair:

```text
max_containment = max(containment_A_in_B, containment_B_in_A)
containment_score = max_containment * sqrt(size_ratio)
match_score = max(magnitude, containment_score)
```

Also record:

```text
match_reason = MAGNITUDE_DOMINANT / CONTAINMENT_DOMINANT
```

### Directional Best Matches

```text
A_i -> best B_j by match_score
B_j -> best A_i by match_score
```

Tie-breaker:

```text
1. higher magnitude
2. higher structural_exactness
3. higher size_ratio
4. stable method order
```

### Merged Pairs

Merge forward and backward matches:

```text
BIDIRECTIONAL = confirmed pair
A_TO_B_ONLY = unconfirmed pair
B_TO_A_ONLY = unconfirmed pair
```

Do not force one-to-one matching. One-to-many and many-to-one relations are important evidence for partial clone, containment, and method extraction.

```text
MergedPairFeature {
  methodA_id
  methodB_id
  feature
  match_score
  match_reason
  direction
  confirmed
  general_weight
}
```

### General Weight

```text
reliability_i = min(1.0, min(sizeA, sizeB) / MIN_METHOD_SIZE)
general_weight_i = match_score_i * reliability_i
```

Initial value:

```text
MIN_METHOD_SIZE = 10 AST nodes
```

If total weight is zero, fall back to equal weights and add `LOW_EVIDENCE`.

### Coverage

Coverage is soft and size-weighted. It is not a thresholded count.

```text
coverage_score_A_i = max match_score over merged pairs containing A_i
coverage_score_B_j = max match_score over merged pairs containing B_j
```

```text
coverage_A = sum(sizeA_i * coverage_score_A_i) / sum(sizeA_i)
coverage_B = sum(sizeB_j * coverage_score_B_j) / sum(sizeB_j)
```

Confirmed coverage:

```text
confirmed_coverage_A =
  sum(sizeA_i for A_i in confirmed pairs) / sum(sizeA_i)

confirmed_coverage_B =
  sum(sizeB_j for B_j in confirmed pairs) / sum(sizeB_j)
```

Do not use hard `unmatched_A` or `unmatched_B` as primary diagnostics. Instead, output:

```text
least_covered_A
least_covered_B
```

### Partial Clone Signal

For each merged pair:

```text
partial_A_in_B_i = containment_A_in_B_i * (1 - size_ratio_i)
partial_B_in_A_i = containment_B_in_A_i * (1 - size_ratio_i)
```

Aggregate:

```text
partial_A_in_B =
  sum(partial_A_in_B_i * general_weight_i) / sum(general_weight_i)

partial_B_in_A =
  sum(partial_B_in_A_i * general_weight_i) / sum(general_weight_i)

partial_clone_signal = max(partial_A_in_B, partial_B_in_A)
```

### Stage 3 Output

All file-level diagnostics are based on `merged_pairs`, not the full matrix.

```text
Stage3Result {
  magnitude_avg
  match_score_avg

  centroid_S3
  centroid_S4

  dominant_token_structure_divergence
  spread_avg

  structural_exactness_avg
  token_exact_gap_avg

  variance_S3
  variance_S4

  coverage_A
  coverage_B
  confirmed_coverage_A
  confirmed_coverage_B
  confirmed_ratio

  partial_clone_signal
  partial_A_in_B
  partial_B_in_A

  merged_pairs
  least_covered_A
  least_covered_B

  S1
  S5_status
  S5_value

  flags
}
```

If either side has no methods, Stage 2 and Stage 3 should return `NOT_APPLICABLE` rather than ordinary zero values.

## Stage 4: Clone Type Classification

Stage 4 consumes Stage 0-3 diagnostics and produces clone type, clone scope, confidence, and evidence.

It does not compute new similarity scores.

### Output Taxonomy

```text
clone_type:
  T1
  T2
  T3
  T4_WEAK
  NON_CLONE
  INCONCLUSIVE

scope_type:
  FULL
  PARTIAL
  MIXED
  UNKNOWN

containment_direction:
  A_IN_B
  B_IN_A
  NONE
  UNKNOWN
```

Partial clone is not a clone type. It is represented through `scope_type` and `containment_direction`.

### Component Scores

Do not use one fixed confidence formula as a theoretical truth. First compute interpretable components:

```text
method_similarity_strength =
  avg(match_score_avg, magnitude_avg, centroid_S3, centroid_S4)

coverage_strength =
  avg(coverage_A, coverage_B)

symmetry_strength =
  avg(confirmed_coverage_A, confirmed_coverage_B, confirmed_ratio)

partial_strength =
  avg(partial_clone_signal, max(partial_A_in_B, partial_B_in_A), abs(coverage_A - coverage_B))

api_semantic_strength =
  S5_value if S5_status = APPLICABLE else NOT_APPLICABLE

exact_structure_strength =
  structural_exactness_avg

modification_strength =
  avg(token_exact_gap_avg, spread_avg)
```

Weights should be:

```text
default
configurable
to be calibrated
profile-aware
```

Stage 0 modes and flags can cap or adjust confidence:

```text
BCB_SINGLE_METHOD_FRAGMENT: ignore or weaken S1
ONE_TO_MANY_METHOD: low confirmed_ratio is not strongly negative
BOILERPLATE_HEAVY: cap T2 confidence
TRIVIAL_METHOD_HEAVY: reduce pipeline reliability
S5 NOT_APPLICABLE: T4_WEAK unavailable or low confidence
S4 partial/missing: cap method-structure confidence
```

### Evidence Patterns

T1:

```text
file_exact_normalized_match = true
```

T2:

```text
method_similarity_strength high
exact_structure_strength high
modification_strength low
coverage_strength high
```

T3:

```text
method_similarity_strength medium/high
centroid_S4 medium/high
modification_strength medium/high
exact_structure_strength lower than T2
```

T4_WEAK:

```text
S5_status = APPLICABLE
api_semantic_strength high
method_similarity_strength not high
```

`T4_WEAK` means weak static semantic/API similarity, not proof of true semantic equivalence.

NON_CLONE:

```text
method_similarity_strength low
coverage_strength low
partial_strength low
S1 low or weak
S5 low or NOT_APPLICABLE
```

INCONCLUSIVE:

```text
parse failure
method pipeline not applicable and file-level evidence conflicts
S4 missing too much
high spread/variance with no dominant pattern
boilerplate-heavy inflated evidence
signals mixed with low confidence
```

### Scope Scores

```text
full_scope_score =
  avg(coverage_A, coverage_B, confirmed_coverage_A, confirmed_coverage_B, confirmed_ratio)
  * (1 - partial_clone_signal)
```

```text
partial_scope_score =
  avg(partial_clone_signal, abs(coverage_A - coverage_B), max(partial_A_in_B, partial_B_in_A))
```

Containment direction:

```text
A_IN_B if partial_A_in_B > partial_B_in_A + 0.10
B_IN_A if partial_B_in_A > partial_A_in_B + 0.10
NONE or UNKNOWN otherwise
```

### Confidence

Confidence is not probability.

```text
confidence =
  evidence_strength * evidence_consistency * pipeline_reliability
```

Also output:

```text
scope_confidence
confidence_level: HIGH / MEDIUM / LOW
scope_confidence_level: HIGH / MEDIUM / LOW
```

### Decision Order

```text
1. Applicability gate
2. T1 exact gate
3. Determine scope_type and containment_direction
4. Determine T2/T3 using method evidence
5. Determine T4_WEAK only if T2/T3 evidence is weak
6. Determine NON_CLONE if all clone evidence is weak
7. Otherwise INCONCLUSIVE
```

### Evidence Chain

The evidence chain should be structured, not plain text only.

```text
EvidenceChain {
  supporting_evidence
  opposing_evidence
  scope_evidence
  reliability_warnings
}
```

Each item:

```text
EvidenceItem {
  category
  signal
  value
  interpretation
  supports
  strength
}
```

Keep only the top few evidence items per section.

### Stage 4 Output

```text
Stage4Result {
  clone_type
  scope_type
  containment_direction

  confidence
  confidence_level
  scope_confidence
  scope_confidence_level

  evidence_strength
  evidence_consistency
  pipeline_reliability

  type_scores {
    T1
    T2
    T3
    T4_WEAK
    NON_CLONE
  }

  scope_scores {
    FULL
    PARTIAL
    MIXED
  }

  evidence_chain
  warnings
}
```

## Stage 5: Structured Report Output

Stage 5 formats the result. It does not compute new scores or change classification.

### Output Formats

The design should support:

```text
Human-readable text report
Machine-readable JSON report
```

Future CLI options:

```text
--format text/json/both
--report summary/normal/verbose
```

### Report Structure

1. Header / Input Summary

```text
File A
File B
Stage0 primary_mode
Flags
Enabled signals
```

2. Final Decision

```text
Clone Type
Scope
Containment Direction
Confidence and level
Scope Confidence and level
```

3. Executive Summary

A short natural-language explanation of the final result.

4. Evidence Chain

```text
Supporting Evidence
Opposing Evidence
Scope Evidence
Reliability Warnings
```

5. Method Correspondence Table

Default text output should show `merged_pairs`, not the full matrix.

Columns:

```text
A Method
B Method
Direction
Match Score
Reason
S2
S3
S4
Magnitude
Containment A->B
Containment B->A
Size Ratio
Flags
```

6. Diagnostic Feature Summary

```text
Overall:
  magnitude_avg
  match_score_avg
  centroid_S3
  centroid_S4

Type diagnostics:
  structural_exactness_avg
  token_exact_gap_avg
  spread_avg

Coverage:
  coverage_A
  coverage_B
  confirmed_coverage_A
  confirmed_coverage_B
  confirmed_ratio

Partial:
  partial_clone_signal
  partial_A_in_B
  partial_B_in_A

File-level:
  S1
  S5_status
  S5_value
```

7. Pipeline Details / Debug Section

For verbose mode:

```text
Stage0:
  method counts, AST sizes, flags

Stage1:
  method pair count
  S4 status summary
  file_exact_normalized_match

Stage2:
  feature count
  LOW_TOKEN_EVIDENCE count

Stage3:
  merged pair count
  confirmed/unconfirmed counts
```

### JSON Export Shape

```text
{
  "input": {},
  "stage0": {},
  "stage1_summary": {},
  "stage3": {},
  "stage4": {},
  "method_pairs": []
}
```

### Stage 5 Must Not

```text
recompute scores
change clone_type
hide warnings
treat NOT_APPLICABLE as zero
print the full pair matrix in default text mode
```

## Implementation Notes

Recommended implementation order:

```text
1. Define pipeline data classes.
2. Implement Stage 1 full method-pair matrix.
3. Implement Stage 2 feature extraction.
4. Implement Stage 3 correspondence and diagnostics.
5. Implement minimal Stage 0 profile.
6. Implement Stage 4 heuristic classifier.
7. Replace AstMain printing with Stage 5 report output.
```

Important implementation risks:

```text
T1 requires raw exact normalization that preserves identifiers and literals.
S5 must use status, not a magic numeric sentinel.
S4 should keep per-pair status even if v1 computes everything.
Short methods and empty fingerprints must be marked as low evidence.
Method IDs must handle overloaded methods.
Stage 3 aggregation must use merged pairs, not the full matrix.
T4_WEAK must be described as weak static semantic/API evidence only.
```

