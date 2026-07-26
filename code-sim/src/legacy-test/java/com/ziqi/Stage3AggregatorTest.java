package com.ziqi;

import com.ziqi.codesim.pipeline.MatchDirection;
import com.ziqi.codesim.pipeline.MethodDescriptor;
import com.ziqi.codesim.pipeline.MethodPairFeature;
import com.ziqi.codesim.pipeline.MergedPairFeature;
import com.ziqi.codesim.pipeline.SignalStatus;
import com.ziqi.codesim.pipeline.Stage1Result;
import com.ziqi.codesim.pipeline.Stage2Result;
import com.ziqi.codesim.pipeline.Stage3Aggregator;
import com.ziqi.codesim.pipeline.Stage3Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage3AggregatorTest {
    @Test
    void mergesDirectionalBestMatchesAndComputesDiagnostics() {
        MethodDescriptor a1 = method("A1", 20);
        MethodDescriptor a2 = method("A2", 10);
        MethodDescriptor b1 = method("B1", 20);
        MethodDescriptor b2 = method("B2", 10);

        Stage1Result stage1 = new Stage1Result(
                0.5,
                SignalStatus.NOT_APPLICABLE,
                -1.0,
                false,
                List.of(a1, a2),
                List.of(b1, b2),
                List.of()
        );
        Stage2Result stage2 = new Stage2Result(
                SignalStatus.COMPUTED,
                List.of(
                        feature("A1", "B1", 20, 20, 0.90, 0.90, 1.0, 1.0),
                        feature("A1", "B2", 20, 10, 0.20, 0.20, 0.2, 0.4),
                        feature("A2", "B1", 10, 20, 0.60, 0.60, 1.0, 0.5),
                        feature("A2", "B2", 10, 10, 0.30, 0.30, 0.3, 0.3)
                )
        );

        Stage3Result result = new Stage3Aggregator().compute(stage1, stage2);

        assertEquals(SignalStatus.COMPUTED, result.status());
        assertEquals(3, result.mergedPairs().size());
        assertTrue(result.mergedPairs().stream()
                .anyMatch(p -> p.methodAId().equals("A1")
                        && p.methodBId().equals("B1")
                        && p.direction() == MatchDirection.BIDIRECTIONAL));
        assertTrue(result.mergedPairs().stream()
                .anyMatch(p -> p.methodAId().equals("A2")
                        && p.methodBId().equals("B1")
                        && p.direction() == MatchDirection.A_TO_B_ONLY));
        assertTrue(result.coverageA() > 0.7);
        assertTrue(result.coverageB() > 0.7);
        assertTrue(result.confirmedRatio() > 0.0);
        assertTrue(result.partialAInB() > result.partialBInA());
        assertEquals(result.partialAInB(), result.partialCloneSignal(), 1e-9);
    }

    private static MethodDescriptor method(String id, int size) {
        return new MethodDescriptor(id, id + "()", id, id + "()", "C", 1, size, size);
    }

    private static MethodPairFeature feature(String methodAId, String methodBId,
                                             int sizeA, int sizeB,
                                             double s3, double s4,
                                             double containmentAInB,
                                             double containmentBInA) {
        double magnitude = Math.sqrt(s3 * s3 + s4 * s4) / Math.sqrt(2.0);
        return new MethodPairFeature(
                methodAId,
                methodBId,
                sizeA,
                sizeB,
                0.5,
                s3,
                s4,
                SignalStatus.COMPUTED,
                0,
                magnitude,
                0.0,
                0.5,
                0.0,
                0.0,
                (double) Math.min(sizeA, sizeB) / Math.max(sizeA, sizeB),
                containmentAInB,
                containmentBInA,
                Set.of()
        );
    }
}
