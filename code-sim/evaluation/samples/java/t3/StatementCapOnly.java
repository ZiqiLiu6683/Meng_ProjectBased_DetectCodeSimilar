public class StatementCapOnly {
    public int clampAndSum(int[] values) {
        int total = 0;
        for (int value : values) {
            if (value > 100) {
                value = 100;
            }
            total += value;
        }
        return total;
    }
}
