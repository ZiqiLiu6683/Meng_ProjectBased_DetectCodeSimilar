package com.ziqi.codesim.semantic.feature;

import com.ziqi.codesim.semantic.model.AnalyzedMethod;
import com.ziqi.codesim.semantic.model.BasicBlockUnit;
import com.ziqi.codesim.semantic.model.CallKind;
import com.ziqi.codesim.semantic.model.ControlFlowEdge;
import com.ziqi.codesim.semantic.model.InstructionCategory;
import com.ziqi.codesim.semantic.model.InstructionUnit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SemanticFeatureExtractor {
    public MethodNumericFeatures methodFeatures(AnalyzedMethod method) {
        List<BasicBlockUnit> blocks = method.cfg().blocks();
        int branchCount = 0;
        int returnCount = 0;
        int callCount = 0;
        int internalCallCount = 0;
        int externalCallCount = 0;
        int arithmeticCount = 0;
        int logicCount = 0;
        int comparisonCount = 0;
        int assignmentCount = 0;
        int allocationCount = 0;
        int fieldAccessCount = 0;
        int arrayAccessCount = 0;
        int constantCount = 0;
        int stringReferenceCount = 0;
        int instructionCount = 0;
        Map<String, Integer> histogram = new HashMap<>();

        for (BasicBlockUnit block : blocks) {
            for (InstructionUnit instruction : block.instructions()) {
                instructionCount++;
                histogram.merge(instruction.operation(), 1, Integer::sum);
                constantCount += instruction.constants().size();
                stringReferenceCount += instruction.stringReferences().size();
                switch (instruction.category()) {
                    case BRANCH -> branchCount++;
                    case RETURN -> returnCount++;
                    case CALL -> callCount++;
                    case ARITHMETIC -> arithmeticCount++;
                    case LOGIC -> logicCount++;
                    case COMPARISON -> comparisonCount++;
                    case ASSIGNMENT -> assignmentCount++;
                    case ALLOCATION -> allocationCount++;
                    case FIELD_ACCESS -> fieldAccessCount++;
                    case ARRAY_ACCESS -> arrayAccessCount++;
                    default -> {
                    }
                }
                if (instruction.callKind() == CallKind.INTERNAL) {
                    internalCallCount++;
                } else if (instruction.callKind() == CallKind.EXTERNAL) {
                    externalCallCount++;
                }
            }
        }

        return new MethodNumericFeatures(
                blocks.size(),
                method.cfg().edges().size(),
                branchCount,
                returnCount,
                callCount,
                internalCallCount,
                externalCallCount,
                arithmeticCount,
                logicCount,
                comparisonCount,
                assignmentCount,
                allocationCount,
                fieldAccessCount,
                arrayAccessCount,
                constantCount,
                stringReferenceCount,
                instructionCount,
                method.parameterTypes().size(),
                histogram
        );
    }

    public BlockNumericFeatures blockFeatures(
            BasicBlockUnit block,
            List<ControlFlowEdge> cfgEdges) {
        int predecessorCount = 0;
        int successorCount = 0;
        for (ControlFlowEdge edge : cfgEdges) {
            if (edge.toBlockId().equals(block.blockId())) predecessorCount++;
            if (edge.fromBlockId().equals(block.blockId())) successorCount++;
        }

        int branchCount = 0;
        int returnCount = 0;
        int callCount = 0;
        int arithmeticCount = 0;
        int comparisonCount = 0;
        int assignmentCount = 0;
        int constantCount = 0;
        int stringReferenceCount = 0;
        Map<String, Integer> histogram = new HashMap<>();

        for (InstructionUnit instruction : block.instructions()) {
            histogram.merge(instruction.operation(), 1, Integer::sum);
            constantCount += instruction.constants().size();
            stringReferenceCount += instruction.stringReferences().size();
            switch (instruction.category()) {
                case BRANCH -> branchCount++;
                case RETURN -> returnCount++;
                case CALL -> callCount++;
                case ARITHMETIC -> arithmeticCount++;
                case COMPARISON -> comparisonCount++;
                case ASSIGNMENT -> assignmentCount++;
                default -> {
                }
            }
        }

        return new BlockNumericFeatures(
                block.instructions().size(),
                predecessorCount,
                successorCount,
                branchCount,
                returnCount,
                callCount,
                arithmeticCount,
                comparisonCount,
                assignmentCount,
                constantCount,
                stringReferenceCount,
                histogram
        );
    }
}
