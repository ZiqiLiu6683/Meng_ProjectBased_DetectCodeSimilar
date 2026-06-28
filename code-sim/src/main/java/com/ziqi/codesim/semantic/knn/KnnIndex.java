package com.ziqi.codesim.semantic.knn;

import java.util.List;

/**
 * A nearest-neighbour index over standardized feature vectors. Implementations
 * may differ in data structure and complexity (exact linear scan, k-d tree, ...)
 * but must agree on the distance metric and the returned {@link BlockCandidate}
 * shape so they are interchangeable as the discovRE numeric pre-filter backend.
 */
public interface KnnIndex {
    List<BlockCandidate> query(StandardizedFeatureVector query, int k);
}
