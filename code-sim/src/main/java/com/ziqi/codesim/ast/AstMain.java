package com.ziqi.codesim.ast;

import java.util.List;

import com.ziqi.codesim.io.FileUtils;
import com.ziqi.codesim.fingerprint.RollingHash;
import com.ziqi.codesim.fingerprint.Winnowing;
import com.ziqi.codesim.sim.Similarity;
import com.github.javaparser.ast.CompilationUnit;

public class AstMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: mvn -q exec:java -Dexec.mainClass=\"com.ziqi.codesim.ast.AstMain\" -Dexec.args=\"A.java B.java\"");
            return;
        }

        String a = FileUtils.readAll(args[0]);
        String b = FileUtils.readAll(args[1]);

        CompilationUnit cuA = AstTokenizer.parse(a);
        CompilationUnit cuB = AstTokenizer.parse(b);

        List<String> tokensA = AstTokenizer.tokenize(cuA);
        List<String> tokensB = AstTokenizer.tokenize(cuB);

        System.out.println("AST tokens A size = " + tokensA.size());
        System.out.println("AST tokens B size = " + tokensB.size());
        System.out.println("First 30 AST tokens of A:\n" + tokensA.subList(0, Math.min(30, tokensA.size())));
        System.out.println("First 30 AST tokens of B:\n" + tokensB.subList(0, Math.min(30, tokensB.size())));

        int k = 6;
        int w = 5;

        List<Winnowing.Fingerprint> fpA = Winnowing.fingerprintTokens(tokensA, k, w);
        List<Winnowing.Fingerprint> fpB = Winnowing.fingerprintTokens(tokensB, k, w);

        double sim = Similarity.jaccard(fpA, fpB);
        System.out.printf("AST Similarity (Jaccard) = %.2f%%%n", sim * 100);
    }
}
