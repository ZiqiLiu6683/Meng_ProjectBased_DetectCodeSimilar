package com.ziqi.codesim.semantic.discovre;

public class DiscovreBlockDistance {
    private static final double ARITHMETIC_WEIGHT = 56.658;
    private static final double CALL_WEIGHT = 87.423;
    private static final double INSTRUCTION_WEIGHT = 40.423;
    private static final double LOGIC_WEIGHT = 76.694;
    private static final double TRANSFER_WEIGHT = 6.841;
    private static final double STRING_CONSTANT_WEIGHT = 11.998;
    private static final double NUMERIC_CONSTANT_WEIGHT = 15.382;

    public double distance(DiscovreBlockFeatures left, DiscovreBlockFeatures right) {
        double numerator = 0.0;
        double denominator = 0.0;
        Accumulator acc = new Accumulator();
        acc.add(ARITHMETIC_WEIGHT, left.arithmeticInstructions(), right.arithmeticInstructions());
        acc.add(CALL_WEIGHT, left.calls(), right.calls());
        acc.add(INSTRUCTION_WEIGHT, left.instructions(), right.instructions());
        acc.add(LOGIC_WEIGHT, left.logicInstructions(), right.logicInstructions());
        acc.add(TRANSFER_WEIGHT, left.transferInstructions(), right.transferInstructions());
        acc.add(STRING_CONSTANT_WEIGHT, left.stringConstants(), right.stringConstants());
        acc.add(NUMERIC_CONSTANT_WEIGHT, left.numericConstants(), right.numericConstants());
        numerator = acc.numerator;
        denominator = acc.denominator;
        if (denominator == 0.0) {
            return 0.0;
        }
        return numerator / denominator;
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
