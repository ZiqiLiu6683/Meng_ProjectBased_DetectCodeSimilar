package com.ziqi;

import com.ziqi.codesim.semantic.feature.MethodNumericFeatures;
import com.ziqi.codesim.semantic.feature.NumericFeatureDistance;
import com.ziqi.codesim.semantic.feature.SemanticFeatureExtractor;
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

public class SemanticFeatureExtractorTest {
    @Test
    void extractsDiscivreStyleNumericFeaturesFromSharedModel() {
        BasicBlockUnit entry = new BasicBlockUnit("b0", 0, true, false, List.of(
                instruction("i0", 0, "assign", InstructionCategory.ASSIGNMENT),
                instruction("i1", 1, "cmp_gt", InstructionCategory.COMPARISON),
                instruction("i2", 2, "branch", InstructionCategory.BRANCH)
        ));
        BasicBlockUnit exit = new BasicBlockUnit("b1", 1, false, true, List.of(
                instruction("i3", 3, "add", InstructionCategory.ARITHMETIC),
                instruction("i4", 4, "return", InstructionCategory.RETURN)
        ));
        AnalyzedMethod method = new AnalyzedMethod(
                "A#m",
                "A",
                "m(int)",
                "int",
                List.of("int"),
                new ControlFlowGraphUnit(
                        List.of(entry, exit),
                        List.of(new ControlFlowEdge("b0", "b1", "true"))
                )
        );

        MethodNumericFeatures features = new SemanticFeatureExtractor().methodFeatures(method);

        assertEquals(2, features.basicBlockCount());
        assertEquals(1, features.cfgEdgeCount());
        assertEquals(1, features.branchCount());
        assertEquals(1, features.returnCount());
        assertEquals(1, features.arithmeticCount());
        assertEquals(1, features.comparisonCount());
        assertEquals(1, features.assignmentCount());
        assertEquals(5, features.instructionCount());
        assertEquals(1, features.parameterCount());
        assertTrue(features.operationHistogram().containsKey("branch"));
        assertEquals(0.0, new NumericFeatureDistance().methodDistance(features, features), 1e-9);
    }

    private static InstructionUnit instruction(
            String id,
            int ordinal,
            String operation,
            InstructionCategory category) {
        return new InstructionUnit(
                id,
                ordinal,
                operation,
                category,
                CallKind.NOT_A_CALL,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
