package com.ziqi.codesim;

import com.ziqi.codesim.io.FileUtils;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java -jar code-sim.jar <file1> <file2>");
            return;
        }

        String a = FileUtils.readAll(args[0]);
        String b = FileUtils.readAll(args[1]);

        System.out.println("File A: " + args[0]);
        System.out.println("Chars A: " + a.length());
        System.out.println("File B: " + args[1]);
        System.out.println("Chars B: " + b.length());
        boolean same = a.equals(b);
        System.out.println("Exact same text? " + same);
        System.out.println("First 200 chars of A:");
        System.out.println(a.substring(0, Math.min(200, a.length())));
        System.out.println("First 200 chars of B:");
        System.out.println(b.substring(0, Math.min(200, b.length())));
    }
}
