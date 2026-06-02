package com.ziqi.codesim.semantic.discovre;

import com.ziqi.codesim.semantic.model.AnalyzedMethod;
import com.ziqi.codesim.semantic.model.BasicBlockUnit;
import com.ziqi.codesim.semantic.model.CallKind;
import com.ziqi.codesim.semantic.model.ControlFlowEdge;
import com.ziqi.codesim.semantic.model.ControlFlowGraphUnit;
import com.ziqi.codesim.semantic.model.InstructionCategory;
import com.ziqi.codesim.semantic.model.InstructionUnit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscovreCfgMatcherTest {
    @Test
    void identicalCfgHasZeroDistance() {
        AnalyzedMethod left = branchingMethod("left", "b1", "b2", "b3");
        AnalyzedMethod right = branchingMethod("right", "x1", "x2", "x3");

        DiscovreCfgMatchResult result = new DiscovreCfgMatcher().match(left, right);

        assertEquals(0.0, result.distance(), 0.0001);
        assertEquals(1.0, result.similarity(), 0.0001);
        assertEquals(3, result.matchedBlockCount());
    }

    @Test
    void edgeMismatchIncreasesDistance() {
        AnalyzedMethod left = branchingMethod("left", "b1", "b2", "b3");
        AnalyzedMethod right = linearMethod("right", "x1", "x2", "x3");

        DiscovreCfgMatchResult result = new DiscovreCfgMatcher().match(left, right);

        assertTrue(result.distance() > 0.0);
        assertTrue(result.matchedBlockCount() < 3);
    }

    @Test
    void differentBlockFeaturesIncreaseDistance() {
        AnalyzedMethod left = branchingMethod("left", "b1", "b2", "b3");
        AnalyzedMethod right = method("right", List.of(
                block("x1", true, false, instruction("i1", InstructionCategory.BRANCH)),
                block("x2", false, false, instruction("i2", InstructionCategory.CALL),
                        instruction("i3", InstructionCategory.RETURN)),
                block("x3", false, true, instruction("i4", InstructionCategory.CALL),
                        instruction("i5", InstructionCategory.RETURN))
        ), List.of(
                new ControlFlowEdge("x1", "x2", "normal"),
                new ControlFlowEdge("x1", "x3", "normal")
        ));

        DiscovreCfgMatchResult result = new DiscovreCfgMatcher().match(left, right);

        assertTrue(result.distance() > 0.0);
        assertTrue(result.similarity() < 1.0);
    }

    @Test
    void constrainedMatcherUsesAllowedBlockPairs() {
        AnalyzedMethod left = branchingMethod("left", "b1", "b2", "b3");
        AnalyzedMethod right = branchingMethod("right", "x1", "x2", "x3");

        DiscovreCfgMatchResult complete = new DiscovreCfgMatcher().match(left, right, Map.of(
                "b1", Set.of("x1"),
                "b2", Set.of("x2"),
                "b3", Set.of("x3")
        ));
        DiscovreCfgMatchResult incomplete = new DiscovreCfgMatcher().match(left, right, Map.of(
                "b1", Set.of("x1"),
                "b2", Set.of("x2")
        ));

        assertEquals(0.0, complete.distance(), 0.0001);
        assertTrue(incomplete.distance() > complete.distance());
        assertTrue(incomplete.matchedBlockCount() < complete.matchedBlockCount());
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
                category == InstructionCategory.CALL ? CallKind.INTERNAL : CallKind.NOT_A_CALL,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
