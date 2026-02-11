package com.ziqi.codesim;

import java.util.List;

import com.ziqi.codesim.io.FileUtils;
import com.ziqi.codesim.preprocess.Tokenizer;
import com.ziqi.codesim.fingerprint.RollingHash;


public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java -jar code-sim.jar <file1> <file2>");
            return;
        }

        String a = FileUtils.readAll(args[0]);
        String b = FileUtils.readAll(args[1]);
        List<String> tokensA = Tokenizer.tokenizeForBaseline(a);
        List<String> tokensB = Tokenizer.tokenizeForBaseline(b);
        // Read Files
        System.out.println("File A: " + args[0]);
        System.out.println("Chars A: " + a.length());
        System.out.println("File B: " + args[1]);
        System.out.println("Chars B: " + b.length());
        // Token
        System.out.println("First 10 tokens of A: \n" + tokensA.subList(0, Math.min(10, tokensA.size())));
        System.out.println("First 10 tokens of B: \n" + tokensB.subList(0, Math.min(10, tokensB.size())));
        // Hash
        int k = 10; 
        long[] hashesA = RollingHash.kgramHashes(tokensA, k);
        long[] hashesB = RollingHash.kgramHashes(tokensB, k);
        System.out.println("\n Rolling Hashes:");
        System.out.println("k = " + k);
        System.out.println("Tokens A = " + tokensA.size() + ", k-grams A = " + hashesA.length);
        System.out.println("Tokens B = " + tokensB.size() + ", k-grams B = " + hashesB.length);
        System.out.println("First 5 k-gram hashes of A:");
        for (int i = 0; i < 5; i++) {
            System.out.println("A[" + i + "] = " + Long.toUnsignedString(hashesA[i]));
        }
        System.out.println("First 5 k-gram hashes of B:");
        for (int i = 0; i < 5; i++) {
            System.out.println("B[" + i + "] = " + Long.toUnsignedString(hashesB[i]));
        }
    }
}
