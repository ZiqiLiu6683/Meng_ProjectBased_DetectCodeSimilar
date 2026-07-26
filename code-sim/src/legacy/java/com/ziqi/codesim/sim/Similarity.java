package com.ziqi.codesim.sim;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ziqi.codesim.fingerprint.Winnowing;

public class Similarity {
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

    // Generic overload covers subtree hashes and API call names.
    public static <T> double jaccard(Set<T> a, Set<T> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<T> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<T> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }
}
