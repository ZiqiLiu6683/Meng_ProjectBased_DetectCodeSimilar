public class StatementThresholdParam {
    public int clampAndSum(int[] values) {
        return clampAndSum(values, 100);
    }

    public int clampAndSum(int[] values, int upperLimit) {
        int total = 0;
        for (int value : values) {
            if (value < 0) {
                value = 0;
            }
            if (value > upperLimit) {
                value = upperLimit;
            }
            total += value;
        }
        return total;
    }
}
