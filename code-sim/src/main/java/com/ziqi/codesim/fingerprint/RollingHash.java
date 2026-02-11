package com.ziqi.codesim.fingerprint;

import java.util.List;

public class RollingHash {
    public static long[] kgramHashes(List<String> tokens, int k) {
        int n = tokens.size();
        if (n < k) return new long[0];
        long base = 202602101053L; 
        long mask = 0xFFFFFFFFFFFFFFFFL; // 64-bit mask

        // Store base^0 to base^k for rolling hash
        long[] basePowers = new long[k+1];
        basePowers[0] = 1;
        for (int i = 1; i <= k; i++) {
            basePowers[i] = basePowers[i-1]*base & mask;
        }

        // Store hash values for tokens[0] to [i]
        long[] prehashes = new long[n+1];
        prehashes[0] = 0;
        for (int i = 0; i < n; i++) {
            long a = mix64(tokens.get(i));
            prehashes [i+1] = (prehashes[i]*base + a) & mask;
        }

        // Store k-gram hashes
        long[] kgramHashes = new long[n-k+1];
        for (int i = 0; i <= n-k; i++) {
            kgramHashes[i] = (prehashes[i+k] - prehashes[i]*basePowers[k]) & mask;
        }
        return kgramHashes;
    }

    // Convert String to a 64-bit hash (FNV-1a)
    private static long mix64(String s) {
        long x = 1469598103934665603L;
        for (int i = 0; i < s.length(); i++) {
            x ^= s.charAt(i);
            x *= 1099511628211L;
        }
        return x;
    }
}
