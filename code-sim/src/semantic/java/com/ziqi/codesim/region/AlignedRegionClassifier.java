package com.ziqi.codesim.region;

import com.ziqi.codesim.region.grow.AlignedPair;
import com.ziqi.codesim.region.grow.RegionGroup;
import com.ziqi.codesim.region.model.SemanticGraph;
import com.ziqi.codesim.region.model.SemanticNode;
import com.ziqi.codesim.semantic.model.InstructionCategory;

import java.util.HashSet;
import java.util.Set;

/**
 * Boundary-free T1/T2/T3 classifier that judges a Phase A region on its ALIGNMENT, not on projected
 * source text. This is the piece the projection seam was missing: it walks the aligned node pairs
 * (which already erase method boundaries and variable names) and reads the syntactic clone type off
 * two signals the alignment already carries -- per-pair agreement and coverage of the spanned
 * methods -- so a helper-extracted or reordered near-copy is scored as T1/T2/T3 on its aligned
 * content instead of being forced to T4 just because its source token order does not line up.
 *
 * <p>How the two signals map to a type:
 * <ul>
 *   <li>{@link AlignedPair#agreement()} is 1.0 when the two nodes compute the SAME value
 *       (identical incl. literals), 0.8 when they share structure but differ in a literal/name, 0.5
 *       when only their operation matches (a modified operation). The MINIMUM agreement over the
 *       substantive pairs is the node-level match grade.</li>
 *   <li>An unaligned SUBSTANTIVE node in either spanned method is an insert/delete -- Type-3 edit
 *       evidence. No unaligned substantive nodes means the region covers both sides completely.</li>
 * </ul>
 *
 * <p>Type decision (only the one already-justified BigCloneBench 0.50 fraction is a threshold):
 * <ul>
 *   <li>aligned fraction &lt; 0.50 -&gt; {@code NONE}: too little lines up to call it a syntactic
 *       clone; leave it to the behavioural (T4) path.</li>
 *   <li>full coverage (no edits) &amp; min agreement 1.0 -&gt; {@code T1} (identical computation).</li>
 *   <li>full coverage &amp; min agreement &ge; 0.8 -&gt; {@code T2} (same structure, changed
 *       literals/names).</li>
 *   <li>otherwise (edits present, or a modified operation) -&gt; {@code T3} (near-miss).</li>
 * </ul>
 *
 * <p>Honest limit: at the SSA level a pure LOCAL-variable rename is invisible (SSA value numbers
 * erase it), so a source "T2 by variable rename" with identical literals reads here as {@code T1}.
 * Literal changes and field/method/type renames remain visible and still read as {@code T2}.
 */
public final class AlignedRegionClassifier {

    /** The single inherent threshold, shared with the recognizer: the BigCloneBench Type-3 boundary. */
    private static final double MIN_T3_ALIGNED_FRACTION = 0.50;
    private static final double T1_MIN_AGREEMENT = 0.999; // agreement == 1.0 (same semantic value hash)
    private static final double T2_MIN_AGREEMENT = 0.79;  // agreement >= 0.8 (same WL structure)

    public enum SyntacticType { T1, T2, T3, NONE }

    /** Verdict plus the raw signals it was read from, so a caller/test can see WHY. */
    public record Verdict(SyntacticType type, double alignedFraction, double minAgreement,
                          boolean editsPresent, int alignedSubstantive,
                          int leftSubstantive, int rightSubstantive) {
    }

    public Verdict classify(RegionGroup region, SemanticGraph leftGraph, SemanticGraph rightGraph) {
        Set<Integer> alignedLeft = new HashSet<>();
        Set<Integer> alignedRight = new HashSet<>();
        int alignedSubstantive = 0;
        double minAgreement = Double.MAX_VALUE;

        for (AlignedPair pair : region.alignment()) {
            SemanticNode left = leftGraph.node(pair.leftNodeId()).orElse(null);
            if (left != null) {
                alignedLeft.add(left.id());
            }
            SemanticNode right = rightGraph.node(pair.rightNodeId()).orElse(null);
            if (right != null) {
                alignedRight.add(right.id());
            }
            // The node-level grade is read only from substantive pairs; plumbing/return pairs (which
            // may legitimately match loosely across a call bridge) must not drag the grade down.
            if (left != null && isSubstantive(left.operation())) {
                alignedSubstantive++;
                minAgreement = Math.min(minAgreement, pair.agreement());
            }
        }

        int leftSubstantive = substantiveCount(leftGraph, region.leftMethods());
        int rightSubstantive = substantiveCount(rightGraph, region.rightMethods());
        int larger = Math.max(leftSubstantive, rightSubstantive);

        if (alignedSubstantive == 0 || larger == 0) {
            return new Verdict(SyntacticType.NONE, 0.0, 0.0, true,
                    alignedSubstantive, leftSubstantive, rightSubstantive);
        }

        double alignedFraction = (double) alignedSubstantive / larger;
        // An unaligned substantive node on either side is an insert/delete (Type-3 edit evidence).
        int unalignedLeft = countUnalignedSubstantive(leftGraph, region.leftMethods(), alignedLeft);
        int unalignedRight = countUnalignedSubstantive(rightGraph, region.rightMethods(), alignedRight);
        boolean editsPresent = unalignedLeft > 0 || unalignedRight > 0;

        SyntacticType type;
        if (alignedFraction < MIN_T3_ALIGNED_FRACTION) {
            type = SyntacticType.NONE;
        } else if (!editsPresent && minAgreement >= T1_MIN_AGREEMENT) {
            type = SyntacticType.T1;
        } else if (!editsPresent && minAgreement >= T2_MIN_AGREEMENT) {
            type = SyntacticType.T2;
        } else {
            type = SyntacticType.T3;
        }
        return new Verdict(type, alignedFraction, minAgreement, editsPresent,
                alignedSubstantive, leftSubstantive, rightSubstantive);
    }

    private static int substantiveCount(SemanticGraph graph, Set<String> methods) {
        int count = 0;
        for (String method : methods) {
            for (SemanticNode node : graph.nodesInMethod(method)) {
                if (isSubstantive(node.operation())) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countUnalignedSubstantive(SemanticGraph graph, Set<String> methods, Set<Integer> aligned) {
        int count = 0;
        for (String method : methods) {
            for (SemanticNode node : graph.nodesInMethod(method)) {
                if (isSubstantive(node.operation()) && !aligned.contains(node.id())) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * "Real work" nodes whose presence/absence is an edit: arithmetic/logic/comparison/branch/field/
     * array/allocation/call/assignment. Returns, constants and plumbing pseudo-nodes are excluded so
     * boilerplate does not read as an insert/delete. Mirrors the substance split in
     * {@code RegionGrower.substanceWeight} (weight >= 0.6).
     */
    private static boolean isSubstantive(InstructionCategory operation) {
        return switch (operation) {
            case ARITHMETIC, LOGIC, COMPARISON, BRANCH, FIELD_ACCESS, ARRAY_ACCESS, ALLOCATION,
                 CALL, ASSIGNMENT -> true;
            case RETURN, CONSTANT, OTHER -> false;
        };
    }
}
