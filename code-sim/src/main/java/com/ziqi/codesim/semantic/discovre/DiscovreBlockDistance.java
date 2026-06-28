package com.ziqi.codesim.semantic.discovre;

/**
 * discovRE basic-block distance d_BB (NDSS'16, section III-C1).
 *
 * <p>The per-feature alpha weights are configurable. The default preset
 * {@link Weights#discovreTable3()} reproduces the paper's best parameter set
 * (Table III). IMPORTANT: those weights were tuned by a genetic algorithm over
 * x86/ARM binary functions and are NOT recalibrated for Java/WALA SSA features.
 * They are kept as a documented, citeable default; {@link Weights#uniform()}
 * provides a neutral equal-weight baseline for comparison, and any custom set
 * can be supplied for future recalibration on this project's labeled data.
 */
public class DiscovreBlockDistance {
    /**
     * Per-feature weights for d_BB. Field order matches discovRE Table III.
     */
    public record Weights(
            double arithmetic,
            double call,
            double instruction,
            double logic,
            double transfer,
            double stringConstant,
            double numericConstant) {

        /** discovRE Table III best parameter set (x86/ARM-tuned; not recalibrated for Java SSA). */
        public static Weights discovreTable3() {
            return new Weights(56.658, 87.423, 40.423, 76.694, 6.841, 11.998, 15.382);
        }

        /** Neutral equal-weight baseline, useful as a domain-agnostic comparison point. */
        public static Weights uniform() {
            return new Weights(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
        }
    }

    private final Weights weights;

    public DiscovreBlockDistance() {
        this(Weights.discovreTable3());
    }

    public DiscovreBlockDistance(Weights weights) {
        this.weights = weights;
    }

    public double distance(DiscovreBlockFeatures left, DiscovreBlockFeatures right) {
        Accumulator acc = new Accumulator();
        acc.add(weights.arithmetic(), left.arithmeticInstructions(), right.arithmeticInstructions());
        acc.add(weights.call(), left.calls(), right.calls());
        acc.add(weights.instruction(), left.instructions(), right.instructions());
        acc.add(weights.logic(), left.logicInstructions(), right.logicInstructions());
        acc.add(weights.transfer(), left.transferInstructions(), right.transferInstructions());
        acc.add(weights.stringConstant(), left.stringConstants(), right.stringConstants());
        acc.add(weights.numericConstant(), left.numericConstants(), right.numericConstants());
        if (acc.denominator == 0.0) {
            return 0.0;
        }
        return acc.numerator / acc.denominator;
    }

    private static class Accumulator {
        double numerator;
        double denominator;

        void add(double weight, int left, int right) {
            numerator += weight * Math.abs(left - right);
            denominator += weight * Math.max(left, right);
        }
    }
}
