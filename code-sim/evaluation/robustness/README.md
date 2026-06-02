# WALA Raw Output Robustness Dataset

Purpose:

```text
same Java source method
  -> different bytecode/build settings
  -> WALA raw snapshot
  -> raw-channel stability analysis
```

This dataset is for discovRE-style robustness feature selection. It is not a
clone benchmark. Source logic should remain unchanged across build variants.

Initial build variants:

```text
javac -g
javac -g:none
javac --release 8
javac --release 11
javac --release 17
```

Build them with:

```text
sh scripts/build_robustness_variants.sh
```

Export one variant's raw WALA snapshot with:

```text
mvn -Psemantic-analysis exec:java \
  -Dexec.mainClass=com.ziqi.codesim.semantic.backend.wala.raw.WalaRawSnapshotMain \
  -Dexec.args="target/robustness/classes/javac-g target/robustness/snapshots/javac-g.jsonl"
```

Export all built variants with:

```text
sh scripts/export_robustness_snapshots.sh
```

Analyze raw-channel stability with:

```text
python3 scripts/analyze_wala_raw_channel_stability.py \
  target/robustness/stability/channel_stability.csv \
  target/robustness/snapshots/*.jsonl
```

## Medium Dataset Workflow

The medium workflow uses open-source Java projects as same-source robustness
inputs. The current project list is stored in:

```text
evaluation/robustness/medium_projects.csv
```

Fetch project sources:

```text
sh scripts/fetch_medium_robustness_projects.sh
```

Compile direct `javac` variants:

```text
python3 scripts/build_medium_robustness_variants.py
```

The build report is written to:

```text
target/robustness/medium/build_report.csv
```

Export raw WALA snapshots for successful variants:

```text
sh scripts/export_medium_robustness_snapshots.sh
```

Generate per-project channel stability tables:

```text
sh scripts/analyze_medium_wala_channel_stability.sh
```

Summarize dataset size and the `minBasicBlocks >= 5` filter:

```text
python3 scripts/summarize_medium_robustness_dataset.py
```

Medium outputs:

```text
target/robustness/medium/snapshots/
target/robustness/medium/stability/
target/robustness/medium/dataset_summary.csv
```

Select raw channels for KNN views:

```text
python3 scripts/select_medium_wala_raw_channels.py
python3 scripts/write_medium_knn_channel_views.py
```

Selection outputs:

```text
target/robustness/medium/channel_selection.csv
target/robustness/medium/knn_views/RAW_ALL_NON_DROP.channels
target/robustness/medium/knn_views/RAW_FILTERED_KEEP.channels
target/robustness/medium/knn_views/RAW_INSTRUCTION_STABLE.channels
```

The selection step does not modify WALA raw outputs. It only decides which
traceable raw channels should be used by later hash-vector/KNN views.

## KNN Candidate Selector Design

The KNN layer is a candidate selector only. It does not decide clone type or
semantic equivalence.

Implemented feature views:

```text
DISCOVRE_NUMERIC
  low-dimensional numeric counts derived from selected raw records

RAW_HASH_BUCKET
  selected raw channel/value pairs hashed into fixed per-channel buckets

HYBRID_NUMERIC_HASH
  numeric counts plus raw hash bucket counts
```

Hashing rule:

```text
featureKey = channel + "\0" + rawValue
bucket = stableHash(featureKey) % bucketCount(channel)
```

Raw values are not rewritten. Hash buckets are derived views, and each feature
keeps provenance back to raw channel, raw value, and source location.

Preprocessing follows the discovRE-style numeric-filter idea:

```text
x' = log10(x + 1)
z  = (x' - mean) / std
```

Features with zero standard deviation are dropped from the KNN vector because
they have no discrimination power.

Current exact KNN distance:

```text
Euclidean distance over standardized vectors
```

Implemented classes:

```text
RawBlockFeatureExtractor
FeaturePreprocessor
ExactKnnIndex
BlockCandidateSelector
```

Run a WALA KNN candidate report:

```text
mvn -Psemantic-analysis exec:java \
  -Dexec.mainClass=com.ziqi.codesim.semantic.backend.wala.raw.WalaKnnCandidateMain \
  -Dexec.args="<left-class-dir> <right-class-dir> <output-csv> HYBRID_NUMERIC_HASH 8"
```

Report columns:

```text
queryBlock
candidateBlock
view
distance
sharedContributionCount
topSharedChannels
```

The report intentionally keeps low-information candidates, such as entry or
empty blocks, instead of hiding them. Later CFG-constrained matching is
responsible for resolving these ambiguous candidates.

## discovRE-Style CFG Similarity Baseline

Implemented baseline classes:

```text
DiscovreBlockFeatureExtractor
DiscovreBlockDistance
DiscovreCfgMatcher
```

The block distance follows the discovRE normalized weighted formula:

```text
dBB =
  sum_i alpha_i * |c_i(a) - c_i(b)|
  /
  sum_i alpha_i * max(c_i(a), c_i(b))
```

The initial weights are copied from discovRE Table III and mapped to WALA
instruction categories:

```text
arithmetic instructions  -> ARITHMETIC
calls                    -> CALL
instructions             -> instruction count
logic instructions       -> LOGIC / COMPARISON
transfer instructions    -> BRANCH / RETURN
string constants         -> string references
numeric constants        -> numeric constants
```

CFG matching follows the discovRE MCS-style baseline:

```text
dBB > 0.5 candidate pairs are pruned
candidate pairs are sorted by dBB ascending
search budget = 16 * max(|G1|, |G2|)
dmcs = 1 - (|matchedPairs| - sum(dBB)) / max(|G1|, |G2|)
similarity = 1 - dmcs
```

This first version intentionally does not yet use KNN constraints. It provides
a clean discovRE-style static CFG similarity baseline before adding the KNN
candidate selector.

The matcher now supports two modes:

```text
exhaustive mode
  empty allowed-pair map
  all block pairs are considered before dBB pruning

KNN-constrained mode
  non-empty allowed-pair map
  only explicitly listed leftBlock -> rightBlock candidates are considered
```

This lets us compare the full discovRE-style CFG matcher against the
KNN-constrained version and measure whether top-k candidate selection loses
important block matches.

Run a WALA end-to-end comparison report:

```text
mvn -Psemantic-analysis exec:java \
  -Dexec.mainClass=com.ziqi.codesim.semantic.backend.wala.raw.WalaDiscovreComparisonMain \
  -Dexec.args="<left-class-dir> <right-class-dir> <output-csv> HYBRID_NUMERIC_HASH 8"
```

Report columns include:

```text
exhaustiveSimilarity
constrainedSimilarity
similarityDelta
exhaustiveCandidatePairs
constrainedCandidatePairs
candidateReduction
exhaustiveMatchedBlocks
constrainedMatchedBlocks
```

On the current robustness probe (`javac -g` vs `javac -g:none`), constrained
matching preserves similarity `1.0` while reducing candidate pairs by roughly
33% to 80% depending on the method.

Raw WALA snapshots must be preserved before any channel filtering or vector
construction. KNN views are derived data and must remain traceable to raw
snapshot records.
