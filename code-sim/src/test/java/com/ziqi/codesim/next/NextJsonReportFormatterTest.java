package com.ziqi.codesim.next;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NextJsonReportFormatterTest {
    @Test
    void emitsFileSummaryRegionEvidenceAndDecisionPath() {
        String left = """
                class A {
                  int count(int[] values) {
                    int total = 0;
                    for (int value : values) {
                      total += value;
                    }
                    return total;
                  }
                }
                """;
        String right = """
                class B {
                  int sum(int[] items) {
                    int result = 0;
                    for (int item : items) {
                      if (item > 0) {
                        result += item;
                      }
                    }
                    return result;
                  }
                }
                """;

        NextPipelineResult result = new NextPipelineRunner().run(left, right);
        String json = new NextJsonReportFormatter().format(result);

        assertTrue(json.contains("\"fileSummary\""));
        assertTrue(json.contains("\"inspectionPriority\""));
        assertTrue(json.contains("\"relationshipShape\""));
        assertTrue(json.contains("\"affectedContent\""));
        assertTrue(json.contains("\"leftRatio\""));
        assertTrue(json.contains("\"rightRatio\""));
        assertTrue(json.contains("\"evidenceBreakdown\""));
        assertTrue(json.contains("\"regionCount\""));
        assertTrue(json.contains("\"overallRelationship\""));
        assertTrue(json.contains("\"regions\""));
        assertTrue(json.contains("\"decisionPath\""));
        assertTrue(json.contains("\"statementChanges\""));
        assertTrue(json.contains("\"RENAMING_DETECTED\""));
    }

    @Test
    void limitsRegionOutputButReportsAcceptedCount() {
        String left = """
                class A {
                  int a(int x) { return x + 1; }
                  int b(int x) { return x + 2; }
                }
                """;
        String right = """
                class B {
                  int a(int y) { return y + 1; }
                  int b(int y) { return y + 2; }
                }
                """;

        NextPipelineResult result = new NextPipelineRunner().run(left, right);
        String json = new NextJsonReportFormatter(1).format(result);

        assertTrue(json.contains("\"acceptedRegionCount\""));
        assertTrue(json.contains("\"selectedRegionCount\""));
        assertTrue(json.contains("\"suppressedRegionCount\""));
        assertTrue(json.contains("\"emittedRegionCount\": 1"));
    }
}
