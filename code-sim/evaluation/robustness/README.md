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

Raw WALA snapshots must be preserved before any channel filtering or vector
construction. KNN views are derived data and must remain traceable to raw
snapshot records.
