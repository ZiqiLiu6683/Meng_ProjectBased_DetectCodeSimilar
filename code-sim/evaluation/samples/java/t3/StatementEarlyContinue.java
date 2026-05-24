public class StatementEarlyContinue {
    public int clampAndSum(int[] values) {
        int total = 0;
        for (int value : values) {
            if (value < 0) {
                total += 0;
                continue;
            }
            if (value > 100) {
                total += 100;
                continue;
            }
            total += value;
        }
        return total;
    }
}
