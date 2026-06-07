package com.ziqi.codesim.next;

import java.util.List;

public record EvidencePackage(
        List<CodeRegion> leftRegions,
        List<CodeRegion> rightRegions
) {
}
