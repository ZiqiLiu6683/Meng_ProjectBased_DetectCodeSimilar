package com.ziqi.codesim.semantic.feature;

import com.ziqi.codesim.semantic.model.AnalyzedMethod;
import com.ziqi.codesim.semantic.model.BasicBlockUnit;
import com.ziqi.codesim.semantic.model.CallKind;
import com.ziqi.codesim.semantic.model.ControlFlowEdge;
import com.ziqi.codesim.semantic.model.InstructionCategory;
import com.ziqi.codesim.semantic.model.InstructionUnit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                countLoopComponents(blocks, method.cfg().edges()),
                method.localValueCount(),
                histogram
        );
    }

    // discovRE uses the number of strongly-connected components as a loop estimate. We count
    // non-trivial SCCs (size > 1) plus single blocks with a self-loop, via Tarjan's algorithm.
    private static int countLoopComponents(List<BasicBlockUnit> blocks, List<ControlFlowEdge> edges) {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (BasicBlockUnit block : blocks) {
            adjacency.put(block.blockId(), new ArrayList<>());
        }
        Set<String> selfLoops = new HashSet<>();
        for (ControlFlowEdge edge : edges) {
            List<String> successors = adjacency.get(edge.fromBlockId());
            if (successors == null || !adjacency.containsKey(edge.toBlockId())) {
                continue;
            }
            successors.add(edge.toBlockId());
            if (edge.fromBlockId().equals(edge.toBlockId())) {
                selfLoops.add(edge.fromBlockId());
            }
        }
        int loops = 0;
        for (List<String> component : new TarjanScc(adjacency).components()) {
            if (component.size() > 1 || selfLoops.contains(component.get(0))) {
                loops++;
            }
        }
        return loops;
    }

    private static final class TarjanScc {
        private final Map<String, List<String>> adjacency;
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> lowLink = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final Set<String> onStack = new HashSet<>();
        private final List<List<String>> components = new ArrayList<>();
        private int counter = 0;

        TarjanScc(Map<String, List<String>> adjacency) {
            this.adjacency = adjacency;
        }

        List<List<String>> components() {
            for (String node : adjacency.keySet()) {
                if (!index.containsKey(node)) {
                    strongConnect(node);
                }
            }
            return components;
        }

        private void strongConnect(String v) {
            index.put(v, counter);
            lowLink.put(v, counter);
            counter++;
            stack.push(v);
            onStack.add(v);
            for (String w : adjacency.get(v)) {
                if (!index.containsKey(w)) {
                    strongConnect(w);
                    lowLink.put(v, Math.min(lowLink.get(v), lowLink.get(w)));
                } else if (onStack.contains(w)) {
                    lowLink.put(v, Math.min(lowLink.get(v), index.get(w)));
                }
            }
            if (lowLink.get(v).equals(index.get(v))) {
                List<String> component = new ArrayList<>();
                String w;
                do {
                    w = stack.pop();
                    onStack.remove(w);
                    component.add(w);
                } while (!w.equals(v));
                components.add(component);
            }
        }
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
