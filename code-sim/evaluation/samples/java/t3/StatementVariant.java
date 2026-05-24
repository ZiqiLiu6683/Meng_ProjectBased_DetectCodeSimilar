public class StatementVariant {
    public int clampAndSum(int[] values) {
        if (values == null) {
            return 0;
        }
        int total = 0;
        for (int value : values) {
            if (value < 0) {
                value = 0;
            } else if (value > 100) {
                value = 100;
            }
            total += value;
        }
        return total;
    }
}
