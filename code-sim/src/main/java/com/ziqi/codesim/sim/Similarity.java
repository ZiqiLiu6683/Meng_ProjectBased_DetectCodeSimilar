// Ziqi Liu Meng Project-Based Software Engineering
// This file is used for calculating the similarity between two sets of fingerprints
// Jaccard
package com.ziqi.codesim.sim;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import com.ziqi.codesim.fingerprint.Winnowing;

public class Similarity {
    // Get Hash Sets from Fingerprint Lists
    public static Set<Long> toHashSet(List<Winnowing.Fingerprint> fps) {
        Set<Long> hashSet = new HashSet<>();
        for (Winnowing.Fingerprint fp : fps) {
            hashSet.add(fp.hash);
        }
        return hashSet;
    }
    public static double jaccard(List<Winnowing.Fingerprint> fpsA, List<Winnowing.Fingerprint> fpsB) {
        Set<Long> a = toHashSet(fpsA);
        Set<Long> b = toHashSet(fpsB);
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<Long> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<Long> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    // Generic overload: any Set<T> — handles Set<Long> (SubtreeHasher/S2)
    // and Set<String> (ApiCallSimilarity/S5). Java type erasure prevents
    // having both a Set<Long> and a Set<T> overload simultaneously.
    public static <T> double jaccard(Set<T> a, Set<T> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<T> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<T> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }
}
