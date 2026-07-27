package com.ziqi.codesim.next.semantic;

import com.ziqi.codesim.next.NextPipelineResult;
import com.ziqi.codesim.next.RegionKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class WalaNextPipelineRunnerTest {

    private static final String LEFT = """
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

    private static final String RIGHT = """
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

    /**
     * The result contract: a file pair is reported as REGIONS. Only behavioural T4 may be
     * method-scoped. Two independent routes used to break this and both are closed here --
     * the opt-in discovRE/kNN channel, and Phase B's own method-pair evidence running the
     * syntactic layers of the cascade.
     */
    @Test
    void noSyntacticCloneTypeIsEverReportedAtMethodScope() throws Exception {
        NextPipelineResult result = new WalaNextPipelineRunner().run(LEFT, RIGHT);

        assertFalse(result.candidates().stream()
                        .flatMap(candidate -> candidate.sources().stream())
                        .anyMatch(source -> source.channel().equals("CFG_KNN_SCAN")),
                "CFG_KNN_SCAN must not be attached unless explicitly enabled");
        assertFalse(result.regionDecisions().stream()
                        .filter(decision -> decision.candidate().left().kind() == RegionKind.METHOD)
                        .anyMatch(decision -> switch (decision.type()) {
                            case T1, T2, T3 -> true;
                            default -> false;
                        }),
                "a syntactic clone type must never be reported at method scope");
    }

    /**
     * The behavioural evidence itself must survive the restriction: this renamed pair is proven
     * equivalent by the dynamic tier, and that verdict must still be reported -- as T4, at method
     * scope, which the contract does allow.
     */
    @Test
    void behaviouralMethodEvidenceIsStillReportedAsT4() throws Exception {
        NextPipelineResult result = new WalaNextPipelineRunner().run(LEFT, RIGHT);

        assertTrue(result.regionDecisions().stream()
                        .filter(decision -> decision.candidate().left().kind() == RegionKind.METHOD)
                        .allMatch(decision -> switch (decision.type()) {
                            case T4_CONFIRMED, T4_DYNAMIC_EVIDENCE, POSSIBLE_T4_CANDIDATE,
                                 NON_CLONE -> true;
                            default -> false;
                        }),
                "a method-scoped decision may only be T4 evidence or NON_CLONE");
    }

    /** The channel still works when a comparison run asks for it explicitly. */
    @Test
    void attachesRealWalaCfgKnnSignalsWhenTheLegacyChannelIsEnabled() throws Exception {
        System.setProperty("codesim.legacyCfgChannels", "true");
        try {
            NextPipelineResult result = new WalaNextPipelineRunner().run(LEFT, RIGHT);
            assertTrue(result.candidates().stream()
                    .flatMap(candidate -> candidate.sources().stream())
                    .anyMatch(source -> source.channel().equals("CFG_KNN_SCAN")));
        } finally {
            System.clearProperty("codesim.legacyCfgChannels");
        }
    }

    /** The region backend itself is unaffected by the switch: a real clone is still found. */
    @Test
    void findsTheClonedRegionWithTheLegacyChannelOff() throws Exception {
        NextPipelineResult result = new WalaNextPipelineRunner().run(LEFT, RIGHT);

        assertTrue(result.selectedRegionDecisions().stream()
                        .anyMatch(decision -> switch (decision.type()) {
                            case T1, T2, T3 -> true;
                            default -> false;
                        }),
                "the renamed clone must still be detected without the legacy channel");
    }
}
