package com.ziqi.codesim.semantic.knn;

import java.util.List;

/**
 * Builds a {@link KnnIndex} over a set of standardized feature vectors. Lets the
 * pre-filter backend be chosen without changing call sites; defaults preserve the
 * existing exact linear scan.
 */
@FunctionalInterface
public interface KnnIndexFactory {
    KnnIndex create(List<StandardizedFeatureVector> vectors);

    /** Exact O(n) linear scan. Deterministic and exact; the default. */
    KnnIndexFactory EXACT_LINEAR = ExactKnnIndex::new;

    /** Exact k-d tree. Same nearest neighbours as the linear scan, tree-pruned search. */
    KnnIndexFactory KD_TREE = KdTreeKnnIndex::new;
}
