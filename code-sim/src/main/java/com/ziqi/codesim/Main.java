package com.ziqi.codesim;

import java.util.List;

import com.ziqi.codesim.io.FileUtils;
import com.ziqi.codesim.preprocess.Tokenizer;

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
    }
}
