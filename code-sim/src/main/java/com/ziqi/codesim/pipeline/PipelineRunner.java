package com.ziqi.codesim.pipeline;

public class PipelineRunner {
    private final Stage0Profiler stage0Profiler;
    private final Stage1Measurement stage1Measurement;
    private final Stage2FeatureExtractor stage2FeatureExtractor;
    private final Stage3Aggregator stage3Aggregator;

    public PipelineRunner() {
        this(new Stage0Profiler(),
                new Stage1Measurement(),
                new Stage2FeatureExtractor(),
                new Stage3Aggregator());
    }

    PipelineRunner(Stage0Profiler stage0Profiler,
                   Stage1Measurement stage1Measurement,
                   Stage2FeatureExtractor stage2FeatureExtractor,
                   Stage3Aggregator stage3Aggregator) {
        this.stage0Profiler = stage0Profiler;
        this.stage1Measurement = stage1Measurement;
        this.stage2FeatureExtractor = stage2FeatureExtractor;
        this.stage3Aggregator = stage3Aggregator;
    }

    public PipelineResult run(String sourceA, String sourceB) {
        Stage0Profile stage0 = stage0Profiler.profile(sourceA, sourceB);
        if (!stage0.parseOkA() || !stage0.parseOkB()) {
            Stage1Result stage1 = emptyStage1();
            Stage2Result stage2 = new Stage2Result(SignalStatus.NOT_APPLICABLE, java.util.List.of());
            Stage3Result stage3 = stage3Aggregator.compute(stage1, stage2);
            return new PipelineResult(stage0, stage1, stage2, stage3);
        }

        Stage1Result stage1 = stage1Measurement.compute(sourceA, sourceB);
        Stage2Result stage2 = stage2FeatureExtractor.compute(stage1);
        Stage3Result stage3 = stage3Aggregator.compute(stage1, stage2);
        return new PipelineResult(stage0, stage1, stage2, stage3);
    }

    private static Stage1Result emptyStage1() {
        return new Stage1Result(
                0.0,
                SignalStatus.NOT_APPLICABLE,
                -1.0,
                false,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()
        );
    }
}
