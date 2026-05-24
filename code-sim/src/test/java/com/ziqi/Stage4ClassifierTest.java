package com.ziqi;

import com.ziqi.codesim.pipeline.CloneType;
import com.ziqi.codesim.pipeline.PipelineResult;
import com.ziqi.codesim.pipeline.PipelineRunner;
import com.ziqi.codesim.pipeline.ScopeType;
import com.ziqi.codesim.pipeline.Stage4Classifier;
import com.ziqi.codesim.pipeline.Stage4Result;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    @Test
    void classifiesRenamedSampleAsT2FullScope() throws IOException {
        Stage4Result result = runSample("B1.java", "B2.java");

        assertEquals(CloneType.T2, result.cloneType());
        assertEquals(ScopeType.FULL, result.scopeType());
        assertTrue(result.confidence() >= 0.95);
    }

    @Test
    void classifiesInternalHelperRefactorSampleAsWeakType4() throws IOException {
        Stage4Result result = runSample("C1.java", "C2.java");

        assertEquals(CloneType.T4_WEAK, result.cloneType());
        assertEquals(ScopeType.FULL, result.scopeType());
        assertTrue(result.typeScores().t4Weak() > result.typeScores().t2());
        assertTrue(result.evidenceChain().supportingEvidence().stream()
                .anyMatch(item -> "S5".equals(item.signal())));
    }

    @Test
    void nonCloneSampleDoesNotClaimFullCloneScope() throws IOException {
        Stage4Result result = runSample("A1.java", "B1.java");

        assertEquals(CloneType.NON_CLONE, result.cloneType());
        assertEquals(ScopeType.UNKNOWN, result.scopeType());
        assertTrue(result.evidenceChain().supportingEvidence().stream()
                .anyMatch(item -> "method_dissimilarity_strength".equals(item.signal())));
        assertTrue(result.evidenceChain().supportingEvidence().stream()
                .anyMatch(item -> "uncovered_method_mass".equals(item.signal())));
    }

    private static Stage4Result runSample(String fileA, String fileB) throws IOException {
        Path samples = Path.of("samples");
        return new PipelineRunner().runFull(
                Files.readString(samples.resolve(fileA)),
                Files.readString(samples.resolve(fileB))
        ).stage4();
    }
}
