# Decision and Evidence Log

Chronological record of what was decided, what was measured, and what turned out to be wrong.
Newest entries at the top. The point of this file is to make a later "why did we do it this way?"
answerable without re-deriving anything.

**Evidence grades** used throughout:

| Grade | Meaning |
| --- | --- |
| **measured** | backed by a run whose data is in hand |
| **code-verified** | read in source or git history; impact not measured |
| **inferred** | judgement, no data |
| **unknown** | cannot be determined from what is available |

**Before reporting any performance or accuracy comparison, answer three questions.** Each was
violated at least once in the session below, and each violation produced a result that looked
correct:

1. Are both sides the same build?
2. Is the cache / intermediate-artifact state the same on both sides?
3. If the measurement were broken, would my check notice?

---

## 2026-07-26 — MIF mutation corpus

### Decision: seed corpus is CodeNet Java250, not IJaDataset or TheAlgorithms

**measured** compile rate, sampled, with the detector's own public-type renaming applied:

| Corpus | standalone | with `-sourcepath` |
| --- | ---: | ---: |
| IJaDataset (`bcb_reduced`) | 14% | 14% (no gain) |
| TheAlgorithms/Java | 78% | 100% |
| **CodeNet Java250** | **100%** | — |

IJaDataset fails on missing third-party jars (`junit.framework`, `org.apache.commons.logging`,
`org.osgi.framework`, …), not on missing siblings — which is why `-sourcepath` adds nothing there.
Its layout is one directory per *functionality*, so a file's real siblings are not co-located; this
is confirmed by BigCloneEval's own docs, not inferred.

TheAlgorithms was the first pick and was **wrong to choose without comparison**: it is a single
educational repository, has no citation, and gives no cross-project diversity. CodeNet Java250 is
citable (IBM, arXiv 2105.12655), already E5 in the evaluation protocol, 75,000 self-contained
files (58,252 in the 20–400 line band), 14.6 MB download, and same-problem solutions double as
semantic-clone material.

Qualitas.class was the stronger candidate on paper (real systems, two publications, a *compiled*
distribution) but **its host is offline** — three URLs return connection failure, not 404.

### Finding: several MIF operators cannot produce compilable code, by design

**measured** on 354 generated mutants: 286 dropped (81%) because the mutant does not compile.

| Operator | Transformation | Compiles? |
| --- | --- | --- |
| `mSIL` | `sc.nextInt()` → `sc.nextInt(X1)` | never — `X1` is undeclared |
| `mML` | `f(x);` → `if (X==Y) f(x);` | never — `X`, `Y` undeclared |
| `mARI` | renames a declaration **only** | rarely — uses are left dangling |
| `mSRI` | renames a declaration **and all uses** | yes |
| T1 operators (whitespace/comments/formatting) | layout only | always |

The framework targets **text-based** detectors (NiCad, SourcererCC), which never compile anything,
so an undeclared placeholder is harmless there. It is not harmless for a compilation-based
detector. `mARI` is the clearest case: inconsistent renaming is the *point* of the operator, and it
is unusable here for exactly that reason.

Consequence: the nominal "15 operators × 400 = 6,000 pairs" target is not reachable. T3 has 5
operators but only 3 usable ones. **Report generated-vs-kept counts; the drop rate is a property of
the operators, not a silent filter.**

### Decision: the generator checks compilability and drops non-compiling pairs

`scripts/experiments/gen_mutants.py` replaces `gen_mutants.sh`, which needs `shuf`, `mapfile` and
`timeout` and therefore only ran under WSL. The Python version also records the **mutated line
range on both sides**, giving this corpus a localisation ground truth that neither BigCloneBench
(pair-level ranges only) nor the in-house set (no ranges) provides.

### Retracted: "TXL's trailing newline corrupts the range annotation"

**inferred from a `diff` marker, then disproved.** `\ No newline at end of file` is diff-command
output, not a line-content difference: Python `splitlines()` treats `"a\nb"` and `"a\nb\n"`
identically. Checked all 300 pairs — ranges before and after normalisation are identical in every
one. **No change was made.** The 18 multi-hunk diffs are all `mSRI` doing consistent renaming
across every occurrence, which is correct behaviour.

---

## 2026-07-26 — Pipeline changes

### Decision: the pre-Phase-A discovRE/kNN channel is off by default

`-Dcodesim.legacyCfgChannels=false` is now the default; the switch and the classes stay so the
comparison is reproducible on a future corpus.

**measured** over 209 pairs (36 labelled clones, 140 negatives), same build (class fingerprint
verified identical before and after both arms), warm compile cache:

| | on | off |
| --- | ---: | ---: |
| pair-level recall | 36/36 | 36/36 |
| pair-level false positives | 0/140 | 0/140 |
| method-scoped T1/T2/T3 regions | 68 | **0** |
| Phase A regions emitted | 124 | **145** |
| graph stage total | 528 s | **370 s** (−30%) |
| end to end | 5551 s | **4523 s** (−18.5%) |

The region increase was not expected. Cause, **code-verified**: `AcceptedRegionSelector.kindPriority`
ranks `METHOD` (6) above `CALL_EXPANDED_REGION` (5), and `lowerPriorityContained` then suppressed
Phase A regions contained in a method candidate. Suppression fell from 77% to 52% of candidates.
The channel was not merely adding method-level output — it was **hiding region-level output**.

### Decision: Phase B method-pair candidates may only yield T4 or NON_CLONE

A candidate whose evidence is *only* `SEMANTIC_EQUIV_SCAN` or `DYNAMIC_EQUIV_SCAN` now skips the
T1/T2/T3 layers. Those channels say "these two METHODS behave alike" and make no syntactic claim;
running them through the syntactic cascade produced a method-scoped T2, which the region-level
result contract excludes. A candidate carrying *any* syntactic evidence still runs the full
cascade, so the strict T1→T2→T3→T4 order is unchanged.

- **measured**: re-running the same 209 pairs produced **byte-identical output** — 0 pairs changed.
  That proves *no regression*; it does **not** prove the fix works, because those runs used
  `-Dcodesim.skipDynamic=true` and never triggered the path.
- **measured** on 30 MIF pairs **with the dynamic tier enabled** (20 reached
  `SOURCE_PLUS_WALA_SMT_DYNAMIC`, dynamic stage SUCCESS on all 20): method-scoped syntactic
  verdicts on the WALA path = **0**. The 14 that remain all come from the source-only fallback,
  where method and window regions are the only thing available.

### Decision: candidate discovery no longer applies a similarity threshold

`NextCandidateDiscovery` gated proposals at the same 0.50 Type-3 boundary the recognizer uses, so a
region pair below it never became a candidate and could not even be reported as refused. That
contradicts the pipeline's own rule that discovery proposes and Stage 4 decides.

### Decision: region growth requires ≥2 aligned *substantive* nodes

**measured** on 1,183 grown regions: 599 projected to no source tokens, and every one of them fails
this floor — 356 with no source mapping at all, 243 with exactly one substantive node and a
one-line span. So the floor makes an accidental filter explicit rather than changing behaviour.

**Retracted along the way**: "58.6% of Phase A regions are lost at projection, including 26 real
T2 regions." **inferred, then disproved** — all 599 are degenerate single-node regions, the same
ones that produced 14 false positives on negatives. A1 and A3 were one problem, not two.

### Retracted: "replace the T3 text threshold with graph alignment"

The first scan (BCB smoke, **no negative samples at all**) recovered 3 missed MT3 pairs and showed
a clean `alignedFraction` gap. With 126 negatives added, **pure graph evidence produced a 100%
false-positive rate (126/126)** and the gap vanished — positives from 0.500, negatives from 0.503.

Corrected conclusion: graph evidence is an **additional necessary condition**, not a replacement.
Graph AND ≥2 substantive nodes AND text similarity ≥0.50 gives 36/36 recall with 0/126 false
positives, strictly better than text alone (2/126). Lowering the text floor to 0.25–0.40 recovers
the 3 MT3 pairs at the cost of 1/126 — **not adopted**: that trade rests on 5 data points and the
threshold was derived by looking at the test data.

---

## 2026-07-26 — Environment and datasets

- **TXL 10.8b** installed to `~/bin` (user-level, no sudo). Its bundled Gatekeeper step is obsolete
  on current macOS ("This operation is no longer supported"); the quarantine attribute had to be
  cleared separately.
- **MIF operators** extracted to `~/codesim-mif/mutators-java` (172 KB, self-contained: only needs
  `jtokens.grm` and `random.mod`, both alongside). The 3.1 GB clone was deleted.
- **`~/Downloads` is unreadable** to tooling under macOS privacy protection — `ls` returns empty
  and `cp` fails with "Operation not permitted" while `stat` succeeds. An empty listing there is
  **not** evidence that a file is absent; that mistake was made once in this session. Data was moved
  to `~/codesim-data/`.
- **BigCloneBench and IJaDataset are downloaded** (5.5 GB and 618 MB). Decision: BCB experiments run
  on the Windows machine (see `scripts/experiments/RUNBOOK_WINDOWS.md`), MIF on the Mac.

### Finding: WALA cannot analyse code whose dependencies do not resolve

**measured**, controlled experiment on WALA's **source** front-end (`com.ibm.wala.cast.java.ecj`):

| Input | Result |
| --- | --- |
| `Target.java` + `Helper.java` | call graph built, 8 nodes, 2.2 s |
| `Target.java` alone | `ClassHierarchyException` → NPE at `MethodInvocation.resolveMethodBinding()` |

So the source front-end removes the need to produce `.class` files but **not** the need for
resolvable bindings. Compilation success is a hard precondition for Phase A on both front-ends.

Setup notes if this is ever retried: WALA's own POM pairs `jdt.core-3.36.0` with `ecj-3.44.0`,
which are incompatible (`NoSuchMethodError` on `Scanner.getNextToken`); use matching 3.36.0. Needs
the full 31-jar Eclipse platform closure and WALA's own scope initialisation (`SourceDirCallGraph`).

### Decision: `-sourcepath` resolves real sibling sources before generating stubs

New `SOURCE_PATH_CONTEXT` compilation mode. Two-pass, because a single javac run would emit the
siblings into the analysed classes directory and WALA would load them as Application classes —
i.e. as clone candidates. Pass 1 compiles with `-sourcepath` into `sibling-classes/`; pass 2
recompiles the target alone against those into `classes/`; the target's own class files are then
stripped from the context directory.

**measured** on the fixture: `classes/` holds only `Target.class`, `sibling-classes/` only
`Helper.class`. Unlike stubs this context is real code, so it keeps full T4 eligibility.

**Not applicable to BigCloneBench** (`bcb_reduced` groups by functionality, so pointing a source
path at that directory would pull in unrelated projects' classes). `--project-root-depth` therefore
defaults to 0.
