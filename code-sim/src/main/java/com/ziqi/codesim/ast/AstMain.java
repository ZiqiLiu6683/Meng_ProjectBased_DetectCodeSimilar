// Ziqi Liu Meng Project-Based Software Engineering
// AST-based similarity detection entry point.
// Runs three complementary structural strategies and prints all scores:
//   1. AST-token Winnowing   – serialise AST to token sequence, then fingerprint
//   2. Subtree Matching      – exact canonical subtree hashes, Jaccard overlap
//   3. Method-level Matching – per-method best-match pairing, weighted average
package com.ziqi.codesim.ast;

import java.util.List;
import java.util.Set;

import com.ziqi.codesim.io.FileUtils;
import com.ziqi.codesim.fingerprint.Winnowing;
import com.ziqi.codesim.sim.Similarity;
import com.github.javaparser.ast.CompilationUnit;

public class AstMain {

    // k-gram size and window size for Strategy 1 (file-level token Winnowing)
    private static final int K = 6;
    private static final int W = 5;

    // Minimum AST node count for a subtree to be collected (Strategy 2)
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

        // ----------------------------------------------------------------
        // Parse both files to AST
        // ----------------------------------------------------------------
        CompilationUnit cuA = AstTokenizer.parse(srcA);
        CompilationUnit cuB = AstTokenizer.parse(srcB);

        // ================================================================
        // Strategy 1 – AST-token Winnowing (file level)
        // ================================================================
        List<String> tokensA = AstTokenizer.tokenize(cuA);
        List<String> tokensB = AstTokenizer.tokenize(cuB);

        List<Winnowing.Fingerprint> fpA = Winnowing.fingerprintTokens(tokensA, K, W);
        List<Winnowing.Fingerprint> fpB = Winnowing.fingerprintTokens(tokensB, K, W);

        double simS1 = Similarity.jaccard(fpA, fpB);

        // ================================================================
        // Strategy 2 – Exact Subtree Matching
        // ================================================================
        Set<Long> subtreesA = SubtreeHasher.extractSubtreeHashes(cuA, MIN_SUBTREE_SIZE);
        Set<Long> subtreesB = SubtreeHasher.extractSubtreeHashes(cuB, MIN_SUBTREE_SIZE);

        double simS2 = Similarity.jaccard(subtreesA, subtreesB);

        // ================================================================
        // Strategy 3 – Method-level Best-match Pairing
        // ================================================================
        MethodLevelSimilarity.Result mlResult =
                MethodLevelSimilarity.compute(cuA, cuB);
        double simS3 = mlResult.similarity;

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
        System.out.printf("  Similarity  : %.2f%%%n", simS1 * 100);
        System.out.println();

        // --- Strategy 2 ---
        System.out.println("-- Strategy 2: Exact Subtree Matching --");
        System.out.printf("  Subtrees (size>=%d): A=%d, B=%d%n",
                MIN_SUBTREE_SIZE, subtreesA.size(), subtreesB.size());
        System.out.printf("  Similarity  : %.2f%%%n", simS2 * 100);
        System.out.println();

        // --- Strategy 3 ---
        System.out.println("-- Strategy 3: Method-level Best-match Pairing --");

        System.out.println("  Forward matches (A → B):");
        for (MethodLevelSimilarity.MatchRecord r : mlResult.forwardMatches) {
            System.out.printf("    %-20s → %-20s  %.2f%%%n",
                    r.methodA + "()", r.methodB + "()", r.similarity * 100);
        }

        System.out.println("  Backward matches (B → A):");
        for (MethodLevelSimilarity.MatchRecord r : mlResult.backwardMatches) {
            System.out.printf("    %-20s → %-20s  %.2f%%%n",
                    r.methodA + "()", r.methodB + "()", r.similarity * 100);
        }
        System.out.printf("  Similarity  : %.2f%%%n", simS3 * 100);
        System.out.println();

        // ================================================================
        // Combined structural score: equal-weight average of all three
        // ================================================================
        double combined = (simS1 + simS2 + simS3) / 3.0;
        System.out.printf("== Combined Structural Score: %.2f%% ==%n", combined * 100);
    }
}
