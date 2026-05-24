public class StatementNullAndLog {
    public int clampAndSum(int[] values) {
        if (values == null) {
            return 0;
        }
        int total = 0;
        int changed = 0;
        for (int value : values) {
            if (value < 0) {
                value = 0;
                changed++;
            }
            if (value > 100) {
                value = 100;
                changed++;
            }
            total += value;
        }
        return total;
    }
}
