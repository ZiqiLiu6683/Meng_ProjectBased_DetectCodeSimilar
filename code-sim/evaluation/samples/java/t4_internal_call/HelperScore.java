public class HelperScore {
    public int score(int[] values) {
        int total = 0;
        for (int value : values) {
            if (isPositive(value)) {
                total += value;
            }
        }
        return total;
    }

    private boolean isPositive(int value) {
        return value > 0;
    }
}
