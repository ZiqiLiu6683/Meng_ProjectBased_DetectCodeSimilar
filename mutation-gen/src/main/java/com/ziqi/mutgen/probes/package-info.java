/**
 * One-off diagnostic probes, kept because each one is the evidence behind a decision.
 *
 * None of these are part of corpus generation. Each was written to answer a single question that a
 * measurement had raised, usually by copying a tool and making it report *why* something failed
 * instead of *how often*. They are archived rather than deleted so a later "how do we know that?"
 * can be answered without re-deriving anything; see {@code code-sim/docs/decision-log.md} for the
 * conclusions they produced.
 *
 * <ul>
 *   <li>{@code SeedFeasibilityProbe} — how many CodeNet seeds can host the mutated ranges? The
 *       first version required every range inside ONE method, which was a self-imposed constraint
 *       the region-level contract never asked for (2.3% usable). {@code 2} spread ranges across
 *       methods; {@code 3} additionally counted a long method's capacity for several ranges, which
 *       is what raised the answer to 17.3%.</li>
 *   <li>{@code DonorProbe} — can a block of foreign code be substituted into a seed and still
 *       compile? {@code 2} tried insert-only instead of replace (it did not help); {@code 3} and
 *       {@code 4} captured the javac errors and then the missing symbol names, which is what
 *       identified donor-local helper methods and nested classes as the real cause.</li>
 *   <li>{@code DonorLibDiag} — why did blocks that passed the portability analysis still fail
 *       compilation at the destination? Answer: the insertion point, not the blocks.</li>
 *   <li>{@code T3FailureDiag} — which javac rule did each surviving T3 mutation break? Answer:
 *       unreachable statement, definite assignment, and missing return — all fixed in
 *       {@link com.ziqi.mutgen.T3OperatorBatch}.</li>
 * </ul>
 */
package com.ziqi.mutgen.probes;
