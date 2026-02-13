// Ziqi Liu Meng Project-Based Software Engineering
// This file is used for selecting the minimum hash values as fingerprints (Winnowing)
// Choose rightmost if tie
package com.ziqi.codesim.fingerprint;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

public class Winnowing {
    // Get fingerprints and their positions from tokens
    public static class Fingerprint {
        public final long hash;
        public final int pos;
        public Fingerprint(long hash, int pos) {
            this.hash = hash;
            this.pos = pos;
        }
    }
    public static List<Fingerprint> fingerprintTokens(List<String> tokens, int k, int w) {
        List<Fingerprint> fpsList = new ArrayList<>();
        long[] kgramHashes = RollingHash.kgramHashes(tokens, k);
        int n = kgramHashes.length;
        // Set<Long> fingerprints = new HashSet<>();
        if (n == 0 || w == 0) return fpsList;
        if (n <= w) {
            int idx  = minRightmost(kgramHashes, 0, n);
            fpsList.add(new Fingerprint(kgramHashes[idx], idx));
            return fpsList;
        }
        int lastIdx = -1;
        for (int i = 0; i <= n-w; i++) {
            int idx = minRightmost(kgramHashes, i, i+w);
            if (idx != lastIdx) {
                fpsList.add(new Fingerprint(kgramHashes[idx], idx));
                lastIdx = idx;
            }
        }
        return fpsList;    
    }
    // Find the index of the minimum hash (choose rightmost if tie)
    private static int minRightmost(long[] a, int start, int end) {
        long min = a[start];
        int idx = start;
        for (int i = start; i < end; i++) {
            if (Long.compareUnsigned(a[i], min) <= 0) {
                min = a[i];
                idx = i;
            }
        }
        return idx;
    }
}
