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
        List<String> stringReferences
) {
    public InstructionUnit {
        definedValues = List.copyOf(definedValues);
        usedValues = List.copyOf(usedValues);
        constants = List.copyOf(constants);
        stringReferences = List.copyOf(stringReferences);
    }
}
