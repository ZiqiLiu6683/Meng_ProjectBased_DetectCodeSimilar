package com.ziqi.codesim.ast;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.ziqi.codesim.fingerprint.Winnowing;
import com.ziqi.codesim.sim.Similarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MethodLevelSimilarity {

    private static final int K = 4;
    private static final int W = 3;
    private static final int MIN_SUBTREE_SIZE = 3;

    public static class MethodInfo {
        public final String name;
        public final List<String> tokens;
        public final List<Winnowing.Fingerprint> fingerprints;
        public final Set<Long> subtreeHashes;

        MethodInfo(String name, List<String> tokens,
                   List<Winnowing.Fingerprint> fps, Set<Long> subtreeHashes) {
            this.name = name;
            this.tokens = tokens;
            this.fingerprints = fps;
            this.subtreeHashes = subtreeHashes;
        }

        public int size() {
            return tokens.size();
        }
    }

    public static class MatchRecord {
        public final String methodA;
        public final String methodB;
        public final double similarity;
        public final double s2;
        public final int weightA;

        MatchRecord(String a, String b, double sim, double s2, int w) {
            this.methodA = a;
            this.methodB = b;
            this.similarity = sim;
            this.s2 = s2;
            this.weightA = w;
        }
    }

    public static class Result {
        public final double similarity;
        public final List<MatchRecord> forwardMatches;
        public final List<MatchRecord> backwardMatches;

        Result(double sim, List<MatchRecord> fwd, List<MatchRecord> bwd) {
            this.similarity = sim;
            this.forwardMatches = fwd;
            this.backwardMatches = bwd;
        }
    }

    public static List<MethodInfo> extractMethods(CompilationUnit cu) {
        List<MethodInfo> methods = new ArrayList<>();
        cu.findAll(MethodDeclaration.class).forEach(md -> {
            List<String> tokens = AstTokenizer.tokenize(md);
            List<Winnowing.Fingerprint> fps = Winnowing.fingerprintTokens(tokens, K, W);
            Set<Long> subtreeHashes = SubtreeHasher.extractSubtreeHashes(md, MIN_SUBTREE_SIZE);
            methods.add(new MethodInfo(md.getNameAsString(), tokens, fps, subtreeHashes));
        });
        return methods;
    }

    public static Result compute(CompilationUnit cuA, CompilationUnit cuB) {
        List<MethodInfo> methodsA = extractMethods(cuA);
        List<MethodInfo> methodsB = extractMethods(cuB);

        if (methodsA.isEmpty() && methodsB.isEmpty()) {
            return new Result(1.0, new ArrayList<>(), new ArrayList<>());
        }
        if (methodsA.isEmpty() || methodsB.isEmpty()) {
            return new Result(0.0, new ArrayList<>(), new ArrayList<>());
        }

        List<MatchRecord> fwd = bestMatchPass(methodsA, methodsB);
        List<MatchRecord> bwd = bestMatchPass(methodsB, methodsA);
        return new Result((weightedAverage(fwd) + weightedAverage(bwd)) / 2.0, fwd, bwd);
    }

    private static List<MatchRecord> bestMatchPass(List<MethodInfo> from,
                                                   List<MethodInfo> to) {
        List<MatchRecord> records = new ArrayList<>();
        for (MethodInfo mA : from) {
            double bestSim = 0.0;
            double bestS2 = 0.0;
            String bestName = "(none)";
            for (MethodInfo mB : to) {
                double sim = Similarity.jaccard(mA.fingerprints, mB.fingerprints);
                if (sim > bestSim) {
                    bestSim = sim;
                    bestName = mB.name;
                    bestS2 = Similarity.jaccard(mA.subtreeHashes, mB.subtreeHashes);
                }
            }
            records.add(new MatchRecord(mA.name, bestName, bestSim, bestS2, mA.size()));
        }
        return records;
    }

    private static double weightedAverage(List<MatchRecord> records) {
        double totalWeight = 0;
        double weightedSum = 0;
        for (MatchRecord r : records) {
            weightedSum += r.similarity * r.weightA;
            totalWeight += r.weightA;
        }
        return totalWeight == 0 ? 0.0 : weightedSum / totalWeight;
    }
}
