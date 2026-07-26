package com.ziqi.codesim.pipeline;

import java.util.Map;
import java.util.Set;

public record Stage0Profile(
        Stage0Mode primaryMode,
        Set<Stage0Flag> flags,
        Map<String, SignalMode> enabledSignals,
        String languageA,
        String languageB,
        boolean parseOkA,
        boolean parseOkB,
        int methodCountA,
        int methodCountB,
        int classCountA,
        int classCountB,
        int totalAstNodesA,
        int totalAstNodesB,
        int maxMethodNodesA,
        int maxMethodNodesB,
        double medianMethodNodesA,
        double medianMethodNodesB,
        int nonMethodTokenCountA,
        int nonMethodTokenCountB,
        int apiCallCountA,
        int apiCallCountB,
        String routingNotes
) {
}
