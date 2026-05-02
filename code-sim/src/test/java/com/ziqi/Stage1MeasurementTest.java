package com.ziqi;

import com.ziqi.codesim.pipeline.MethodPairRawScore;
import com.ziqi.codesim.pipeline.SignalStatus;
import com.ziqi.codesim.pipeline.Stage1Measurement;
import com.ziqi.codesim.pipeline.Stage1Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage1MeasurementTest {
    @Test
    void computesFullMethodPairMatrixForSingleMethodInputs() {
        String sourceA = """
                class Sample {
                    // whitespace and comments should not affect exact-normalized T1 signal
                    int sum(int a, int b) {
                        return a + b;
                    }
                }
                """;
        String sourceB = """
                class Sample { int sum(int a, int b) { /* same body */ return a + b; } }
                """;

        Stage1Result result = new Stage1Measurement().compute(sourceA, sourceB);

        assertTrue(result.fileExactNormalizedMatch());
        assertEquals(SignalStatus.NOT_APPLICABLE, result.s5Status());
        assertEquals(1, result.methodsA().size());
        assertEquals(1, result.methodsB().size());
        assertEquals(1, result.pairMatrix().size());

        MethodPairRawScore pair = result.pairMatrix().get(0);
        assertEquals(SignalStatus.COMPUTED, pair.s4Status());
        assertTrue(pair.s2() > 0.0);
        assertTrue(pair.s3() > 0.0);
        assertTrue(pair.s4() > 0.0);
        assertEquals(pair.s3CountA(), pair.s3IntersectionCount());
        assertEquals(pair.s3CountB(), pair.s3IntersectionCount());
    }
}
