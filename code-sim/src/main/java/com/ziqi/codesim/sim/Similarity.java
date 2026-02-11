// Ziqi Liu Meng Project-Based Software Engineering
// This file is used for calculating the similarity between two sets of fingerprints
// Jaccard
package com.ziqi.codesim.sim;

import java.util.Set;
import java.util.HashSet;

public class Similarity {
    public static double jaccard(Set<Long> a, Set<Long> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<Long> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<Long> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }
}
