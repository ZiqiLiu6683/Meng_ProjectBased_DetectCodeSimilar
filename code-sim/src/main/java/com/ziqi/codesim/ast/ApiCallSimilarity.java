// Ziqi Liu Meng Project-Based Software Engineering
// API call sequence similarity (Strategy 5).
//
// Inspired by CLAN (McMillan et al. 2012), which uses API call sequences as
// semantic anchors for cross-project similarity detection.  We adopt a
// linearised, lightweight variant suited to clone detection:
//
//   - For each MethodDeclaration, extract EXTERNAL MethodCallExpr nodes only.
//     Calls to methods defined within the same CompilationUnit are filtered out
//     because internal calls are implementation details already handled by
//     S3 (method-level Winnowing) and S4 (method-level TED).
//     Calls are collected in AST traversal order (≈ source-code order).
//     Only the callee name is kept; scope is discarded for robustness to
//     variable renaming (e.g. list.add == myList.add == "add").
//   - Fingerprint each sequence with Winnowing (k=2, w=2).
//     Fallback: if the sequence is shorter than k, use unigram Winnowing
//     so that even single-call methods get a meaningful score.
//   - Bidirectional best-match pairing and size-weighted aggregation,
//     identical to MethodLevelSimilarity and AptedSimilarity.
//
// Division of labour with S3/S4:
//   Internal calls (step, helper, compute…) → S3/S4 handle via method bodies
//   External calls (Collections.sort, list.add, Files.read…) → S5 only
//
// What this captures that S1-S4 miss:
//   Two methods may differ in token structure (different loops, variable names)
//   yet invoke the same external API calls in the same order.  S5 surfaces
//   this "behavioural fingerprint" independently of syntax.
package com.ziqi.codesim.ast;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.ziqi.codesim.fingerprint.Winnowing;
import com.ziqi.codesim.sim.Similarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ApiCallSimilarity {

    // 2-gram of consecutive external call names; window = 2.
    private static final int K = 2;
    private static final int W = 2;

    // -----------------------------------------------------------------------
    // Public data structures
    // -----------------------------------------------------------------------

    /** One extracted method with its external call sequence and fingerprints. */
    public static class MethodCallInfo {
        public final String                      name;
        public final List<String>                callSequence;  // external calls only
        public final List<Winnowing.Fingerprint> fingerprints;

        MethodCallInfo(String name,
                       List<String> seq,
                       List<Winnowing.Fingerprint> fps) {
            this.name         = name;
            this.callSequence = seq;
            this.fingerprints = fps;
        }

        /** Weight used in aggregation = number of external API calls. */
        public int size() { return callSequence.size(); }
    }

    /** One row of the best-match table. */
    public static class MatchRecord {
        public final String methodA;
        public final String methodB;
        public final double similarity;
        public final int    weightA;

        MatchRecord(String a, String b, double sim, int w) {
            this.methodA    = a;
            this.methodB    = b;
            this.similarity = sim;
            this.weightA    = w;
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
     * Extract all MethodDeclarations from {@code cu} and build
     * MethodCallInfo records (external call sequence + fingerprints).
     */
    public static List<MethodCallInfo> extractMethods(CompilationUnit cu) {
        // Collect all method names defined in this file — these are "internal"
        Set<String> internalNames = cu.findAll(MethodDeclaration.class).stream()
            .map(MethodDeclaration::getNameAsString)
            .collect(Collectors.toSet());

        List<MethodCallInfo> methods = new ArrayList<>();
        cu.findAll(MethodDeclaration.class).forEach(md -> {
            List<String> seq = extractExternalCallSequence(md, internalNames);
            List<Winnowing.Fingerprint> fps = fingerprintSequence(seq);
            methods.add(new MethodCallInfo(md.getNameAsString(), seq, fps));
        });
        return methods;
    }

    /**
     * Compute API-call-sequence similarity between two compilation units.
     * Methods whose external call sequences are both empty score 1.0
     * (structurally equivalent in terms of API usage).
     */
    public static Result compute(CompilationUnit cuA, CompilationUnit cuB) {
        List<MethodCallInfo> methodsA = extractMethods(cuA);
        List<MethodCallInfo> methodsB = extractMethods(cuB);

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
    // External call-sequence extraction
    // -----------------------------------------------------------------------

    /**
     * Collect MethodCallExpr nodes inside {@code md} in AST traversal order,
     * keeping only calls whose name is NOT in {@code internalNames}.
     * Only the callee name is kept (scope discarded).
     */
    static List<String> extractExternalCallSequence(MethodDeclaration md,
                                                     Set<String> internalNames) {
        List<String> seq = new ArrayList<>();
        md.findAll(MethodCallExpr.class).forEach(call -> {
            String name = call.getNameAsString();
            if (!internalNames.contains(name)) {
                seq.add(name);
            }
        });
        return seq;
    }

    // -----------------------------------------------------------------------
    // Fingerprinting with short-sequence fallback
    // -----------------------------------------------------------------------

    /**
     * Fingerprint a call sequence with Winnowing(K, W).
     * Falls back to unigram Winnowing(1,1) when the sequence is shorter than K,
     * so that even a single external call produces a non-empty fingerprint set.
     */
    static List<Winnowing.Fingerprint> fingerprintSequence(List<String> seq) {
        if (seq.isEmpty()) return new ArrayList<>();
        if (seq.size() < K) return Winnowing.fingerprintTokens(seq, 1, 1);
        return Winnowing.fingerprintTokens(seq, K, W);
    }

    // -----------------------------------------------------------------------
    // Best-match pairing
    // -----------------------------------------------------------------------

    private static List<MatchRecord> bestMatchPass(List<MethodCallInfo> from,
                                                    List<MethodCallInfo> to) {
        List<MatchRecord> records = new ArrayList<>();
        for (MethodCallInfo mA : from) {

            // Methods with no external calls carry no S5 signal.
            // Weight = 0 so they don't affect the weighted average.
            if (mA.callSequence.isEmpty()) {
                records.add(new MatchRecord(mA.name, "(no ext. calls)", 0.0, 0));
                continue;
            }

            double bestSim  = 0.0;
            String bestName = "(none)";
            for (MethodCallInfo mB : to) {
                if (mB.callSequence.isEmpty()) continue;
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

    private static double weightedAverage(List<MatchRecord> records) {
        double totalWeight = 0, weightedSum = 0;
        for (MatchRecord r : records) {
            weightedSum += r.similarity * r.weightA;
            totalWeight += r.weightA;
        }
        return totalWeight == 0 ? 0.0 : weightedSum / totalWeight;
    }
}
