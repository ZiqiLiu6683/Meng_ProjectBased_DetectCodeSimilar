package com.ziqi;

import com.ziqi.codesim.pipeline.PipelineResult;
import com.ziqi.codesim.pipeline.PipelineRunner;
import com.ziqi.codesim.pipeline.SignalStatus;
import com.ziqi.codesim.pipeline.Stage0Mode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PipelineRunnerTest {
    @Test
    void runsStagesZeroThroughThree() {
        String sourceA = """
                import java.util.List;
                class A {
                    int seed;
                    int add(int x, int y) { return x + y; }
                }
                """;
        String sourceB = """
                import java.util.ArrayList;
                class B {
                    int seed;
                    int sum(int left, int right) { return left + right; }
                }
                """;

        PipelineResult result = new PipelineRunner().run(sourceA, sourceB);

        assertEquals(Stage0Mode.SINGLE_METHOD_REAL_FILE, result.stage0().primaryMode());
        assertEquals(1, result.stage1().pairMatrix().size());
        assertEquals(SignalStatus.COMPUTED, result.stage2().status());
        assertEquals(SignalStatus.COMPUTED, result.stage3().status());
        assertFalse(result.stage3().mergedPairs().isEmpty());
        assertTrue(result.stage3().matchScoreAvg() > 0.0);
    }
}
