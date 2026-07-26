package com.ziqi.codesim.pipeline;

import java.util.List;

public record Stage1Result(
        double s1,
        SignalStatus s5Status,
        double s5,
        boolean fileExactNormalizedMatch,
        List<MethodDescriptor> methodsA,
        List<MethodDescriptor> methodsB,
        List<MethodPairRawScore> pairMatrix
) {
}
