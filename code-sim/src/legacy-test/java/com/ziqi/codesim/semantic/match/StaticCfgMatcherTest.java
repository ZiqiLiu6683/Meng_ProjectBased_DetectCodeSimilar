package com.ziqi.codesim.semantic.match;

import com.ziqi.codesim.semantic.model.AnalyzedMethod;
import com.ziqi.codesim.semantic.model.BasicBlockUnit;
import com.ziqi.codesim.semantic.model.CallKind;
import com.ziqi.codesim.semantic.model.ControlFlowEdge;
import com.ziqi.codesim.semantic.model.ControlFlowGraphUnit;
import com.ziqi.codesim.semantic.model.InstructionCategory;
import com.ziqi.codesim.semantic.model.InstructionUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticCfgMatcherTest {
    @Test
    void givesHighScoreWhenBlockFeaturesAndCfgEdgesArePreserved() {
        AnalyzedMethod left = branchingMethod("left", "b1", "b2", "b3");
        AnalyzedMethod right = branchingMethod("right", "x1", "x2", "x3");

        MethodCfgMatchResult result = new StaticCfgMatcher().match(left, right);

        assertEquals(3, result.blockMatches().size());
        assertEquals(1.0, result.blockSimilarity(), 0.0001);
        assertEquals(1.0, result.edgePreservation(), 0.0001);
        assertTrue(result.overallSimilarity() > 0.95);
    }

    @Test
    void penalizesSimilarBlockCountsWhenControlFlowEdgesDoNotMatch() {
        AnalyzedMethod left = branchingMethod("left", "b1", "b2", "b3");
        AnalyzedMethod right = linearMethod("right", "x1", "x2", "x3");

        MethodCfgMatchResult result = new StaticCfgMatcher().match(left, right);

        assertEquals(3, result.blockMatches().size());
        assertTrue(result.blockSimilarity() > 0.65);
        assertTrue(result.edgePreservation() < 0.75);
        assertTrue(result.overallSimilarity() < 0.95);
    }

    private static AnalyzedMethod branchingMethod(
            String id,
            String entry,
            String thenBlock,
            String elseBlock) {
        return method(id, List.of(
                block(entry, true, false, instruction("i1", InstructionCategory.BRANCH)),
                block(thenBlock, false, false, instruction("i2", InstructionCategory.ARITHMETIC),
                        instruction("i3", InstructionCategory.RETURN)),
                block(elseBlock, false, true, instruction("i4", InstructionCategory.ARITHMETIC),
                        instruction("i5", InstructionCategory.RETURN))
        ), List.of(
                new ControlFlowEdge(entry, thenBlock, "normal"),
                new ControlFlowEdge(entry, elseBlock, "normal")
        ));
    }

    private static AnalyzedMethod linearMethod(
            String id,
            String first,
            String second,
            String third) {
        return method(id, List.of(
                block(first, true, false, instruction("i1", InstructionCategory.BRANCH)),
                block(second, false, false, instruction("i2", InstructionCategory.ARITHMETIC),
                        instruction("i3", InstructionCategory.RETURN)),
                block(third, false, true, instruction("i4", InstructionCategory.ARITHMETIC),
                        instruction("i5", InstructionCategory.RETURN))
        ), List.of(
                new ControlFlowEdge(first, second, "normal"),
                new ControlFlowEdge(second, third, "normal")
        ));
    }

    private static AnalyzedMethod method(
            String id,
            List<BasicBlockUnit> blocks,
            List<ControlFlowEdge> edges) {
        return new AnalyzedMethod(
                id,
                "LExample",
                id + "()I",
                "I",
                List.of(),
                new ControlFlowGraphUnit(blocks, edges)
        );
    }

    private static BasicBlockUnit block(
            String id,
            boolean entry,
            boolean exit,
            InstructionUnit... instructions) {
        return new BasicBlockUnit(id, 0, entry, exit, List.of(instructions));
    }

    private static InstructionUnit instruction(String id, InstructionCategory category) {
        return new InstructionUnit(
                id,
                0,
                category.name(),
                category,
                CallKind.NOT_A_CALL,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
