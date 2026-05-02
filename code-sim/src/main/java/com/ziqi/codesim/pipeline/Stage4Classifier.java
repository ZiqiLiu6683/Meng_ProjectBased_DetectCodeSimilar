package com.ziqi.codesim.pipeline;

import java.util.ArrayList;
import java.util.List;

public class Stage4Classifier {
    private static final double HIGH = 0.75;
    private static final double MEDIUM = 0.45;
    private static final double LOW = 0.30;
    private static final double DIRECTION_MARGIN = 0.10;

    public Stage4Result classify(Stage0Profile stage0, Stage1Result stage1, Stage3Result stage3) {
        ComponentScores components = components(stage0, stage3);
        ScopeScores scopeScores = scopeScores(stage3);
        ScopeType scopeType = chooseScope(stage3, scopeScores);
        ContainmentDirection containmentDirection = chooseContainmentDirection(stage3, scopeType);
        TypeScores typeScores = typeScores(stage1, stage3, components);
        CloneType cloneType = chooseCloneType(stage0, stage1, stage3, components, typeScores);

        double evidenceStrength = evidenceStrength(cloneType, typeScores);
        double evidenceConsistency = evidenceConsistency(scopeType, stage3);
        double pipelineReliability = pipelineReliability(stage0, stage3);
        double confidence = clamp(evidenceStrength * evidenceConsistency * pipelineReliability, 0.0, 1.0);
        if (cloneType == CloneType.T4_WEAK) {
            confidence = Math.min(confidence, 0.70);
        }

        double scopeConfidence = scopeConfidence(scopeType, scopeScores);
        EvidenceChain evidenceChain = evidenceChain(cloneType, scopeType, stage0, stage3, components);
        List<String> warnings = evidenceChain.reliabilityWarnings().stream()
                .map(EvidenceItem::interpretation)
                .toList();

        return new Stage4Result(
                cloneType,
                scopeType,
                containmentDirection,
                confidence,
                level(confidence),
                scopeConfidence,
                level(scopeConfidence),
                evidenceStrength,
                evidenceConsistency,
                pipelineReliability,
                typeScores,
                scopeScores,
                evidenceChain,
                warnings
        );
    }

    private static ComponentScores components(Stage0Profile stage0, Stage3Result stage3) {
        double methodSimilarityStrength = avg(
                stage3.matchScoreAvg(),
                stage3.magnitudeAvg(),
                stage3.centroidS3(),
                stage3.centroidS4()
        );
        double coverageStrength = avg(stage3.coverageA(), stage3.coverageB());
        double symmetryStrength = avg(
                stage3.confirmedCoverageA(),
                stage3.confirmedCoverageB(),
                stage3.confirmedRatio()
        );
        double partialStrength = avg(
                stage3.partialCloneSignal(),
                Math.max(stage3.partialAInB(), stage3.partialBInA()),
                Math.abs(stage3.coverageA() - stage3.coverageB())
        );
        double apiSemanticStrength = stage3.s5Status() == SignalStatus.APPLICABLE
                ? stage3.s5()
                : -1.0;
        double s1Reliable = stage0.flags().contains(Stage0Flag.CLASS_CONTEXT_WEAK)
                ? 0.0
                : stage3.s1();
        double exactStructureStrength = stage3.structuralExactnessAvg();
        double modificationStrength = avg(stage3.tokenExactGapAvg(), stage3.spreadAvg());
        return new ComponentScores(
                methodSimilarityStrength,
                coverageStrength,
                symmetryStrength,
                partialStrength,
                apiSemanticStrength,
                s1Reliable,
                exactStructureStrength,
                modificationStrength
        );
    }

    private static ScopeScores scopeScores(Stage3Result stage3) {
        double full = avg(
                stage3.coverageA(),
                stage3.coverageB(),
                stage3.confirmedCoverageA(),
                stage3.confirmedCoverageB(),
                stage3.confirmedRatio()
        ) * (1.0 - stage3.partialCloneSignal());
        double partial = avg(
                stage3.partialCloneSignal(),
                Math.abs(stage3.coverageA() - stage3.coverageB()),
                Math.max(stage3.partialAInB(), stage3.partialBInA())
        );
        double mixed = avg(stage3.varianceS3(), stage3.varianceS4(), stage3.spreadAvg());
        return new ScopeScores(clamp(full), clamp(partial), clamp(mixed));
    }

    private static ScopeType chooseScope(Stage3Result stage3, ScopeScores scores) {
        if (stage3.status() == SignalStatus.NOT_APPLICABLE) {
            return ScopeType.UNKNOWN;
        }
        if (scores.partial() >= MEDIUM && scores.partial() > scores.full()) {
            return ScopeType.PARTIAL;
        }
        if (scores.full() >= MEDIUM && scores.full() >= scores.mixed()) {
            return ScopeType.FULL;
        }
        if (scores.mixed() >= MEDIUM) {
            return ScopeType.MIXED;
        }
        return ScopeType.UNKNOWN;
    }

    private static ContainmentDirection chooseContainmentDirection(Stage3Result stage3, ScopeType scopeType) {
        if (scopeType != ScopeType.PARTIAL) {
            return ContainmentDirection.NONE;
        }
        if (stage3.partialAInB() > stage3.partialBInA() + DIRECTION_MARGIN) {
            return ContainmentDirection.A_IN_B;
        }
        if (stage3.partialBInA() > stage3.partialAInB() + DIRECTION_MARGIN) {
            return ContainmentDirection.B_IN_A;
        }
        return ContainmentDirection.UNKNOWN;
    }

    private static TypeScores typeScores(Stage1Result stage1, Stage3Result stage3,
                                         ComponentScores c) {
        double t1 = stage1.fileExactNormalizedMatch() ? 1.0 : 0.0;
        double t2 = avg(
                c.methodSimilarityStrength(),
                c.exactStructureStrength(),
                1.0 - c.modificationStrength(),
                c.coverageStrength()
        );
        double t3 = avg(
                c.methodSimilarityStrength(),
                stage3.centroidS4(),
                c.modificationStrength(),
                c.coverageStrength()
        );
        double t4Weak = c.apiSemanticStrength() < 0.0
                ? 0.0
                : avg(c.apiSemanticStrength(), 1.0 - c.methodSimilarityStrength(), c.s1Reliable());
        double nonClone = avg(
                1.0 - c.methodSimilarityStrength(),
                1.0 - c.coverageStrength(),
                1.0 - c.partialStrength(),
                c.apiSemanticStrength() >= 0.0 ? 1.0 - c.apiSemanticStrength() : 0.5
        );
        return new TypeScores(clamp(t1), clamp(t2), clamp(t3), clamp(t4Weak), clamp(nonClone));
    }

    private static CloneType chooseCloneType(Stage0Profile stage0, Stage1Result stage1,
                                             Stage3Result stage3, ComponentScores components,
                                             TypeScores scores) {
        if (!stage0.parseOkA() || !stage0.parseOkB()
                || stage0.primaryMode() == Stage0Mode.PARSE_FAILED) {
            return CloneType.INCONCLUSIVE;
        }
        if (stage1.fileExactNormalizedMatch()) {
            return CloneType.T1;
        }
        boolean methodEvidenceUsable = stage3.status() == SignalStatus.COMPUTED;
        if (methodEvidenceUsable
                && components.methodSimilarityStrength() >= MEDIUM
                && components.coverageStrength() >= LOW) {
            return scores.t2() >= scores.t3() ? CloneType.T2 : CloneType.T3;
        }
        if (scores.t4Weak() >= HIGH
                && components.methodSimilarityStrength() < HIGH) {
            return CloneType.T4_WEAK;
        }
        if (scores.nonClone() >= HIGH) {
            return CloneType.NON_CLONE;
        }
        return CloneType.INCONCLUSIVE;
    }

    private static double evidenceStrength(CloneType cloneType, TypeScores scores) {
        return switch (cloneType) {
            case T1 -> scores.t1();
            case T2 -> scores.t2();
            case T3 -> scores.t3();
            case T4_WEAK -> scores.t4Weak();
            case NON_CLONE -> scores.nonClone();
            case INCONCLUSIVE -> 0.35;
        };
    }

    private static double evidenceConsistency(ScopeType scopeType, Stage3Result stage3) {
        double spreadPenalty = scopeType == ScopeType.FULL ? 0.50 : 0.35;
        double variancePenalty = scopeType == ScopeType.FULL ? 0.25 : 0.15;
        double penalty = spreadPenalty * stage3.spreadAvg()
                + variancePenalty * stage3.varianceS3()
                + variancePenalty * stage3.varianceS4();
        return clamp(1.0 - penalty);
    }

    private static double pipelineReliability(Stage0Profile stage0, Stage3Result stage3) {
        double reliability = 1.0;
        if (!stage0.parseOkA() || !stage0.parseOkB()) {
            return 0.20;
        }
        if (stage3.status() == SignalStatus.NOT_APPLICABLE) {
            reliability = Math.min(reliability, 0.50);
        }
        if (stage0.flags().contains(Stage0Flag.BOILERPLATE_HEAVY)) {
            reliability -= 0.10;
        }
        if (stage0.flags().contains(Stage0Flag.TRIVIAL_METHOD_HEAVY)) {
            reliability -= 0.10;
        }
        if (stage0.flags().contains(Stage0Flag.S4_COST_RISK)) {
            reliability -= 0.05;
        }
        if (stage3.flags().contains(Stage3Flag.LOW_EVIDENCE)) {
            reliability = Math.min(reliability, 0.50);
        }
        return clamp(reliability);
    }

    private static double scopeConfidence(ScopeType scopeType, ScopeScores scores) {
        return switch (scopeType) {
            case FULL -> scores.full();
            case PARTIAL -> scores.partial();
            case MIXED -> scores.mixed();
            case UNKNOWN -> 0.35;
        };
    }

    private static EvidenceChain evidenceChain(CloneType cloneType, ScopeType scopeType,
                                               Stage0Profile stage0, Stage3Result stage3,
                                               ComponentScores c) {
        List<EvidenceItem> support = new ArrayList<>();
        List<EvidenceItem> oppose = new ArrayList<>();
        List<EvidenceItem> scope = new ArrayList<>();
        List<EvidenceItem> warnings = new ArrayList<>();

        if (cloneType == CloneType.T2) {
            add(support, "structural_exactness_avg", stage3.structuralExactnessAvg(),
                    "Exact subtree overlap explains much of the structural similarity.", "T2");
            add(support, "method_similarity_strength", c.methodSimilarityStrength(),
                    "Method-level token and structure signals are strong.", "T2");
            add(oppose, "modification_strength", c.modificationStrength(),
                    "Modification evidence can pull the result toward T3.", "T3");
        } else if (cloneType == CloneType.T3) {
            add(support, "method_similarity_strength", c.methodSimilarityStrength(),
                    "Method-level similarity remains visible after modifications.", "T3");
            add(support, "modification_strength", c.modificationStrength(),
                    "Token/exact-structure gap suggests near-miss or statement-level changes.", "T3");
            add(oppose, "structural_exactness_avg", stage3.structuralExactnessAvg(),
                    "Exact structural overlap still leaves some T2-like evidence.", "T2");
        } else if (cloneType == CloneType.T4_WEAK) {
            add(support, "S5", stage3.s5(),
                    "API vocabulary overlaps while method-level clone evidence is weaker.", "T4_WEAK");
        } else if (cloneType == CloneType.NON_CLONE) {
            add(support, "method_similarity_strength", 1.0 - c.methodSimilarityStrength(),
                    "Method-level similarity evidence is weak.", "NON_CLONE");
            add(support, "coverage_strength", 1.0 - c.coverageStrength(),
                    "Most method mass is not well covered by the opposite file.", "NON_CLONE");
        } else if (cloneType == CloneType.T1) {
            add(support, "file_exact_normalized_match", 1.0,
                    "Sources match after removing comments and whitespace.", "T1");
        }

        if (scopeType == ScopeType.PARTIAL) {
            add(scope, "partial_clone_signal", stage3.partialCloneSignal(),
                    "Containment and size asymmetry indicate a partial relationship.", "PARTIAL");
            add(scope, "coverage_asymmetry", Math.abs(stage3.coverageA() - stage3.coverageB()),
                    "One side is covered more strongly than the other.", "PARTIAL");
        } else if (scopeType == ScopeType.FULL) {
            add(scope, "confirmed_ratio", stage3.confirmedRatio(),
                    "Directional method matches are mostly symmetric.", "FULL");
            add(scope, "coverage_A/B", Math.min(stage3.coverageA(), stage3.coverageB()),
                    "Both files have broad method coverage.", "FULL");
        } else if (scopeType == ScopeType.MIXED) {
            add(scope, "spread_avg", stage3.spreadAvg(),
                    "Detector disagreement suggests mixed evidence across methods.", "MIXED");
        }

        if (stage0.flags().contains(Stage0Flag.CLASS_CONTEXT_WEAK)) {
            warning(warnings, "CLASS_CONTEXT_WEAK",
                    "Class-level S1 context is weak and should not dominate interpretation.");
        }
        if (stage0.flags().contains(Stage0Flag.TRIVIAL_METHOD_HEAVY)) {
            warning(warnings, "TRIVIAL_METHOD_HEAVY",
                    "Many small methods may reduce method-level reliability.");
        }
        if (stage0.flags().contains(Stage0Flag.S4_COST_RISK)) {
            warning(warnings, "S4_COST_RISK",
                    "Pairwise structural comparison may be costly for this input size.");
        }
        if (stage3.s5Status() == SignalStatus.NOT_APPLICABLE) {
            warning(warnings, "S5_NOT_APPLICABLE",
                    "No external API vocabulary signal was available.");
        }
        if (stage3.flags().contains(Stage3Flag.LOW_EVIDENCE)) {
            warning(warnings, "LOW_EVIDENCE",
                    "Some aggregate weights were weak or unavailable.");
        }

        return new EvidenceChain(
                List.copyOf(support),
                List.copyOf(oppose),
                List.copyOf(scope),
                List.copyOf(warnings)
        );
    }

    private static void add(List<EvidenceItem> items, String signal, double value,
                            String interpretation, String supports) {
        items.add(new EvidenceItem(
                EvidenceCategory.TYPE_SUPPORT,
                signal,
                String.format("%.4f", value),
                interpretation,
                supports,
                level(value)
        ));
    }

    private static void warning(List<EvidenceItem> items, String signal, String interpretation) {
        items.add(new EvidenceItem(
                EvidenceCategory.RELIABILITY_WARNING,
                signal,
                "true",
                interpretation,
                "LOWER_CONFIDENCE",
                ConfidenceLevel.MEDIUM
        ));
    }

    private static ConfidenceLevel level(double value) {
        if (value >= HIGH) return ConfidenceLevel.HIGH;
        if (value >= MEDIUM) return ConfidenceLevel.MEDIUM;
        return ConfidenceLevel.LOW;
    }

    private static double avg(double... values) {
        if (values.length == 0) return 0.0;
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    private static double clamp(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ComponentScores(
            double methodSimilarityStrength,
            double coverageStrength,
            double symmetryStrength,
            double partialStrength,
            double apiSemanticStrength,
            double s1Reliable,
            double exactStructureStrength,
            double modificationStrength
    ) {
    }
}
