package com.ziqi.codesim.region.model;

import com.ziqi.codesim.semantic.model.InstructionCategory;

/**
 * One node of a {@link SemanticGraph}: a single SDG statement, lifted out of WALA into our own
 * representation so the rest of the region selector never depends on WALA types.
 *
 * @param id               graph-local sequential id, unique within one {@link SemanticGraph}
 * @param cgNodeId         WALA call-graph node id of the owning method context; nodes with
 *                         different {@code cgNodeId} live in different methods, which is what makes
 *                         cross-method (boundary-free) regions visible
 * @param methodSignature  full signature of the declaring method (provenance + method grouping)
 * @param kind             coarse category of this node
 * @param walaKind         raw WALA {@code Statement.Kind} name, preserved for provenance
 * @param operation        coarse instruction category (reuses the existing discovRE vocabulary);
 *                         {@code OTHER} for pseudo-nodes
 * @param operationToken   name-free normalized operation token used as the WL base label, e.g.
 *                         {@code binaryop:mul}, {@code invoke:internal}, {@code cond:le}. Variable
 *                         names and SSA value numbers are deliberately excluded so structurally
 *                         equal code matches regardless of identifiers
 * @param semanticValueHash hash of the normalized SSA symbolic expression this node computes
 *                          (commutativity + constant/parameter canonicalized, name-free). 0 when
 *                          the node computes no meaningful value (pseudo-nodes, goto, void). This is
 *                          the semantic channel: a fast seed-level approximation of "what it
 *                          computes", NOT a proof of equivalence (that is Phase B's SMT job)
 * @param source           source line range this node maps back to (synthetic for pseudo-nodes)
 * @param instructionText  best-effort human-readable instruction text (may be empty for pseudo-nodes)
 */
public record SemanticNode(
        int id,
        int cgNodeId,
        String methodSignature,
        NodeKind kind,
        String walaKind,
        InstructionCategory operation,
        String operationToken,
        long semanticValueHash,
        SourceSpan source,
        String instructionText
) {
    public boolean hasSource() {
        return source != null && source.isKnown();
    }
}
