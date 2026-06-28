package com.ziqi.codesim.semantic.model;

import java.util.List;

public record InstructionUnit(
        String instructionId,
        int ordinal,
        String operation,
        InstructionCategory category,
        CallKind callKind,
        List<String> definedValues,
        List<String> usedValues,
        List<String> constants,
        List<String> stringReferences,
        // Declared target signature for call instructions (empty for non-calls). Used to
        // derive call-graph in-degree (incoming calls) at the program level.
        String callTargetSignature
) {
    public InstructionUnit {
        definedValues = List.copyOf(definedValues);
        usedValues = List.copyOf(usedValues);
        constants = List.copyOf(constants);
        stringReferences = List.copyOf(stringReferences);
        callTargetSignature = callTargetSignature == null ? "" : callTargetSignature;
    }

    // Backward-compatible constructor for callers that do not supply a call target.
    public InstructionUnit(
            String instructionId,
            int ordinal,
            String operation,
            InstructionCategory category,
            CallKind callKind,
            List<String> definedValues,
            List<String> usedValues,
            List<String> constants,
            List<String> stringReferences) {
        this(instructionId, ordinal, operation, category, callKind,
                definedValues, usedValues, constants, stringReferences, "");
    }
}
