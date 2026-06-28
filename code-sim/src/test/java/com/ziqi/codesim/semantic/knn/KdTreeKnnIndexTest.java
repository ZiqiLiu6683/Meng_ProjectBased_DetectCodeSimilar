package com.ziqi.codesim.semantic.knn;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The k-d tree index must return exactly the same top-k as the exact linear scan,
 * including tie-breaking when several candidates are equidistant. This is checked
 * over many randomized, deliberately tie-heavy inputs (empty vectors, repeated
 * values, low dimensionality) because equidistant ties are where a naive k-d tree
 * diverges from the linear scan.
 */
class KdTreeKnnIndexTest {

    @Test
    void kdTreeMatchesExactLinearScanOnTieHeavyInputs() {
        Random random = new Random(7);
        int trials = 3000;
        for (int trial = 0; trial < trials; trial++) {
            int n = 1 + random.nextInt(14);
            int dimensions = random.nextInt(6); // 0..5, dim 0 -> empty vectors (all equidistant)
            int k = 1 + random.nextInt(n);

            List<StandardizedFeatureVector> vectors = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Map<String, Double> values = new HashMap<>();
                for (int j = 0; j < dimensions; j++) {
                    if (random.nextDouble() < 0.7) {
                        values.put("f" + j, tieHeavyValue(random));
                    }
                }
                vectors.add(new StandardizedFeatureVector(
                        "v" + i, KnnFeatureView.DISCOVRE_NUMERIC, values, Map.of()));
            }
            StandardizedFeatureVector query = vectors.get(random.nextInt(n));

            List<BlockCandidate> exact = new ExactKnnIndex(vectors).query(query, k);
            List<BlockCandidate> kdTree = new KdTreeKnnIndex(vectors).query(query, k);

            String context = "trial " + trial + " (n=" + n + ", dim=" + dimensions + ", k=" + k + ")";
            assertEquals(candidateIds(exact), candidateIds(kdTree),
                    "candidate ids differ at " + context);
            for (int i = 0; i < exact.size(); i++) {
                assertEquals(exact.get(i).distance(), kdTree.get(i).distance(), 1e-9,
                        "distance differs at " + context + ", position " + i);
            }
        }
    }

    private static double tieHeavyValue(Random random) {
        double[] common = {-1.0, 0.0, 1.0, 2.0};
        if (random.nextDouble() < 0.6) {
            return common[random.nextInt(common.length)];
        }
        return -2.0 + 4.0 * random.nextDouble();
    }

    private static List<String> candidateIds(List<BlockCandidate> candidates) {
        List<String> ids = new ArrayList<>();
        for (BlockCandidate candidate : candidates) {
            ids.add(candidate.candidateBlockId());
        }
        return ids;
    }
}
