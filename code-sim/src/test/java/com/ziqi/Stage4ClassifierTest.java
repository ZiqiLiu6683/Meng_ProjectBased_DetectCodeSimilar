package com.ziqi;

import com.ziqi.codesim.pipeline.CloneType;
import com.ziqi.codesim.pipeline.PipelineResult;
import com.ziqi.codesim.pipeline.PipelineRunner;
import com.ziqi.codesim.pipeline.ScopeType;
import com.ziqi.codesim.pipeline.Stage4Classifier;
import com.ziqi.codesim.pipeline.Stage4Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage4ClassifierTest {
    @Test
    void classifiesExactNormalizedMatchAsT1FullScope() {
        String sourceA = """
                class A {
                    int add(int x, int y) {
                        return x + y;
                    }
                }
                """;
        String sourceB = """
                class A { int add(int x, int y) { /* same */ return x + y; } }
                """;

        PipelineResult pipeline = new PipelineRunner().run(sourceA, sourceB);
        Stage4Result result = new Stage4Classifier().classify(
                pipeline.stage0(),
                pipeline.stage1(),
                pipeline.stage3()
        );

        assertEquals(CloneType.T1, result.cloneType());
        assertEquals(ScopeType.FULL, result.scopeType());
        assertTrue(result.confidence() > 0.0);
        assertFalse(result.evidenceChain().supportingEvidence().isEmpty());
    }
}
