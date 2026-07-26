package com.ziqi.codesim.pipeline;

public record PipelineResult(
        Stage0Profile stage0,
        Stage1Result stage1,
        Stage2Result stage2,
        Stage3Result stage3
) {
}
