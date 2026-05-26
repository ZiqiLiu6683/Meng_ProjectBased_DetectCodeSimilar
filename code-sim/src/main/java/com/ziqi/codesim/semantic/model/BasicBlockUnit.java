package com.ziqi.codesim.semantic.model;

import java.util.List;

public record BasicBlockUnit(
        String blockId,
        int ordinal,
        boolean entry,
        boolean exit,
        List<InstructionUnit> instructions
) {
    public BasicBlockUnit {
        instructions = List.copyOf(instructions);
    }
}
