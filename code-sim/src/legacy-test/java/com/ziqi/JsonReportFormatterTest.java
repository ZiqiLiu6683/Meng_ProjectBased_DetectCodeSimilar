package com.ziqi;

import com.ziqi.codesim.pipeline.FullPipelineResult;
import com.ziqi.codesim.pipeline.JsonReportFormatter;
import com.ziqi.codesim.pipeline.PipelineRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonReportFormatterTest {
    @Test
    void formatsFullPipelineAsJson() {
        String sourceA = "class A { int add(int x, int y) { return x + y; } }";
        String sourceB = "class B { int sum(int left, int right) { return left + right; } }";

        FullPipelineResult result = new PipelineRunner().runFull(sourceA, sourceB);
        String json = new JsonReportFormatter().format(result, "A.java", "B.java");

        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"schemaVersion\":\"1.0\""));
        assertTrue(json.contains("\"summary\""));
        assertTrue(json.contains("\"stage0\""));
        assertTrue(json.contains("\"stage1\""));
        assertTrue(json.contains("\"stage2\""));
        assertTrue(json.contains("\"stage3\""));
        assertTrue(json.contains("\"stage4\""));
        assertTrue(json.contains("\"mergedPairs\""));
    }
}
