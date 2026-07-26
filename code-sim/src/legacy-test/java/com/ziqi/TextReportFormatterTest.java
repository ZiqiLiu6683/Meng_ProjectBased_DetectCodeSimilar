package com.ziqi;

import com.ziqi.codesim.pipeline.FullPipelineResult;
import com.ziqi.codesim.pipeline.PipelineRunner;
import com.ziqi.codesim.pipeline.TextReportFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TextReportFormatterTest {
    @Test
    void formatsFullPipelineReport() {
        String sourceA = "class A { int add(int x, int y) { return x + y; } }";
        String sourceB = "class A { int add(int x, int y) { return x + y; } }";

        FullPipelineResult result = new PipelineRunner().runFull(sourceA, sourceB);
        String report = new TextReportFormatter().format(result, "A.java", "B.java");

        assertTrue(report.contains("=== Code Similarity Report ==="));
        assertTrue(report.contains("=== Final Decision ==="));
        assertTrue(report.contains("Clone Type"));
        assertTrue(report.contains("=== Evidence Chain ==="));
        assertTrue(report.contains("=== Matched Method Pairs ==="));
        assertTrue(report.contains("=== Diagnostic Feature Summary ==="));
        assertTrue(report.contains("=== Pipeline Details ==="));
    }
}
