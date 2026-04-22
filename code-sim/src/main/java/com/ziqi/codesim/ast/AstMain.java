// Ziqi Liu Meng Project-Based Software Engineering
// AST-based similarity detection entry point.
// Runs four complementary structural strategies:
//   1. AST-token Winnowing   – serialise AST to token sequence, then Winnow
//   2. Exact Subtree Match   – canonical subtree hashes, Jaccard set overlap
//   3. Method-level Winnow   – per-method token Winnowing + best-match pairing
//   4. Method-level TED      – per-method Tree Edit Distance + best-match pairing
package com.ziqi.codesim.ast;

import java.util.List;
import java.util.Set;

import com.ziqi.codesim.io.FileUtils;
import com.ziqi.codesim.fingerprint.Winnowing;
import com.ziqi.codesim.sim.Similarity;
import com.github.javaparser.ast.CompilationUnit;

public class AstMain {

    // Parameters for Strategy 1 (file-level token Winnowing)
    private static final int K_FILE = 6;
    private static final int W_FILE = 5;

    // Minimum subtree size for Strategy 2
    private static final int MIN_SUBTREE_SIZE = 3;

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

        // ================================================================
        // Strategy 1 – AST-token Winnowing (file level)
        // ================================================================
        List<String> tokensA = AstTokenizer.tokenize(cuA);
        List<String> tokensB = AstTokenizer.tokenize(cuB);
        List<Winnowing.Fingerprint> fpA = Winnowing.fingerprintTokens(tokensA, K_FILE, W_FILE);
        List<Winnowing.Fingerprint> fpB = Winnowing.fingerprintTokens(tokensB, K_FILE, W_FILE);
        double simS1 = Similarity.jaccard(fpA, fpB);

        // ================================================================
        // Strategy 2 – Exact Subtree Matching
        // ================================================================
        Set<Long> subtreesA = SubtreeHasher.extractSubtreeHashes(cuA, MIN_SUBTREE_SIZE);
        Set<Long> subtreesB = SubtreeHasher.extractSubtreeHashes(cuB, MIN_SUBTREE_SIZE);
        double simS2 = Similarity.jaccard(subtreesA, subtreesB);

        // ================================================================
        // Strategy 3 – Method-level Token Winnowing + Best-match
        // ================================================================
        MethodLevelSimilarity.Result mlResult = MethodLevelSimilarity.compute(cuA, cuB);
        double simS3 = mlResult.similarity;

        // ================================================================
        // Strategy 4 – Method-level TED (APTED-style) + Best-match
        // ================================================================
        AptedSimilarity.Result aptedResult = AptedSimilarity.compute(cuA, cuB);
        double simS4 = aptedResult.similarity;

        // ================================================================
        // Output
        // ================================================================
        System.out.println("=== AST Similarity Report ===");
        System.out.printf("File A : %s%n", args[0]);
        System.out.printf("File B : %s%n", args[1]);
        System.out.println();

        // --- Strategy 1 ---
        System.out.println("-- Strategy 1: AST-token Winnowing (file level) --");
        System.out.printf("  AST tokens  : A=%d, B=%d%n", tokensA.size(), tokensB.size());
        System.out.printf("  Fingerprints: A=%d, B=%d%n", fpA.size(), fpB.size());
        System.out.printf("  Similarity  : %.2f%%%n%n", simS1 * 100);

        // --- Strategy 2 ---
        System.out.println("-- Strategy 2: Exact Subtree Matching --");
        System.out.printf("  Subtrees (size>=%d): A=%d, B=%d%n",
                MIN_SUBTREE_SIZE, subtreesA.size(), subtreesB.size());
        System.out.printf("  Similarity  : %.2f%%%n%n", simS2 * 100);

        // --- Strategy 3 ---
        System.out.println("-- Strategy 3: Method-level Token Winnowing --");
        System.out.println("  Forward (A → B):");
        for (MethodLevelSimilarity.MatchRecord r : mlResult.forwardMatches) {
            System.out.printf("    %-20s → %-20s  %.2f%%%n",
                    r.methodA + "()", r.methodB + "()", r.similarity * 100);
        }
        System.out.println("  Backward (B → A):");
        for (MethodLevelSimilarity.MatchRecord r : mlResult.backwardMatches) {
            System.out.printf("    %-20s → %-20s  %.2f%%%n",
                    r.methodA + "()", r.methodB + "()", r.similarity * 100);
        }
        System.out.printf("  Similarity  : %.2f%%%n%n", simS3 * 100);

        // --- Strategy 4 ---
        System.out.println("-- Strategy 4: Method-level TED (APTED-style) --");
        System.out.println("  Forward (A → B):");
        for (AptedSimilarity.MatchRecord r : aptedResult.forwardMatches) {
            System.out.printf("    %-20s → %-20s  sim=%.2f%%  TED=%d  sizes=(%d,%d)%n",
                    r.methodA + "()", r.methodB + "()",
                    r.similarity * 100, r.tedDist, r.sizeA,
                    // find matching tree size from B
                    AptedSimilarity.extractMethods(cuB).stream()
                        .filter(m -> m.name.equals(r.methodB))
                        .mapToInt(m -> m.treeSize).findFirst().orElse(-1));
        }
        System.out.println("  Backward (B → A):");
        for (AptedSimilarity.MatchRecord r : aptedResult.backwardMatches) {
            System.out.printf("    %-20s → %-20s  sim=%.2f%%  TED=%d  sizes=(%d,%d)%n",
                    r.methodA + "()", r.methodB + "()",
                    r.similarity * 100, r.tedDist, r.sizeA,
                    AptedSimilarity.extractMethods(cuA).stream()
                        .filter(m -> m.name.equals(r.methodB))
                        .mapToInt(m -> m.treeSize).findFirst().orElse(-1));
        }
        System.out.printf("  Similarity  : %.2f%%%n%n", simS4 * 100);

        // ================================================================
        // Summary
        // ================================================================
        System.out.println("=== Summary ===");
        System.out.printf("  S1 AST-token Winnowing (file)  : %.2f%%%n", simS1 * 100);
        System.out.printf("  S2 Exact Subtree Matching       : %.2f%%%n", simS2 * 100);
        System.out.printf("  S3 Method-level Winnowing       : %.2f%%%n", simS3 * 100);
        System.out.printf("  S4 Method-level TED (APTED)     : %.2f%%%n", simS4 * 100);
        double combined = (simS1 + simS2 + simS3 + simS4) / 4.0;
        System.out.printf("  Combined Structural Score        : %.2f%%%n", combined * 100);
    }
}
