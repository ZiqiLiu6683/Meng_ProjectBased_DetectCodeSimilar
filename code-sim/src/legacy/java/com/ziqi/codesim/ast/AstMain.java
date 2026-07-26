package com.ziqi.codesim.ast;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

import com.ziqi.codesim.io.FileUtils;
import com.ziqi.codesim.fingerprint.Winnowing;
import com.ziqi.codesim.sim.Similarity;
import com.github.javaparser.ast.CompilationUnit;

public class AstMain {

    // S1: Winnowing parameters for non-method-body class-level tokens.
    // Slightly larger k than S3 because class-level sequences (import lists,
    // field lists) are longer and coarser fingerprints are appropriate.
    private static final int K_FILE = 6;
    private static final int W_FILE = 5;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: mvn -q exec:java " +
                "-Dexec.mainClass=\"com.ziqi.codesim.ast.AstMain\" " +
                "-Dexec.args=\"A.java B.java\"");
            return;
        }

        String srcA = FileUtils.readAll(args[0]);
        String srcB = FileUtils.readAll(args[1]);

        CompilationUnit cuA = AstTokenizer.parse(srcA);
        CompilationUnit cuB = AstTokenizer.parse(srcB);

        // S1: non-method-body class-level context.
        List<String> nmTokensA = AstTokenizer.tokenizeNonMethod(cuA);
        List<String> nmTokensB = AstTokenizer.tokenizeNonMethod(cuB);
        List<Winnowing.Fingerprint> fpA = Winnowing.fingerprintTokens(nmTokensA, K_FILE, W_FILE);
        List<Winnowing.Fingerprint> fpB = Winnowing.fingerprintTokens(nmTokensB, K_FILE, W_FILE);
        double simS1 = Similarity.jaccard(fpA, fpB);

        // S3 and S2: method-level token and exact-subtree signals.
        MethodLevelSimilarity.Result mlResult = MethodLevelSimilarity.compute(cuA, cuB);
        double simS3 = mlResult.similarity;

        // S4: method-level TED/APTED structural similarity.
        AptedSimilarity.Result aptedResult = AptedSimilarity.compute(cuA, cuB);
        double simS4 = aptedResult.similarity;

        // S5: file-level API call vocabulary signal.
        Set<String> apiCallsA = ApiCallSimilarity.extractApiCallSet(cuA);
        Set<String> apiCallsB = ApiCallSimilarity.extractApiCallSet(cuB);
        double simS5 = ApiCallSimilarity.compute(cuA, cuB);
        boolean s5Applicable = (simS5 != ApiCallSimilarity.NOT_APPLICABLE);

        System.out.println("=== AST Similarity Report ===");
        System.out.printf("File A : %s%n", args[0]);
        System.out.printf("File B : %s%n", args[1]);
        System.out.println();

        System.out.println("-- S1: Non-method-body Token Winnowing (class-level context) --");
        System.out.printf("  Non-method tokens : A=%d, B=%d%n", nmTokensA.size(), nmTokensB.size());
        System.out.printf("  Fingerprints      : A=%d, B=%d%n", fpA.size(), fpB.size());
        if (nmTokensA.isEmpty() && nmTokensB.isEmpty()) {
            System.out.println("  Note: both files are single-method fragments (BCB mode); S1=0 is expected.");
        }
        System.out.printf("  S1 Similarity     : %.2f%%%n%n", simS1 * 100);

        System.out.println("-- S3: Method-level Token Winnowing (+ S2 exact subtree per pair) --");
        System.out.println("  Forward (A -> B):");
        for (MethodLevelSimilarity.MatchRecord r : mlResult.forwardMatches) {
            System.out.printf("    %-20s -> %-20s  S3=%.2f%%  S2=%.2f%%%n",
                    r.methodA + "()", r.methodB + "()",
                    r.similarity * 100, r.s2 * 100);
        }
        System.out.println("  Backward (B -> A):");
        for (MethodLevelSimilarity.MatchRecord r : mlResult.backwardMatches) {
            System.out.printf("    %-20s -> %-20s  S3=%.2f%%  S2=%.2f%%%n",
                    r.methodA + "()", r.methodB + "()",
                    r.similarity * 100, r.s2 * 100);
        }
        System.out.printf("  S3 Similarity     : %.2f%%%n%n", simS3 * 100);

        System.out.println("-- S4: Method-level TED/APTED (global structural) --");
        List<AptedSimilarity.MethodTreeInfo> methodsB = AptedSimilarity.extractMethods(cuB);
        List<AptedSimilarity.MethodTreeInfo> methodsA = AptedSimilarity.extractMethods(cuA);
        System.out.println("  Forward (A -> B):");
        for (AptedSimilarity.MatchRecord r : aptedResult.forwardMatches) {
            int sizeB = methodsB.stream()
                .filter(m -> m.name.equals(r.methodB))
                .mapToInt(m -> m.treeSize).findFirst().orElse(-1);
            System.out.printf("    %-20s -> %-20s  S4=%.2f%%  TED=%d  sizes=(%d,%d)%n",
                    r.methodA + "()", r.methodB + "()",
                    r.similarity * 100, r.tedDist, r.sizeA, sizeB);
        }
        System.out.println("  Backward (B -> A):");
        for (AptedSimilarity.MatchRecord r : aptedResult.backwardMatches) {
            int sizeA = methodsA.stream()
                .filter(m -> m.name.equals(r.methodB))
                .mapToInt(m -> m.treeSize).findFirst().orElse(-1);
            System.out.printf("    %-20s -> %-20s  S4=%.2f%%  TED=%d  sizes=(%d,%d)%n",
                    r.methodA + "()", r.methodB + "()",
                    r.similarity * 100, r.tedDist, r.sizeA, sizeA);
        }
        System.out.printf("  S4 Similarity     : %.2f%%%n%n", simS4 * 100);

        System.out.println("-- S5: API Call Vocabulary Jaccard (weak semantic signal, CLAN-inspired) --");
        System.out.printf("  API calls : A=%d unique, B=%d unique%n",
                apiCallsA.size(), apiCallsB.size());
        if (s5Applicable) {
            Set<String> apiIntersect = new HashSet<>(apiCallsA);
            apiIntersect.retainAll(apiCallsB);
            System.out.printf("  Shared    : %d external API names%n", apiIntersect.size());
            System.out.printf("  S5 Similarity     : %.2f%%  [weak semantic signal]%n%n", simS5 * 100);
        } else {
            System.out.println("  S5 Similarity     : NOT_APPLICABLE (no external API calls in either file)");
            System.out.println("  (excluded from combined score -- no signal, not zero similarity)");
            System.out.println();
        }

        // structural_exactness helps separate Type-2-like and Type-3-like signals.
        double structExactNum = 0.0, structExactDen = 0.0;
        for (MethodLevelSimilarity.MatchRecord r : mlResult.forwardMatches) {
            double s4ForPair = aptedResult.forwardMatches.stream()
                .filter(a -> a.methodA.equals(r.methodA))
                .mapToDouble(a -> a.similarity)
                .findFirst().orElse(0.0);
            double denom = s4ForPair + 1e-9;
            structExactNum += (r.s2 / denom) * r.weightA;
            structExactDen += r.weightA;
        }
        double structuralExactness = (structExactDen > 0) ? structExactNum / structExactDen : 0.0;

        // S2 remains diagnostic; S4 carries the structural layer score.
        double tokenLayer      = (simS1 + simS3) / 2.0;
        double structuralLayer = simS4;
        double combined;
        String combinedLabel;
        if (s5Applicable) {
            combined      = (tokenLayer + structuralLayer + simS5) / 3.0;
            combinedLabel = "3-layer: (S1+S3)/2, S4, S5";
        } else {
            combined      = (tokenLayer + structuralLayer) / 2.0;
            combinedLabel = "2-layer: (S1+S3)/2, S4";
        }

        System.out.println("=== Summary ===");
        System.out.printf("  S1  Non-method-body Winnowing  : %.2f%%%n", simS1 * 100);
        System.out.printf("  S3  Method-level Winnowing     : %.2f%%%n", simS3 * 100);
        System.out.printf("  S4  Method-level TED (APTED)   : %.2f%%%n", simS4 * 100);
        System.out.printf("  S5  API Call Vocabulary        : %s%n",
            s5Applicable ? String.format("%.2f%%  [weak semantic signal]", simS5 * 100)
                         : "NOT_APPLICABLE");
        System.out.println("  --- Diagnostic ---");
        System.out.printf("  structural_exactness (S2/S4)   : %.2f%%  %s%n",
            structuralExactness * 100,
            structuralExactness >= 0.8 ? "[-> Type-2 signal: rename-only]"
          : structuralExactness >= 0.4 ? "[-> Type-3 signal: structural changes]"
          : "[-> low structural overlap]");
        System.out.println("  --- Layers ---");
        System.out.printf("  Token layer      (S1+S3)/2     : %.2f%%%n", tokenLayer * 100);
        System.out.printf("  Structural layer S4            : %.2f%%%n", structuralLayer * 100);
        System.out.printf("  Semantic layer   S5            : %s%n",
            s5Applicable ? String.format("%.2f%%", simS5 * 100) : "N/A");
        System.out.printf("  Combined Score [%s]  : %.2f%%%n", combinedLabel, combined * 100);
    }
}
