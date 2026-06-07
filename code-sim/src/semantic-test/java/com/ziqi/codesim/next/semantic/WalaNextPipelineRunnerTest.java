package com.ziqi.codesim.next.semantic;

import com.ziqi.codesim.next.NextPipelineResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class WalaNextPipelineRunnerTest {
    @Test
    void attachesRealWalaCfgKnnSignalsToNextPipelineCandidates() throws Exception {
        String left = """
                class LeftInput {
                  int count(int[] values) {
                    int total = 0;
                    for (int value : values) {
                      if (value > 0) {
                        total += value;
                      }
                    }
                    return total;
                  }
                }
                """;
        String right = """
                class RightInput {
                  int count(int[] items) {
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

        NextPipelineResult result = new WalaNextPipelineRunner().run(left, right);

        assertTrue(result.candidates().stream()
                .flatMap(candidate -> candidate.sources().stream())
                .anyMatch(source -> source.channel().equals("CFG_KNN_SCAN")));
    }
}
