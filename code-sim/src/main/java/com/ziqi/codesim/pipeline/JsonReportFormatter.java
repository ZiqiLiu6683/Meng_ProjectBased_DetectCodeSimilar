package com.ziqi.codesim.pipeline;

import java.util.Collection;
import java.util.Map;

public class JsonReportFormatter {
    public String format(FullPipelineResult result, String fileA, String fileB) {
        Json out = new Json();
        out.beginObject();
        out.name("schemaVersion").value("1.0");
        out.name("fileA").value(fileA);
        out.name("fileB").value(fileB);
        appendSummary(out, result);
        appendStage0(out, result.stage0());
        appendStage1(out, result.stage1());
        appendStage2(out, result.stage2());
        appendStage3(out, result.stage3());
        appendStage4(out, result.stage4());
        out.endObject();
        return out.toString();
    }

    private static void appendSummary(Json out, FullPipelineResult result) {
        Stage4Result stage4 = result.stage4();
        out.name("summary").beginObject();
        out.name("cloneType").value(stage4.cloneType());
        out.name("scopeType").value(stage4.scopeType());
        out.name("containmentDirection").value(stage4.containmentDirection());
        out.name("confidence").value(stage4.confidence());
        out.name("confidenceLevel").value(stage4.confidenceLevel());
        out.name("scopeConfidence").value(stage4.scopeConfidence());
        out.name("scopeConfidenceLevel").value(stage4.scopeConfidenceLevel());
        out.name("methodPairCount").value(result.stage3().mergedPairs().size());
        out.name("mode").value(result.stage0().primaryMode());
        out.endObject();
    }

    private static void appendStage0(Json out, Stage0Profile stage0) {
        out.name("stage0").beginObject();
        out.name("primaryMode").value(stage0.primaryMode());
        out.name("flags").enumArray(stage0.flags());
        out.name("enabledSignals").beginObject();
        for (Map.Entry<String, SignalMode> entry : stage0.enabledSignals().entrySet()) {
            out.name(entry.getKey()).value(entry.getValue());
        }
        out.endObject();
        out.name("languageA").value(stage0.languageA());
        out.name("languageB").value(stage0.languageB());
        out.name("parseOkA").value(stage0.parseOkA());
        out.name("parseOkB").value(stage0.parseOkB());
        out.name("methodCountA").value(stage0.methodCountA());
        out.name("methodCountB").value(stage0.methodCountB());
        out.name("classCountA").value(stage0.classCountA());
        out.name("classCountB").value(stage0.classCountB());
        out.name("totalAstNodesA").value(stage0.totalAstNodesA());
        out.name("totalAstNodesB").value(stage0.totalAstNodesB());
        out.name("maxMethodNodesA").value(stage0.maxMethodNodesA());
        out.name("maxMethodNodesB").value(stage0.maxMethodNodesB());
        out.name("medianMethodNodesA").value(stage0.medianMethodNodesA());
        out.name("medianMethodNodesB").value(stage0.medianMethodNodesB());
        out.name("nonMethodTokenCountA").value(stage0.nonMethodTokenCountA());
        out.name("nonMethodTokenCountB").value(stage0.nonMethodTokenCountB());
        out.name("apiCallCountA").value(stage0.apiCallCountA());
        out.name("apiCallCountB").value(stage0.apiCallCountB());
        out.name("routingNotes").value(stage0.routingNotes());
        out.endObject();
    }

    private static void appendStage1(Json out, Stage1Result stage1) {
        out.name("stage1").beginObject();
        out.name("s1").value(stage1.s1());
        out.name("s5Status").value(stage1.s5Status());
        out.name("s5").value(stage1.s5());
        out.name("fileExactNormalizedMatch").value(stage1.fileExactNormalizedMatch());
        out.name("methodsA").beginArray();
        for (MethodDescriptor method : stage1.methodsA()) appendMethod(out, method);
        out.endArray();
        out.name("methodsB").beginArray();
        for (MethodDescriptor method : stage1.methodsB()) appendMethod(out, method);
        out.endArray();
        out.name("pairMatrix").beginArray();
        for (MethodPairRawScore pair : stage1.pairMatrix()) appendRawPair(out, pair);
        out.endArray();
        out.endObject();
    }

    private static void appendStage2(Json out, Stage2Result stage2) {
        out.name("stage2").beginObject();
        out.name("status").value(stage2.status());
        out.name("pairFeatures").beginArray();
        for (MethodPairFeature feature : stage2.pairFeatures()) appendPairFeature(out, feature);
        out.endArray();
        out.endObject();
    }

    private static void appendStage3(Json out, Stage3Result stage3) {
        out.name("stage3").beginObject();
        out.name("status").value(stage3.status());
        out.name("magnitudeAvg").value(stage3.magnitudeAvg());
        out.name("matchScoreAvg").value(stage3.matchScoreAvg());
        out.name("centroidS3").value(stage3.centroidS3());
        out.name("centroidS4").value(stage3.centroidS4());
        out.name("dominantTokenStructureDivergence").value(stage3.dominantTokenStructureDivergence());
        out.name("spreadAvg").value(stage3.spreadAvg());
        out.name("structuralExactnessAvg").value(stage3.structuralExactnessAvg());
        out.name("tokenExactGapAvg").value(stage3.tokenExactGapAvg());
        out.name("varianceS3").value(stage3.varianceS3());
        out.name("varianceS4").value(stage3.varianceS4());
        out.name("coverageA").value(stage3.coverageA());
        out.name("coverageB").value(stage3.coverageB());
        out.name("confirmedCoverageA").value(stage3.confirmedCoverageA());
        out.name("confirmedCoverageB").value(stage3.confirmedCoverageB());
        out.name("confirmedRatio").value(stage3.confirmedRatio());
        out.name("partialCloneSignal").value(stage3.partialCloneSignal());
        out.name("partialAInB").value(stage3.partialAInB());
        out.name("partialBInA").value(stage3.partialBInA());
        out.name("mergedPairs").beginArray();
        for (MergedPairFeature pair : stage3.mergedPairs()) appendMergedPair(out, pair);
        out.endArray();
        out.name("leastCoveredA").beginArray();
        for (MethodCoverage coverage : stage3.leastCoveredA()) appendCoverage(out, coverage);
        out.endArray();
        out.name("leastCoveredB").beginArray();
        for (MethodCoverage coverage : stage3.leastCoveredB()) appendCoverage(out, coverage);
        out.endArray();
        out.name("s1").value(stage3.s1());
        out.name("s5Status").value(stage3.s5Status());
        out.name("s5").value(stage3.s5());
        out.name("flags").enumArray(stage3.flags());
        out.endObject();
    }

    private static void appendStage4(Json out, Stage4Result stage4) {
        out.name("stage4").beginObject();
        out.name("cloneType").value(stage4.cloneType());
        out.name("scopeType").value(stage4.scopeType());
        out.name("containmentDirection").value(stage4.containmentDirection());
        out.name("confidence").value(stage4.confidence());
        out.name("confidenceLevel").value(stage4.confidenceLevel());
        out.name("scopeConfidence").value(stage4.scopeConfidence());
        out.name("scopeConfidenceLevel").value(stage4.scopeConfidenceLevel());
        out.name("evidenceStrength").value(stage4.evidenceStrength());
        out.name("evidenceConsistency").value(stage4.evidenceConsistency());
        out.name("pipelineReliability").value(stage4.pipelineReliability());
        appendTypeScores(out, stage4.typeScores());
        appendScopeScores(out, stage4.scopeScores());
        appendEvidenceChain(out, stage4.evidenceChain());
        out.name("warnings").stringArray(stage4.warnings());
        out.endObject();
    }

    private static void appendMethod(Json out, MethodDescriptor method) {
        out.beginObject();
        out.name("methodId").value(method.methodId());
        out.name("displayName").value(method.displayName());
        out.name("methodName").value(method.methodName());
        out.name("signature").value(method.signature());
        out.name("declaringType").value(method.declaringType());
        out.name("occurrenceIndex").value(method.occurrenceIndex());
        out.name("tokenCount").value(method.tokenCount());
        out.name("treeSize").value(method.treeSize());
        out.endObject();
    }

    private static void appendRawPair(Json out, MethodPairRawScore pair) {
        out.beginObject();
        out.name("methodAId").value(pair.methodAId());
        out.name("methodBId").value(pair.methodBId());
        out.name("sizeA").value(pair.sizeA());
        out.name("sizeB").value(pair.sizeB());
        out.name("s2").value(pair.s2());
        out.name("s3").value(pair.s3());
        out.name("s4").value(pair.s4());
        out.name("s4Status").value(pair.s4Status());
        out.name("tedDistance").value(pair.tedDistance());
        out.name("s3IntersectionCount").value(pair.s3IntersectionCount());
        out.name("s3CountA").value(pair.s3CountA());
        out.name("s3CountB").value(pair.s3CountB());
        out.endObject();
    }

    private static void appendPairFeature(Json out, MethodPairFeature feature) {
        out.beginObject();
        out.name("methodAId").value(feature.methodAId());
        out.name("methodBId").value(feature.methodBId());
        out.name("sizeA").value(feature.sizeA());
        out.name("sizeB").value(feature.sizeB());
        out.name("s2").value(feature.s2());
        out.name("s3").value(feature.s3());
        out.name("s4").value(feature.s4());
        out.name("s4Status").value(feature.s4Status());
        out.name("tedDistance").value(feature.tedDistance());
        out.name("magnitude").value(feature.magnitude());
        out.name("tokenStructureDivergence").value(feature.tokenStructureDivergence());
        out.name("structuralExactness").value(feature.structuralExactness());
        out.name("tokenExactGap").value(feature.tokenExactGap());
        out.name("spread").value(feature.spread());
        out.name("sizeRatio").value(feature.sizeRatio());
        out.name("containmentAInB").value(feature.containmentAInB());
        out.name("containmentBInA").value(feature.containmentBInA());
        out.name("flags").enumArray(feature.flags());
        out.endObject();
    }

    private static void appendMergedPair(Json out, MergedPairFeature pair) {
        out.beginObject();
        out.name("methodAId").value(pair.methodAId());
        out.name("methodBId").value(pair.methodBId());
        out.name("matchScore").value(pair.matchScore());
        out.name("matchReason").value(pair.matchReason());
        out.name("direction").value(pair.direction());
        out.name("confirmed").value(pair.confirmed());
        out.name("generalWeight").value(pair.generalWeight());
        out.name("feature");
        appendPairFeature(out, pair.feature());
        out.endObject();
    }

    private static void appendCoverage(Json out, MethodCoverage coverage) {
        out.beginObject();
        out.name("methodId").value(coverage.methodId());
        out.name("coverageScore").value(coverage.coverageScore());
        out.endObject();
    }

    private static void appendTypeScores(Json out, TypeScores scores) {
        out.name("typeScores").beginObject();
        out.name("t1").value(scores.t1());
        out.name("t2").value(scores.t2());
        out.name("t3").value(scores.t3());
        out.name("t4Weak").value(scores.t4Weak());
        out.name("nonClone").value(scores.nonClone());
        out.endObject();
    }

    private static void appendScopeScores(Json out, ScopeScores scores) {
        out.name("scopeScores").beginObject();
        out.name("full").value(scores.full());
        out.name("partial").value(scores.partial());
        out.name("mixed").value(scores.mixed());
        out.endObject();
    }

    private static void appendEvidenceChain(Json out, EvidenceChain evidence) {
        out.name("evidenceChain").beginObject();
        out.name("supportingEvidence").beginArray();
        for (EvidenceItem item : evidence.supportingEvidence()) appendEvidenceItem(out, item);
        out.endArray();
        out.name("opposingEvidence").beginArray();
        for (EvidenceItem item : evidence.opposingEvidence()) appendEvidenceItem(out, item);
        out.endArray();
        out.name("scopeEvidence").beginArray();
        for (EvidenceItem item : evidence.scopeEvidence()) appendEvidenceItem(out, item);
        out.endArray();
        out.name("reliabilityWarnings").beginArray();
        for (EvidenceItem item : evidence.reliabilityWarnings()) appendEvidenceItem(out, item);
        out.endArray();
        out.endObject();
    }

    private static void appendEvidenceItem(Json out, EvidenceItem item) {
        out.beginObject();
        out.name("category").value(item.category());
        out.name("signal").value(item.signal());
        out.name("value").value(item.value());
        out.name("interpretation").value(item.interpretation());
        out.name("supports").value(item.supports());
        out.name("strength").value(item.strength());
        out.endObject();
    }

    private static final class Json {
        private final StringBuilder text = new StringBuilder();
        private final boolean[] first = new boolean[128];
        private int depth = -1;
        private boolean afterName;

        Json beginObject() {
            beforeValue();
            text.append('{');
            first[++depth] = true;
            return this;
        }

        Json endObject() {
            text.append('}');
            depth--;
            return this;
        }

        Json beginArray() {
            beforeValue();
            text.append('[');
            first[++depth] = true;
            return this;
        }

        Json endArray() {
            text.append(']');
            depth--;
            return this;
        }

        Json name(String name) {
            beforeValue();
            string(name);
            text.append(':');
            afterName = true;
            return this;
        }

        Json value(String value) {
            beforeValue();
            if (value == null) {
                text.append("null");
            } else {
                string(value);
            }
            return this;
        }

        Json value(Enum<?> value) {
            return value(value == null ? null : value.name());
        }

        Json value(boolean value) {
            beforeValue();
            text.append(value);
            return this;
        }

        Json value(int value) {
            beforeValue();
            text.append(value);
            return this;
        }

        Json value(double value) {
            beforeValue();
            if (Double.isFinite(value)) {
                text.append(Double.toString(value));
            } else {
                text.append("null");
            }
            return this;
        }

        Json enumArray(Collection<? extends Enum<?>> values) {
            beginArray();
            for (Enum<?> value : values) value(value);
            endArray();
            return this;
        }

        Json stringArray(Collection<String> values) {
            beginArray();
            for (String value : values) value(value);
            endArray();
            return this;
        }

        private void beforeValue() {
            if (afterName) {
                afterName = false;
                return;
            }
            if (depth >= 0) {
                if (first[depth]) {
                    first[depth] = false;
                } else {
                    text.append(',');
                }
            }
        }

        private void string(String value) {
            text.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> text.append("\\\"");
                    case '\\' -> text.append("\\\\");
                    case '\b' -> text.append("\\b");
                    case '\f' -> text.append("\\f");
                    case '\n' -> text.append("\\n");
                    case '\r' -> text.append("\\r");
                    case '\t' -> text.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            text.append(String.format("\\u%04x", (int) c));
                        } else {
                            text.append(c);
                        }
                    }
                }
            }
            text.append('"');
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }
}
