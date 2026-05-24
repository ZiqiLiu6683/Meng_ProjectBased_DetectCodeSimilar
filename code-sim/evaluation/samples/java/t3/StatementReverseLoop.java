public class StatementReverseLoop {
    public int clampAndSum(int[] values) {
        int total = 0;
        for (int i = values.length - 1; i >= 0; i--) {
            int value = values[i];
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
