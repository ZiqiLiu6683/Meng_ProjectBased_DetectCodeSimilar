package com.ziqi.codesim.next.semantic.web;

import com.ziqi.codesim.next.semantic.AnalysisOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class InputPreflightTest {

    @Test
    void recommendsQuickForSmallParseableSources() {
        String left = "class Left { int inc(int x) { return x + 1; } }";
        String right = "class Right { int addOne(int value) { return value + 1; } }";

        InputPreflight.Result result = InputPreflight.inspect(left, right);

        assertEquals(1, result.left().lines());
        assertEquals(1, result.left().methods());
        assertEquals(1, result.right().methods());
        assertTrue(result.left().regions() > 1);
        assertTrue(result.right().regions() > 1);
        assertTrue(result.quickComparisonUpperBound() <= result.quickComparisonBudget());
        assertTrue(result.quickAllowed());
        assertEquals(InputPreflight.Workload.LOW, result.workload());
        assertEquals(AnalysisOptions.AnalysisDepth.SOURCE_AST, result.recommendedMode());
    }

    @Test
    void measuredLargeInputShapeIsRejectedForQuickWithoutRunningCandidates() {
        InputPreflight.Decision decision = InputPreflight.decisionForRegionCounts(2_236, 2_275);

        assertEquals(5_082_391L, decision.comparisonUpperBound());
        assertFalse(decision.quickAllowed());
        assertEquals(InputPreflight.Workload.HIGH, decision.workload());
        assertEquals(AnalysisOptions.AnalysisDepth.WALA_REGIONS, decision.recommendedMode());
    }

    @Test
    void comparisonEstimateMatchesFileOnlyAndNonFileCrossProduct() {
        assertEquals(1L, InputPreflight.comparisonUpperBound(1, 1));
        assertEquals(21L, InputPreflight.comparisonUpperBound(5, 6));
        assertEquals(0L, InputPreflight.comparisonUpperBound(0, 4));
    }
}
