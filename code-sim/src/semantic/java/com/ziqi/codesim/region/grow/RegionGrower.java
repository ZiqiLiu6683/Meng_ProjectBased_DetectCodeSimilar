package com.ziqi.codesim.region.grow;

import com.ziqi.codesim.region.model.NodeDescriptor;
import com.ziqi.codesim.region.model.NodeKind;
import com.ziqi.codesim.region.model.SemanticEdge;
import com.ziqi.codesim.region.model.SemanticGraph;
import com.ziqi.codesim.region.model.SemanticNode;
import com.ziqi.codesim.region.model.SourceSpan;
import com.ziqi.codesim.region.seed.SeedPair;
import com.ziqi.codesim.semantic.model.InstructionCategory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Phase A core: grows boundary-free, cross-method region groups from seed anchors by seed-and-extend
 * alignment over the two SDGs. Each region is the (heuristically) maximal common connected subgraph
 * reachable from a seed by walking <em>corresponding</em> dependence edges (same {@code EdgeKind} and
 * direction) into compatible node pairs.
 *
 * <p>Design notes:
 * <ul>
 *   <li><b>Regions are an output, not an input.</b> We never enumerate candidate regions; the
 *       alignment between the two graphs produces them. This is why there is no region-dedup step:
 *       a node-disjoint claimed set means each grown region is maximal and non-overlapping.</li>
 *   <li><b>Boundary-free.</b> Because the SDG edges include interprocedural call/param/return edges,
 *       growth crosses method boundaries naturally (helper extraction, inlining).</li>
 *   <li><b>Greedy.</b> Maximum common subgraph is NP-hard, so we grow strongest-seed-first and pick
 *       the best-agreeing corresponding neighbour at each step. This is the intended approximation;
 *       proving equivalence of a grown region is Phase B's job.</li>
 * </ul>
 * Pure Java.
 */
public final class RegionGrower {

    private final int minPairs;
    private final int minSubstantivePairs;

    public RegionGrower() {
        this(2, 2);
    }

    /** @param minPairs smallest region (in aligned pairs) worth emitting; singletons are dropped */
    public RegionGrower(int minPairs) {
        this(minPairs, 2);
    }

    /**
     * @param minPairs            smallest region in aligned pairs, counting plumbing
     * @param minSubstantivePairs smallest region in aligned pairs that do REAL WORK (arithmetic,
     *                            logic, comparison, branch, field/array access, allocation, call,
     *                            assignment). {@code minPairs} alone is not enough: a region of two
     *                            aligned pairs where only one does real work scores a perfect
     *                            aligned fraction (1 of 1 substantive node) while representing a
     *                            single statement, which is a coincidence rather than a clone.
     *                            Measured on 1,183 grown regions, every region that failed this
     *                            floor also projected to no source tokens and was silently dropped
     *                            further down the pipeline -- so this makes an accidental filter
     *                            explicit rather than changing behaviour, and it stops those
     *                            regions from becoming false positives for any consumer that reads
     *                            the alignment directly instead of the projected source.
     */
    public RegionGrower(int minPairs, int minSubstantivePairs) {
        if (minPairs < 1) {
            throw new IllegalArgumentException("minPairs must be >= 1: " + minPairs);
        }
        if (minSubstantivePairs < 0) {
            throw new IllegalArgumentException(
                    "minSubstantivePairs must be >= 0: " + minSubstantivePairs);
        }
        this.minPairs = minPairs;
        this.minSubstantivePairs = minSubstantivePairs;
    }

    public List<RegionGroup> grow(SemanticGraph leftGraph, Map<Integer, NodeDescriptor> leftDesc,
                                  SemanticGraph rightGraph, Map<Integer, NodeDescriptor> rightDesc,
                                  List<SeedPair> seeds) {
        Aligner aligner = new Aligner(leftGraph, leftDesc, rightGraph, rightDesc);
        List<RegionGroup> regions = new ArrayList<>();
        for (SeedPair seed : seeds) {
            if (aligner.isClaimed(seed.leftNodeId(), seed.rightNodeId())) {
                continue;
            }
            List<AlignedPair> alignment = aligner.growFrom(seed);
            if (alignment.size() < minPairs
                    || substantivePairs(alignment, leftGraph) < minSubstantivePairs) {
                continue;
            }
            aligner.claim(alignment);
            regions.add(toRegionGroup(alignment, leftGraph, rightGraph));
        }
        regions.sort(Comparator.comparingDouble(RegionGroup::priority).reversed()
                .thenComparing(Comparator.comparingInt(RegionGroup::size).reversed()));
        return regions;
    }

    private static RegionGroup toRegionGroup(List<AlignedPair> alignment,
                                             SemanticGraph leftGraph, SemanticGraph rightGraph) {
        double strength = alignment.stream().mapToDouble(AlignedPair::agreement).average().orElse(0.0);
        double coverage = 0.0;
        SourceSpan leftSpan = SourceSpan.SYNTHETIC;
        SourceSpan rightSpan = SourceSpan.SYNTHETIC;
        Set<String> leftMethods = new TreeSet<>();
        Set<String> rightMethods = new TreeSet<>();
        for (AlignedPair pair : alignment) {
            SemanticNode left = leftGraph.node(pair.leftNodeId()).orElse(null);
            if (left != null) {
                leftSpan = leftSpan.union(left.source());
                leftMethods.add(left.methodSignature());
                coverage += substanceWeight(left.operation());
            }
            SemanticNode right = rightGraph.node(pair.rightNodeId()).orElse(null);
            if (right != null) {
                rightSpan = rightSpan.union(right.source());
                rightMethods.add(right.methodSignature());
            }
        }
        return new RegionGroup(alignment, strength, coverage, leftSpan, rightSpan, leftMethods, rightMethods);
    }

    /**
     * Aligned pairs whose left node does real work. Mirrors the substance split used by
     * {@code AlignedRegionClassifier}: everything {@link #substanceWeight} scores at or above 0.6.
     */
    private static int substantivePairs(List<AlignedPair> alignment, SemanticGraph leftGraph) {
        int count = 0;
        for (AlignedPair pair : alignment) {
            SemanticNode left = leftGraph.node(pair.leftNodeId()).orElse(null);
            if (left != null && substanceWeight(left.operation()) >= 0.6) {
                count++;
            }
        }
        return count;
    }

    /**
     * How much "real work" an aligned node represents, for coverage scoring. Core computation
     * (arithmetic/logic/comparison/branch/field/array/allocation) counts full; calls slightly less;
     * assignments less; returns, constants, and plumbing pseudo-nodes (operation OTHER) count
     * little so a trivial constructor or getter does not outrank a substantive clone.
     */
    private static double substanceWeight(InstructionCategory operation) {
        return switch (operation) {
            case ARITHMETIC, LOGIC, COMPARISON, BRANCH, FIELD_ACCESS, ARRAY_ACCESS, ALLOCATION -> 1.0;
            case CALL -> 0.8;
            case ASSIGNMENT -> 0.6;
            case RETURN, CONSTANT, OTHER -> 0.1;
        };
    }

    /** Holds the per-grow() shared state (graphs, descriptors, global claimed nodes). */
    private static final class Aligner {
        private final SemanticGraph leftGraph;
        private final SemanticGraph rightGraph;
        private final Map<Integer, NodeDescriptor> leftDesc;
        private final Map<Integer, NodeDescriptor> rightDesc;
        private static final int MAX_BRIDGE_DEPTH = 6;

        private final Set<Integer> claimedLeft = new HashSet<>();
        private final Set<Integer> claimedRight = new HashSet<>();

        // Per-region working state, reset at the start of each growFrom().
        private Map<Integer, AlignedPair> alignment;
        private Set<Integer> usedRight;
        private Deque<int[]> frontier;

        Aligner(SemanticGraph leftGraph, Map<Integer, NodeDescriptor> leftDesc,
                SemanticGraph rightGraph, Map<Integer, NodeDescriptor> rightDesc) {
            this.leftGraph = leftGraph;
            this.rightGraph = rightGraph;
            this.leftDesc = leftDesc;
            this.rightDesc = rightDesc;
        }

        boolean isClaimed(int leftId, int rightId) {
            return claimedLeft.contains(leftId) || claimedRight.contains(rightId);
        }

        void claim(List<AlignedPair> region) {
            for (AlignedPair pair : region) {
                claimedLeft.add(pair.leftNodeId());
                claimedRight.add(pair.rightNodeId());
            }
        }

        List<AlignedPair> growFrom(SeedPair seed) {
            alignment = new LinkedHashMap<>();
            usedRight = new HashSet<>();
            frontier = new ArrayDeque<>();
            tryAdd(seed.leftNodeId(), seed.rightNodeId());
            while (!frontier.isEmpty()) {
                int[] current = frontier.poll();
                int left = current[0];
                int right = current[1];
                extend(left, right, true);
                extend(left, right, false);
            }
            return new ArrayList<>(alignment.values());
        }

        /**
         * Align each next reachable anchor on the left to its best counterpart on the right. "Next
         * reachable" allows passing through interprocedural plumbing (return statements and
         * return/param pseudo-nodes) so a left {@code mul -> add} edge can correspond to a right
         * {@code mul -> (helper return + call plumbing) -> add} path. Real computations are never
         * passed through, so growth cannot leak across unrelated code.
         */
        private void extend(int left, int right, boolean outgoing) {
            Map<Integer, Integer> leftTargets = reachableTargets(leftGraph, left, outgoing);
            Map<Integer, Integer> rightTargets = reachableTargets(rightGraph, right, outgoing);
            List<Integer> orderedLeft = new ArrayList<>(leftTargets.keySet());
            orderedLeft.sort(Comparator.comparingInt((Integer id) -> leftTargets.get(id))
                    .thenComparingInt(id -> id));
            for (int leftTarget : orderedLeft) {
                if (alignment.containsKey(leftTarget) || claimedLeft.contains(leftTarget)) {
                    continue;
                }
                int bestRight = -1;
                int bestDepth = Integer.MAX_VALUE;
                double bestAgreement = -1.0;
                for (Map.Entry<Integer, Integer> candidate : rightTargets.entrySet()) {
                    int rightTarget = candidate.getKey();
                    int depth = candidate.getValue();
                    if (usedRight.contains(rightTarget) || claimedRight.contains(rightTarget)) {
                        continue;
                    }
                    if (!compatible(leftTarget, rightTarget)) {
                        continue;
                    }
                    double agreement = agreement(leftTarget, rightTarget);
                    boolean better = depth < bestDepth
                            || (depth == bestDepth && agreement > bestAgreement)
                            || (depth == bestDepth && agreement == bestAgreement && rightTarget < bestRight);
                    if (better) {
                        bestDepth = depth;
                        bestAgreement = agreement;
                        bestRight = rightTarget;
                    }
                }
                if (bestRight >= 0) {
                    tryAdd(leftTarget, bestRight);
                }
            }
        }

        /**
         * Nodes reachable from {@code start} in the given direction, recorded with their hop
         * distance. Every immediate neighbour is recorded as a candidate target; we only recurse
         * <em>through</em> bridge nodes (return statements + return/param plumbing), never through
         * real computations, and only up to {@link #MAX_BRIDGE_DEPTH}.
         */
        private Map<Integer, Integer> reachableTargets(SemanticGraph graph, int start, boolean outgoing) {
            Map<Integer, Integer> depth = new HashMap<>();
            Deque<int[]> expand = new ArrayDeque<>();
            for (SemanticEdge edge : outgoing ? graph.outgoing(start) : graph.incoming(start)) {
                visitTarget(outgoing ? edge.toId() : edge.fromId(), 1, graph, depth, expand);
            }
            while (!expand.isEmpty()) {
                int[] current = expand.poll();
                int node = current[0];
                int nodeDepth = current[1];
                for (SemanticEdge edge : outgoing ? graph.outgoing(node) : graph.incoming(node)) {
                    visitTarget(outgoing ? edge.toId() : edge.fromId(), nodeDepth + 1, graph, depth, expand);
                }
            }
            depth.remove(start);
            return depth;
        }

        private void visitTarget(int node, int depth, SemanticGraph graph,
                                 Map<Integer, Integer> depths, Deque<int[]> expand) {
            Integer previous = depths.get(node);
            if (previous != null && previous <= depth) {
                return;
            }
            depths.put(node, depth);
            if (depth < MAX_BRIDGE_DEPTH && isBridge(graph, node)) {
                expand.add(new int[]{node, depth});
            }
        }

        /** A node growth may pass through: interprocedural return/param plumbing or a return stmt. */
        private boolean isBridge(SemanticGraph graph, int nodeId) {
            SemanticNode node = graph.node(nodeId).orElse(null);
            if (node == null) {
                return false;
            }
            if (node.kind() == NodeKind.RETURN || node.kind() == NodeKind.PARAM) {
                return true;
            }
            return node.kind() == NodeKind.STATEMENT && node.operation() == InstructionCategory.RETURN;
        }

        private void tryAdd(int leftId, int rightId) {
            if (alignment.containsKey(leftId) || usedRight.contains(rightId)) {
                return;
            }
            if (claimedLeft.contains(leftId) || claimedRight.contains(rightId)) {
                return;
            }
            alignment.put(leftId, new AlignedPair(leftId, rightId, agreement(leftId, rightId)));
            usedRight.add(rightId);
            frontier.add(new int[]{leftId, rightId});
        }

        /** Necessary condition to align two nodes: same name-free operation token + kind. */
        private boolean compatible(int leftId, int rightId) {
            NodeDescriptor left = leftDesc.get(leftId);
            NodeDescriptor right = rightDesc.get(rightId);
            return left != null && right != null && left.baseLabel().equals(right.baseLabel());
        }

        private double agreement(int leftId, int rightId) {
            NodeDescriptor left = leftDesc.get(leftId);
            NodeDescriptor right = rightDesc.get(rightId);
            if (left == null || right == null) {
                return 0.0;
            }
            if (left.semanticValueHash() != 0L && left.semanticValueHash() == right.semanticValueHash()) {
                return 1.0;
            }
            if (left.finalWlHash() == right.finalWlHash()) {
                return 0.8;
            }
            return 0.5;
        }
    }
}
