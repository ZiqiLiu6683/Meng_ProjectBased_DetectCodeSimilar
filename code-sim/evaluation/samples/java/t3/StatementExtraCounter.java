public class StatementExtraCounter {
    public int clampAndSum(int[] values) {
        int total = 0;
        int positives = 0;
        for (int value : values) {
            if (value < 0) {
                value = 0;
            }
            if (value > 100) {
                value = 100;
            }
            if (value > 0) {
                positives++;
            }
            total += value;
        }
        return total;
    }
}
