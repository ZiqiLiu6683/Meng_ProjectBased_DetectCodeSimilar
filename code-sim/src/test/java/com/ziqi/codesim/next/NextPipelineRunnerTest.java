package com.ziqi.codesim.next;

import com.ziqi.codesim.semantic.knn.KnnFeatureView;
import com.ziqi.codesim.semantic.raw.RawToolBlock;
import com.ziqi.codesim.semantic.raw.RawToolInstruction;
import com.ziqi.codesim.semantic.raw.RawToolMethod;
import com.ziqi.codesim.semantic.raw.RawToolRecord;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

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

    @Test
    void discoversStatementWindowCandidateInsideLargerMethod() {
        String left = """
                class A {
                  int score(int a, int b) {
                    int total = a + b;
                    total = total * 2;
                    return total;
                  }
                }
                """;
        String right = """
                class B {
                  int wrapper(int x, int y, boolean enabled) {
                    if (!enabled) {
                      return 0;
                    }
                    int value = x + y;
                    value = value * 2;
                    return value;
                  }
                }
                """;

        NextPipelineResult result = runner.run(left, right);

        assertTrue(result.evidencePackage().leftRegions().stream()
                .anyMatch(r -> r.kind() == RegionKind.STATEMENT_WINDOW_REGION));
        assertTrue(result.evidencePackage().rightRegions().stream()
                .anyMatch(r -> r.kind() == RegionKind.STATEMENT_WINDOW_REGION));
        assertTrue(result.regionDecisions().stream()
                .anyMatch(d -> d.type() == CloneRegionType.T2
                        && d.candidate().left().kind() == RegionKind.STATEMENT_WINDOW_REGION
                        && d.candidate().right().kind() == RegionKind.STATEMENT_WINDOW_REGION));
    }

    @Test
    void buildsControlAndBlockSequenceRegions() {
        String left = """
                class A {
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
                class B {
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

        NextPipelineResult result = runner.run(left, right);

        assertTrue(result.evidencePackage().leftRegions().stream()
                .anyMatch(r -> r.kind() == RegionKind.CONTROL_REGION));
        assertTrue(result.evidencePackage().leftRegions().stream()
                .anyMatch(r -> r.kind() == RegionKind.BLOCK_SEQUENCE_REGION));
    }

    @Test
    void mergesCfgKnnCandidateSignalsIntoStage2Candidates() {
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
                  int count(int[] items) {
                    int result = 0;
                    for (int item : items) {
                      result += item;
                    }
                    return result;
                  }
                }
                """;
        RawToolCfgCandidateProvider provider = new RawToolCfgCandidateProvider(
                List.of(rawMethod("A", "count(int[])", "v1 = phi", "return v1")),
                List.of(rawMethod("B", "count(int[])", "v2 = phi", "return v2")),
                Set.of("instruction.rawInstructionText", "instruction.rawInstructionClassName"),
                KnnFeatureView.RAW_HASH_BUCKET,
                1
        );
        NextPipelineRunner cfgRunner = new NextPipelineRunner(List.of(provider));

        NextPipelineResult result = cfgRunner.run(left, right);

        assertTrue(result.candidates().stream()
                .flatMap(c -> c.sources().stream())
                .anyMatch(s -> s.channel().equals("CFG_KNN_SCAN")));
    }

    @Test
    void ranksMethodAndMultiSourceCandidatesBeforeSmallWindows() {
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
                  int count(int[] items) {
                    int result = 0;
                    for (int item : items) {
                      result += item;
                    }
                    return result;
                  }
                }
                """;
        RawToolCfgCandidateProvider provider = new RawToolCfgCandidateProvider(
                List.of(rawMethod("A", "count(int[])", "v1 = phi", "return v1")),
                List.of(rawMethod("B", "count(int[])", "v2 = phi", "return v2")),
                Set.of("instruction.rawInstructionText", "instruction.rawInstructionClassName"),
                KnnFeatureView.RAW_HASH_BUCKET,
                1
        );
        NextPipelineResult result = new NextPipelineRunner(List.of(provider)).run(left, right);

        RankedRegionCandidate top = result.rankedCandidates().get(0);

        assertEquals(RegionKind.METHOD, top.candidate().left().kind());
        assertEquals(RegionKind.METHOD, top.candidate().right().kind());
        assertTrue(top.candidate().sources().stream()
                .anyMatch(source -> source.channel().equals("CFG_KNN_SCAN")));
        assertTrue(top.rankingReasons().stream()
                .anyMatch(reason -> reason.equals("cfg_knn_candidate")));
    }

    @Test
    void aggregatesFileLevelSummaryFromRegionDecisions() {
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

        NextPipelineResult result = runner.run(left, right);

        assertEquals(CloneRegionType.T2, result.fileSummary().dominantRegionType());
        assertTrue(result.fileSummary().overallRelationship() == FileRelationship.FULL_FILE_T2
                || result.fileSummary().overallRelationship() == FileRelationship.PARTIAL_T2);
        assertEquals(RelationshipShape.PARTIAL_OVERLAP, result.fileSummary().relationshipShape());
        assertEquals(InspectionPriority.MEDIUM, result.fileSummary().inspectionPriority());
        assertTrue(result.fileSummary().matchedCoverageLeft() > 0.0);
        assertTrue(result.fileSummary().matchedCoverageRight() > 0.0);
        assertTrue(result.fileSummary().evidenceBreakdown().stream()
                .anyMatch(breakdown -> breakdown.type() == CloneRegionType.T2
                        && breakdown.regionCount() > 0
                        && breakdown.affectedLeftRatio() > 0.0
                        && breakdown.affectedRightRatio() > 0.0));
        assertTrue(result.fileSummary().fileTags().contains(RegionTag.RENAMING_DETECTED));
    }

    @Test
    void reportsMixedFileRelationshipWhenDifferentCloneTypesAppear() {
        String left = """
                class A {
                  int exact(int a) {
                    return a + 1;
                  }

                  int changed(int[] values) {
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
                  int exact(int a) {
                    return a + 1;
                  }

                  int changed(int[] items) {
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

        NextPipelineResult result = runner.run(left, right);

        assertEquals(FileRelationship.MIXED_CLONE_TYPES, result.fileSummary().overallRelationship());
        assertTrue(result.fileSummary().regionTypeCounts().get(CloneRegionType.T1) > 0);
        assertTrue(result.fileSummary().regionTypeCounts().get(CloneRegionType.T3) > 0);
    }

    @Test
    void suppressesOverlappingAcceptedRegionsBeforeFileAggregation() {
        String left = """
                class A {
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
                class B {
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

        NextPipelineResult result = runner.run(left, right);

        assertTrue(result.regionSelectionSummary().acceptedRegionCount()
                >= result.regionSelectionSummary().selectedRegionCount());
        assertTrue(result.regionSelectionSummary().suppressedRegionCount() > 0);
        assertTrue(result.fileSummary().regionTypeCoverage().values().stream()
                .allMatch(value -> value <= 1.0));
    }

    @Test
    void doesNotPromoteSourceOnlyLocalWindowsToT3WithoutComparableUnitEvidence() {
        String left = """
                class A {
                  int sum(int[] values) {
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
                  boolean prime(int value) {
                    if (value < 2) {
                      return false;
                    }
                    for (int divisor = 2; divisor < value; divisor++) {
                      if (value % divisor == 0) {
                        return false;
                      }
                    }
                    return true;
                  }
                }
                """;

        NextPipelineResult result = runner.run(left, right);

        assertFalse(result.selectedRegionDecisions().stream()
                .anyMatch(decision -> decision.type() == CloneRegionType.T3
                        && decision.candidate().left().kind() == RegionKind.STATEMENT_WINDOW_REGION
                        && decision.candidate().right().kind() == RegionKind.STATEMENT_WINDOW_REGION));
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

    private static RawToolMethod rawMethod(String declaringClass,
                                           String signature,
                                           String firstInstruction,
                                           String secondInstruction) {
        return new RawToolMethod(
                declaringClass + "." + signature,
                signature,
                declaringClass,
                "int",
                List.of("int[]"),
                "",
                "",
                "",
                List.of(
                        rawBlock(1, firstInstruction),
                        rawBlock(2, secondInstruction)
                ),
                List.of()
        );
    }

    private static RawToolBlock rawBlock(int number, String instructionText) {
        return new RawToolBlock(
                "BB" + number,
                number,
                number == 1,
                number == 2,
                number == 1 ? List.of("BB2") : List.of(),
                List.of(),
                number == 2 ? List.of("BB1") : List.of(),
                List.of(rawInstruction(number, instructionText)),
                List.of(new RawToolRecord("block.rawNormalSuccessor", number == 1 ? "BB2" : "", List.of("test")))
        );
    }

    private static RawToolInstruction rawInstruction(int index, String instructionText) {
        return new RawToolInstruction(
                "TestInstruction",
                instructionText,
                index,
                List.of("v" + index),
                List.of("u" + index),
                "",
                List.of(
                        new RawToolRecord("instruction.rawInstructionText", instructionText, List.of("test")),
                        new RawToolRecord("instruction.rawInstructionClassName", "TestInstruction", List.of("test"))
                )
        );
    }
}
