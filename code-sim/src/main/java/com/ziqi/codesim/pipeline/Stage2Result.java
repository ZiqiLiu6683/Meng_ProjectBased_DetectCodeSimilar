package com.ziqi.codesim.pipeline;

import java.util.List;

public record Stage2Result(
        SignalStatus status,
        List<MethodPairFeature> pairFeatures
) {
}
