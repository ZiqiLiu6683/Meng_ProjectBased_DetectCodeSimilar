# Modular Semantic Similarity Pipeline

Date: 2026-05-25

This document records the planned implementation of a new analysis pipeline
inspired by discovRE, BinHunt, and iBinHunt. The goal is not to patch the
existing AST-token pipeline, but to add a separate bytecode/IR-based method
that can be enabled in layers.

## Design Goal

The three paper families share a large amount of infrastructure:

```text
program -> IR -> basic blocks -> CFG -> block matching
```

Therefore, the implementation should not duplicate three pipelines. It should
provide one shared representation layer and three attachable analysis modes:

```text
Static CFG mode          discovRE-style
Semantic verification    BinHunt-style
Inter-procedural mode    iBinHunt-style
```

User-facing reports must only display real tool output or directly traceable
derived evidence. A CFG visualization must be generated from real CFG data
emitted by the active backend, such as WALA basic blocks and successor edges.
If that backend is not available, the UI should report the missing analysis
source instead of substituting a demo graph or simulated preview.

## Tooling Direction

The implementation should rely on established analysis tools instead of
manually rebuilding compiler infrastructure.

### Base Static Analysis

Candidate tools:

- WALA: Java bytecode IR, CFG, call graph, and inter-procedural CFG APIs.
- Soot/SootUp: Java bytecode/Jimple IR, call graph, data-flow analysis.

Initial preference:

```text
WALA for the first implementation
```

Reason:

- It has mature Java bytecode IR and CFG support.
- It exposes inter-procedural CFG concepts directly.
- It is suitable for bytecode-level analysis, which is closer to the binary
  analysis papers than JavaParser AST.

Soot/SootUp remains a second backend candidate if WALA integration blocks.

### SMT / Theorem Proving

Candidate tools:

- JavaSMT as a solver-independent Java API.
- Z3 as the main backend solver.

Initial preference:

```text
JavaSMT API with Z3 backend when available
```

The pipeline should not bind semantic summaries directly to one solver API.

### Dynamic Taint and Input Generation

Candidate tools:

- Phosphor for JVM dynamic taint tracking.
- Symbolic PathFinder or JBSE for Java bytecode symbolic execution and path
  constraint generation.
- JQF/Zest may be useful later for coverage-guided input exploration, but it
  does not replace symbolic path constraint solving.

These are later layers and should plug into the same CFG/IR abstraction.

## Shared Representation

The following concepts should be owned by our codebase and filled by adapters:

```text
AnalyzedProgram
AnalyzedMethod
InstructionUnit
BasicBlockUnit
ControlFlowGraph
InterproceduralGraph
MethodFeatures
BlockFeatures
BlockMatch
PathSummary
SemanticSummary
```

Adapters convert tool-specific output into this representation:

```text
WalaAnalysisBackend
SootAnalysisBackend
```

The rest of the pipeline should not depend on WALA/Soot classes directly.

## Mode 1: Static CFG Mode

Paper inspiration:

```text
discovRE
```

Purpose:

- Provide efficient static similarity.
- Filter candidates.
- Compare CFG structure and block features without symbolic reasoning.

Implementation pieces:

```text
IR extraction
CFG extraction
method numeric features
block numeric features
candidate selection
CFG/block matching
```

Method-level features:

```text
basic block count
CFG edge count
branch count
loop / SCC count
call count
internal call count
external call count
arithmetic op count
logic/comparison op count
assignment count
return count
parameter count
local variable count
constant count
string reference count
```

Block-level features:

```text
instruction count
opcode/category histogram
call category histogram
constant categories
string references
successor/predecessor counts
```

Similarity:

```text
numeric feature distance
block distance
approximate CFG matching
```

This mode should be fast and explainable. It does not prove semantic
equivalence.

## Mode 2: Semantic Verification

Paper inspiration:

```text
BinHunt
```

Purpose:

- Verify whether matched blocks or paths have equivalent symbolic effects.
- Reduce false positives where structure is similar but behavior differs.

Implementation pieces:

```text
symbolic summary builder
path condition extractor
branch condition extractor
return expression extractor
state update extractor
SMT equivalence checker
```

Output categories:

```text
EQUIVALENT
DIFFERENT
UNKNOWN
UNSUPPORTED
```

This mode reuses Mode 1 block/path matches. It should not redo CFG matching.

## Mode 3: Inter-Procedural Mode

Paper inspiration:

```text
iBinHunt
```

Purpose:

- Handle function boundary changes, such as helper extraction, method merging,
  and call-chain refactoring.
- Compare relevant cross-method paths instead of only caller bodies.

Implementation pieces:

```text
ICFG extraction
dynamic taint trace collection
input generation / path exploration
tainted block candidate selection
inter-procedural path matching
semantic verification on matched paths
```

This mode reuses:

- shared IR/CFG representation
- Mode 1 block features and matching
- Mode 2 semantic verifier

It should not duplicate static CFG or semantic comparison logic.

## Expected Pipeline Composition

```text
Base:
  AnalysisBackend
  IR / CFG / features

Static:
  MethodFeatureDistance
  BlockMatcher
  CfgMatcher

Semantic:
  SymbolicSummary
  SmtEquivalenceChecker

Inter-procedural:
  ICFG
  TaintTrace
  InputGeneration
  CrossMethodPathMatcher
```

## First Implementation Target

The first implementation target is not the full iBinHunt layer. It is the
shared base plus the discovRE-style static CFG mode:

```text
Java source or bytecode
  -> WALA analysis backend
  -> method CFGs
  -> method/block numeric features
  -> static CFG comparison report
```

This is the required base for both BinHunt-style and iBinHunt-style layers.

Implemented first slice:

```text
shared semantic model
WALA bytecode analysis backend
method/block numeric feature extraction
static method CFG matcher
profile-gated WALA integration test
```

Not implemented yet:

```text
candidate indexing / top-k retrieval
SMT semantic equivalence checker
inter-procedural CFG path matching
dynamic taint and automatic input generation
```

## Evaluation Plan

Run the new method against the existing 55-pair evaluation dataset and compare:

```text
existing staged AST pipeline
static CFG mode
static CFG + semantic verification
static CFG + semantic verification + inter-procedural mode
```

Key evaluation questions:

- Does static CFG reduce blind-normalization false positives?
- Which hard negatives still require semantic verification?
- Which helper/call-chain cases require inter-procedural mode?
- What is the runtime cost at each layer?
