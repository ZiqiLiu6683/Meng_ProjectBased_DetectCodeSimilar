package com.ziqi.codesim.next.semantic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class PipelineExecutionProvenanceTest {

    @Test
    void recordsFullWalaModeAndIndependentStageDurations() throws Exception {
        String previous = System.getProperty("codesim.skipDynamic");
        System.setProperty("codesim.skipDynamic", "true");
        try {
            String left = "class LeftInput { int f(int x) { return x + 1; } }";
            String right = "class RightInput { int g(int y) { return y + 1; } }";

            PipelineExecution execution = new WalaNextPipelineRunner().runDetailed(left, right);

            assertEquals(PipelineExecution.AnalysisMode.SOURCE_PLUS_WALA_SMT,
                    execution.analysisMode());
            assertEquals(PipelineExecution.StageStatus.SUCCESS,
                    execution.stages().get("compile_left").status());
            assertEquals(PipelineExecution.StageStatus.SUCCESS,
                    execution.stages().get("compile_right").status());
            assertEquals(PipelineExecution.StageStatus.SUCCESS,
                    execution.stages().get("graph").status());
            assertEquals(PipelineExecution.StageStatus.SKIPPED_CONFIG,
                    execution.stages().get("dynamic").status());
            assertEquals("", execution.fallbackStage());
            assertFalse(execution.stages().isEmpty());
        } finally {
            restoreSkipDynamic(previous);
        }
    }

    @Test
    void recordsCompilationFailureBeforeSourceFallback() throws Exception {
        String previous = System.getProperty("codesim.skipDynamic");
        System.setProperty("codesim.skipDynamic", "true");
        try {
            String left = "class LeftInput { int f() { return unknownValue; } }";
            String right = "class RightInput { int g() { return 1; } }";

            PipelineExecution execution = new WalaNextPipelineRunner().runDetailed(left, right);

            assertEquals(PipelineExecution.AnalysisMode.SOURCE_ONLY_FALLBACK,
                    execution.analysisMode());
            assertEquals("compile_left", execution.fallbackStage());
            assertEquals("COMPILATION_FAILED", execution.fallbackReason());
            assertEquals(PipelineExecution.StageStatus.FAILED,
                    execution.stages().get("compile_left").status());
            assertEquals(PipelineExecution.StageStatus.NOT_REACHED,
                    execution.stages().get("compile_right").status());
            assertEquals(PipelineExecution.StageStatus.SUCCESS,
                    execution.stages().get("fallback").status());
            assertNotNull(execution.result());
        } finally {
            restoreSkipDynamic(previous);
        }
    }

    @Test
    void recordsStubAssistedWalaSeparatelyFromExactWala() throws Exception {
        String previous = System.getProperty("codesim.skipDynamic");
        System.setProperty("codesim.skipDynamic", "true");
        try {
            String left = "class LeftInput { MissingType value; int f(int x) { return x + 1; } }";
            String right = "class RightInput { int g(int y) { return y + 1; } }";

            PipelineExecution execution = new WalaNextPipelineRunner().runDetailed(left, right);

            assertEquals(PipelineExecution.AnalysisMode.SOURCE_PLUS_STUBBED_WALA_SMT,
                    execution.analysisMode());
            assertEquals("STUBBED", execution.compilations().get("left").mode());
            assertEquals(1, execution.compilations().get("left").generatedStubCount());
            assertEquals(17, execution.compilations().get("left").javaRelease());
        } finally {
            restoreSkipDynamic(previous);
        }
    }

    @Test
    void stubbedDependencyContextCannotProduceStrictOrDynamicT4Evidence() throws Exception {
        String previous = System.getProperty("codesim.skipDynamic");
        System.clearProperty("codesim.skipDynamic");
        try {
            String left = "class LeftInput { MissingType dependency; "
                    + "int f(int x) { return x + x + x; } }";
            String right = "class RightInput { int g(int y) { return y * 3; } }";

            PipelineExecution execution = new WalaNextPipelineRunner().runDetailed(left, right);

            assertEquals(PipelineExecution.AnalysisMode.SOURCE_PLUS_STUBBED_WALA_SMT,
                    execution.analysisMode());
            assertEquals(PipelineExecution.StageStatus.SKIPPED_CONFIG,
                    execution.stages().get("smt").status());
            assertEquals(PipelineExecution.StageStatus.SKIPPED_CONFIG,
                    execution.stages().get("dynamic").status());
            assertTrue(execution.result().regionDecisions().stream().noneMatch(decision ->
                    decision.type() == com.ziqi.codesim.next.CloneRegionType.T4_CONFIRMED
                            || decision.type()
                            == com.ziqi.codesim.next.CloneRegionType.T4_DYNAMIC_EVIDENCE));
        } finally {
            restoreSkipDynamic(previous);
        }
    }

    private static void restoreSkipDynamic(String previous) {
        if (previous == null) {
            System.clearProperty("codesim.skipDynamic");
        } else {
            System.setProperty("codesim.skipDynamic", previous);
        }
    }
}
