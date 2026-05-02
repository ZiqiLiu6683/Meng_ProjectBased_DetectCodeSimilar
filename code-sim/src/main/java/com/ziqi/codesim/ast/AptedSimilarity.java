// Ziqi Liu Meng Project-Based Software Engineering
// APTED-style structural similarity: method-level TED-based best-match pairing.
//
// Improvement over exact subtree matching (Strategy 2):
//   - Exact subtree matching requires complete structural equality (binary 0/1).
//   - TED gives a continuous score: two trees that differ by only a few edits
//     (e.g. AssignExpr → ReturnStmt) still receive a high similarity score.
//
// Implementation note:
//   Uses the official APTED library (Pawlik & Augsten 2015/2016) via the
//   distance.APTED / node.Node<StringNodeData> / costmodel.StringUnitCostModel API.
//   The library must be installed to the local Maven repo:
//     mvn install:install-file -Dfile=apted-1.0.jar \
//       -DgroupId=com.github.DatabaseGroup -DartifactId=apted \
//       -Dversion=1.0 -Dpackaging=jar
//
// Pipeline:
//   1. Extract every MethodDeclaration from both CompilationUnits.
//   2. Convert each method's AST subtree to a Node<StringNodeData> tree
//      (same label normalisation as SubtreeHasher for consistency).
//   3. Compute pairwise APTED edit distance for every method pair.
//   4. Symmetric best-match pairing (forward A→B + backward B→A), weighted by
//      method tree size, identical to MethodLevelSimilarity's aggregation strategy.
package com.ziqi.codesim.ast;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.PrimitiveType;

import eu.mihosoft.ext.apted.distance.APTED;
import eu.mihosoft.ext.apted.node.StringNodeData;
import eu.mihosoft.ext.apted.costmodel.StringUnitCostModel;

import java.util.ArrayList;
import java.util.List;

public class AptedSimilarity {

    // -----------------------------------------------------------------------
    // Public data structures
    // -----------------------------------------------------------------------

    /** One extracted method together with its APTED node tree. */
    public static class MethodTreeInfo {
        public final String name;
        public final eu.mihosoft.ext.apted.node.Node<StringNodeData> tree;
        public final int treeSize;

        MethodTreeInfo(String name, eu.mihosoft.ext.apted.node.Node<StringNodeData> tree, int size) {
            this.name     = name;
            this.tree     = tree;
            this.treeSize = size;
        }
    }

    /** One row of the best-match table. */
    public static class MatchRecord {
        public final String methodA;
        public final String methodB;   // best match found in the other file
        public final double similarity;
        public final int    tedDist;   // raw tree edit distance (for reporting)
        public final int    sizeA;     // tree size of methodA (used as weight)

        MatchRecord(String a, String b, double sim, int dist, int size) {
            this.methodA    = a;
            this.methodB    = b;
            this.similarity = sim;
            this.tedDist    = dist;
            this.sizeA      = size;
        }
    }

    /** Full result returned to the caller. */
    public static class Result {
        public final double            similarity;
        public final List<MatchRecord> forwardMatches;
        public final List<MatchRecord> backwardMatches;

        Result(double sim, List<MatchRecord> fwd, List<MatchRecord> bwd) {
            this.similarity      = sim;
            this.forwardMatches  = fwd;
            this.backwardMatches = bwd;
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Extract all MethodDeclarations from {@code cu} and convert each to an
     * APTED Node<StringNodeData> tree ready for distance computation.
     */
    public static List<MethodTreeInfo> extractMethods(CompilationUnit cu) {
        List<MethodTreeInfo> methods = new ArrayList<>();
        cu.findAll(MethodDeclaration.class).forEach(md -> {
            eu.mihosoft.ext.apted.node.Node<StringNodeData> tree = toAptedNode(md);
            int size = countNodes(tree);
            methods.add(new MethodTreeInfo(md.getNameAsString(), tree, size));
        });
        return methods;
    }

    /**
     * Compute method-level APTED-based similarity between two compilation units.
     *
     * @return Result with final score and per-method match details
     */
    public static Result compute(CompilationUnit cuA, CompilationUnit cuB) {
        List<MethodTreeInfo> methodsA = extractMethods(cuA);
        List<MethodTreeInfo> methodsB = extractMethods(cuB);

        if (methodsA.isEmpty() && methodsB.isEmpty()) {
            return new Result(1.0, new ArrayList<>(), new ArrayList<>());
        }
        if (methodsA.isEmpty() || methodsB.isEmpty()) {
            return new Result(0.0, new ArrayList<>(), new ArrayList<>());
        }

        List<MatchRecord> fwd = bestMatchPass(methodsA, methodsB);
        List<MatchRecord> bwd = bestMatchPass(methodsB, methodsA);

        double fwdScore = weightedAverage(fwd);
        double bwdScore = weightedAverage(bwd);

        return new Result((fwdScore + bwdScore) / 2.0, fwd, bwd);
    }

    // -----------------------------------------------------------------------
    // JavaParser → APTED Node<StringNodeData> conversion
    // -----------------------------------------------------------------------

    /**
     * Recursively convert a JavaParser AST node to an APTED Node<StringNodeData>.
     * Uses the same label normalisation as SubtreeHasher for consistency:
     *   - Leaf value nodes (literals, names) → abstract category token
     *   - Structural nodes → class simple name (e.g. "IfStmt", "ForStmt")
     */
    public static eu.mihosoft.ext.apted.node.Node<StringNodeData> toAptedNode(Node javaParserNode) {
        eu.mihosoft.ext.apted.node.Node<StringNodeData> aptedNode =
            new eu.mihosoft.ext.apted.node.Node<>(new StringNodeData(normalizeLabel(javaParserNode)));
        for (Node child : javaParserNode.getChildNodes()) {
            aptedNode.addChild(toAptedNode(child));
        }
        return aptedNode;
    }

    private static String normalizeLabel(Node n) {
        // Literals
        if (n instanceof StringLiteralExpr)  return "STR";
        if (n instanceof IntegerLiteralExpr) return "NUM";
        if (n instanceof LongLiteralExpr)    return "NUM";
        if (n instanceof DoubleLiteralExpr)  return "NUM";
        if (n instanceof CharLiteralExpr)    return "CHR";
        if (n instanceof BooleanLiteralExpr) return "BOOL";
        if (n instanceof NullLiteralExpr)    return "NULL";
        // Names
        if (n instanceof SimpleName) return "ID";
        if (n instanceof NameExpr)   return "ID";
        // Self-references
        if (n instanceof ThisExpr)   return "THIS";
        if (n instanceof SuperExpr)  return "SUPER";
        // Primitive types: keep the type name so that int↔boolean is detected
        if (n instanceof PrimitiveType) return "T:" + n.toString();
        // All other structural nodes: use the AST class name
        return n.getClass().getSimpleName();
    }

    // -----------------------------------------------------------------------
    // Best-match pairing
    // -----------------------------------------------------------------------

    private static List<MatchRecord> bestMatchPass(List<MethodTreeInfo> from,
                                                    List<MethodTreeInfo> to) {
        List<MatchRecord> records = new ArrayList<>();
        for (MethodTreeInfo mA : from) {
            double bestSim  = -1.0;
            int    bestDist = Integer.MAX_VALUE;
            String bestName = "(none)";

            for (MethodTreeInfo mB : to) {
                int dist = computeAptedDist(mA.tree, mB.tree);
                int maxSize = Math.max(mA.treeSize, mB.treeSize);
                double sim = (maxSize == 0) ? 1.0
                    : Math.max(0.0, 1.0 - (double) dist / maxSize);
                if (sim > bestSim) {
                    bestSim  = sim;
                    bestDist = dist;
                    bestName = mB.name;
                }
            }
            records.add(new MatchRecord(
                mA.name, bestName,
                Math.max(0.0, bestSim),
                bestDist,
                mA.treeSize
            ));
        }
        return records;
    }

    /**
     * Compute APTED tree edit distance between two APTED node trees.
     * Creates a fresh APTED instance per call (the library is not thread-safe
     * across calls on the same instance).
     */
    private static int computeAptedDist(eu.mihosoft.ext.apted.node.Node<StringNodeData> t1,
                                         eu.mihosoft.ext.apted.node.Node<StringNodeData> t2) {
        APTED<StringUnitCostModel, StringNodeData> apted =
            new APTED<>(new StringUnitCostModel());
        float dist = apted.computeEditDistance(t1, t2);
        return Math.round(dist);
    }

    private static double weightedAverage(List<MatchRecord> records) {
        double totalWeight = 0, weightedSum = 0;
        for (MatchRecord r : records) {
            weightedSum  += r.similarity * r.sizeA;
            totalWeight  += r.sizeA;
        }
        return totalWeight == 0 ? 0.0 : weightedSum / totalWeight;
    }

    // -----------------------------------------------------------------------
    // Utility: count nodes in an APTED tree
    // -----------------------------------------------------------------------

    private static int countNodes(eu.mihosoft.ext.apted.node.Node<StringNodeData> n) {
        int count = 1;
        for (eu.mihosoft.ext.apted.node.Node<StringNodeData> child : n.getChildren()) {
            count += countNodes(child);
        }
        return count;
    }
}
