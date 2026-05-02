package com.ziqi;

import com.ziqi.codesim.pipeline.MethodPairFeature;
import com.ziqi.codesim.pipeline.MethodPairRawScore;
import com.ziqi.codesim.pipeline.PairFlag;
import com.ziqi.codesim.pipeline.SignalStatus;
import com.ziqi.codesim.pipeline.Stage2FeatureExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage2FeatureExtractorTest {
    @Test
    void computesDerivedFeaturesForRawPair() {
        MethodPairRawScore raw = new MethodPairRawScore(
                "A#m", "B#m",
                10, 20,
                0.25, 0.50, 0.75,
                SignalStatus.COMPUTED,
                5,
                4, 8, 10
        );

        MethodPairFeature feature = new Stage2FeatureExtractor().computeFeature(raw);

        assertEquals(Math.sqrt(0.5 * 0.5 + 0.75 * 0.75) / Math.sqrt(2.0),
                feature.magnitude(), 1e-9);
        assertEquals(0.5, feature.sizeRatio(), 1e-9);
        assertEquals(0.5, feature.containmentAInB(), 1e-9);
        assertEquals(0.4, feature.containmentBInA(), 1e-9);
        assertEquals(0.5, feature.tokenExactGap(), 1e-9);
        assertEquals(0.5, feature.spread(), 1e-9);
        assertTrue(feature.flags().isEmpty());
    }

    @Test
    void emptyFingerprintContainmentIsZeroAndFlagged() {
        MethodPairRawScore raw = new MethodPairRawScore(
                "A#tiny", "B#tiny",
                2, 2,
                1.0, 1.0, 1.0,
                SignalStatus.COMPUTED,
                0,
                0, 0, 0
        );

        MethodPairFeature feature = new Stage2FeatureExtractor().computeFeature(raw);

        assertEquals(0.0, feature.containmentAInB(), 1e-9);
        assertEquals(0.0, feature.containmentBInA(), 1e-9);
        assertTrue(feature.flags().contains(PairFlag.LOW_TOKEN_EVIDENCE));
    }
}
