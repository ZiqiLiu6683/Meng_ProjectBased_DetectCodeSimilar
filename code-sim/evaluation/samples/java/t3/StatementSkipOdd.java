public class StatementSkipOdd {
    public int clampAndSum(int[] values) {
        int total = 0;
        for (int value : values) {
            if (value % 2 != 0) {
                continue;
            }
            if (value < 0) {
                value = 0;
            }
            if (value > 100) {
                value = 100;
            }
            total += value;
        }
        return total;
    }
}
