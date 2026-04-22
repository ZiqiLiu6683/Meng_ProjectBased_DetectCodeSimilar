// Ziqi Liu Meng Project-Based Software Engineering
// Method-level structural similarity.
//
// Improvement over whole-file subtree matching: instead of comparing two large
// ASTs as a single unit, we extract each MethodDeclaration as an independent
// subtree, fingerprint it with Winnowing, and then perform best-match pairing
// between the method sets of the two files.
//
// This relaxes the "entire-file structure must align" constraint.  A helper
// method extracted from an inline block (Type-3 clone, e.g. A1 → A2) will
// still find a high-scoring counterpart in the other file because the method
// bodies share significant token sub-sequences.
//
// Aggregation strategy: symmetric best-match (forward A→B + backward B→A),
// each direction weighted by method token count so that larger methods
// contribute proportionally more to the final score.
package com.ziqi.codesim.ast;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.ziqi.codesim.fingerprint.Winnowing;
import com.ziqi.codesim.sim.Similarity;

import java.util.ArrayList;
import java.util.List;

public class MethodLevelSimilarity {

    // Smaller k/w than the file-level pipeline: methods are shorter, so we
    // need a finer granularity to avoid too-sparse fingerprint sets.
    private static final int K = 4;
    private static final int W = 3;

    // -----------------------------------------------------------------------
    // Public data structures
    // -----------------------------------------------------------------------

    /** Lightweight record for one extracted method. */
    public static class MethodInfo {
        public final String name;
        public final List<String> tokens;
        public final List<Winnowing.Fingerprint> fingerprints;

        MethodInfo(String name, List<String> tokens, List<Winnowing.Fingerprint> fps) {
            this.name         = name;
            this.tokens       = tokens;
            this.fingerprints = fps;
        }

        /** Token count used as weight in the aggregation step. */
        public int size() { return tokens.size(); }
    }

    /** One row of the best-match table (printed in AstMain). */
    public static class MatchRecord {
        public final String methodA;
        public final String methodB;   // best match in the other file
        public final double similarity;
        public final int    weightA;   // token count of methodA

        MatchRecord(String a, String b, double sim, int w) {
            this.methodA    = a;
            this.methodB    = b;
            this.similarity = sim;
            this.weightA    = w;
        }
    }

    /** Full result returned to the caller. */
    public static class Result {
        /** Final symmetric similarity score (0.0 – 1.0). */
        public final double similarity;
        /** Forward match records: each method in A → best method in B. */
        public final List<MatchRecord> forwardMatches;
        /** Backward match records: each method in B → best method in A. */
        public final List<MatchRecord> backwardMatches;

        Result(double sim,
               List<MatchRecord> fwd,
               List<MatchRecord> bwd) {
            this.similarity      = sim;
            this.forwardMatches  = fwd;
            this.backwardMatches = bwd;
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /*
     * Extract all MethodDeclarations from a CompilationUnit and build
     * MethodInfo records (tokens + Winnowing fingerprints).
     */
    public static List<MethodInfo> extractMethods(CompilationUnit cu) {
        List<MethodInfo> methods = new ArrayList<>();
        cu.findAll(MethodDeclaration.class).forEach(md -> {
            List<String> tokens = AstTokenizer.tokenize(md);
            List<Winnowing.Fingerprint> fps = Winnowing.fingerprintTokens(tokens, K, W);
            methods.add(new MethodInfo(md.getNameAsString(), tokens, fps));
        });
        return methods;
    }

    /**
     * Compute method-level similarity between two compilation units.
     *
     * @return Result containing the final score and the match tables
     */
    public static Result compute(CompilationUnit cuA, CompilationUnit cuB) {
        List<MethodInfo> methodsA = extractMethods(cuA);
        List<MethodInfo> methodsB = extractMethods(cuB);

        // Edge cases
        if (methodsA.isEmpty() && methodsB.isEmpty()) {
            return new Result(1.0, new ArrayList<>(), new ArrayList<>());
        }
        if (methodsA.isEmpty() || methodsB.isEmpty()) {
            return new Result(0.0, new ArrayList<>(), new ArrayList<>());
        }

        // Forward pass: each method in A finds its best match in B
        List<MatchRecord> fwd = bestMatchPass(methodsA, methodsB);
        double fwdScore = weightedAverage(fwd);

        // Backward pass: each method in B finds its best match in A
        List<MatchRecord> bwd = bestMatchPass(methodsB, methodsA);
        double bwdScore = weightedAverage(bwd);

        // Symmetric score: average of both directions
        double finalScore = (fwdScore + bwdScore) / 2.0;
        return new Result(finalScore, fwd, bwd);
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * For every method in {@code from}, find the method in {@code to} with
     * the highest Jaccard fingerprint similarity and record the match.
     */
    private static List<MatchRecord> bestMatchPass(List<MethodInfo> from,
                                                    List<MethodInfo> to) {
        List<MatchRecord> records = new ArrayList<>();
        for (MethodInfo mA : from) {
            double bestSim  = 0.0;
            String bestName = "(none)";
            for (MethodInfo mB : to) {
                double sim = Similarity.jaccard(mA.fingerprints, mB.fingerprints);
                if (sim > bestSim) {
                    bestSim  = sim;
                    bestName = mB.name;
                }
            }
            records.add(new MatchRecord(mA.name, bestName, bestSim, mA.size()));
        }
        return records;
    }

    /** Weighted average of match similarities, weighted by method token count. */
    private static double weightedAverage(List<MatchRecord> records) {
        double totalWeight  = 0;
        double weightedSum  = 0;
        for (MatchRecord r : records) {
            weightedSum  += r.similarity * r.weightA;
            totalWeight  += r.weightA;
        }
        return totalWeight == 0 ? 0.0 : weightedSum / totalWeight;
    }
}
