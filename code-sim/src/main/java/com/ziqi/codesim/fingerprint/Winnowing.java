// Ziqi Liu Meng Project-Based Software Engineering
// This file is used for selecting the minimum hash values as fingerprints (Winnowing)
package com.ziqi.codesim.fingerprint;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Winnowing {
    public static Set<Long> fingerprintTokens(List<String> tokens, int k, int w) {
        long[] kgramHashes = RollingHash.kgramHashes(tokens, k);
        int n = kgramHashes.length;
        Set<Long> fingerprints = new HashSet<>();
        if (n == 0 || w == 0) return fingerprints;
        if (n <= w) {
            int idx  = minRightmost(kgramHashes, 0, n);
            fingerprints.add(kgramHashes[idx]);
            return fingerprints;
        }
        int lastIdx = -1;
        for (int i = 0; i <= n-w; i++) {
            int idx = minRightmost(kgramHashes, i, i+w);
            if (idx != lastIdx) {
                fingerprints.add(kgramHashes[idx]);
                lastIdx = idx;
            }
        }
        return fingerprints;    
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
