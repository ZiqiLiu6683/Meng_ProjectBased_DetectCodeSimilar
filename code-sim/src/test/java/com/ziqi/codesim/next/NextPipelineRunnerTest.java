package com.ziqi.codesim.next;

import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NextPipelineRunnerTest {
    private final NextPipelineRunner runner = new NextPipelineRunner();

    @Test
    void classifiesT1OnlyWhenComparableTokensAreIdentical() {
        String left = """
                class A {
                  // comment
                  int sum(int a, int b) {
                    return a + b;
                  }
                }
                """;
        String right = """
                class A {
                  int sum(int a, int b) {
                    /* another comment */
                    return a + b;
                  }
                }
                """;

        RegionDecision decision = bestDecision(left, right);

        assertEquals(CloneRegionType.T1, decision.type());
        assertTrue(decision.tags().contains(RegionTag.EXACT_COPY));
    }

    @Test
    void classifiesT2WhenOnlyNamesAndLiteralsChange() {
        String left = """
                class A {
                  int sum(int a, int b) {
                    int total = a + b + 10;
                    return total;
                  }
                }
                """;
        String right = """
                class B {
                  int add(int x, int y) {
                    int result = x + y + 20;
                    return result;
                  }
                }
                """;

        RegionDecision decision = bestDecision(left, right);

        assertEquals(CloneRegionType.T2, decision.type());
        assertTrue(decision.tags().contains(RegionTag.RENAMING_DETECTED));
        assertTrue(decision.tags().contains(RegionTag.LITERAL_CHANGED));
        assertFalse(decision.statementEditScript().hasAnyChange());
    }

    @Test
    void classifiesT3WhenNormalizedCodeStillHasStatementEdits() {
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
                      if (item < 0) {
                        continue;
                      }
                      result += item;
                    }
                    return result;
                  }
                }
                """;

        RegionDecision decision = bestDecision(left, right);

        assertEquals(CloneRegionType.T3, decision.type());
        assertTrue(decision.tags().contains(RegionTag.RENAMING_DETECTED));
        assertTrue(decision.tags().contains(RegionTag.STATEMENT_INSERTED)
                || decision.tags().contains(RegionTag.STATEMENT_MODIFIED));
        assertTrue(decision.syntacticSimilarity() >= 0.50);
    }

    private RegionDecision bestDecision(String left, String right) {
        return runner.run(left, right).regionDecisions().stream()
                .filter(d -> d.candidate().left().kind() == RegionKind.METHOD
                        || d.candidate().left().kind() == RegionKind.METHOD_BODY_REGION)
                .filter(d -> d.candidate().right().kind() == RegionKind.METHOD
                        || d.candidate().right().kind() == RegionKind.METHOD_BODY_REGION)
                .filter(d -> d.type() != CloneRegionType.NON_CLONE)
                .max(Comparator.comparingDouble(RegionDecision::syntacticSimilarity))
                .orElseThrow();
    }
}
