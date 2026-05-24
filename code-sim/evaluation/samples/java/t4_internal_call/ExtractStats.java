public class ExtractStats {
    public int averageAboveZero(int[] values) {
        int total = positiveTotal(values);
        int count = positiveCount(values);
        return count == 0 ? 0 : total / count;
    }

    private int positiveTotal(int[] values) {
        int total = 0;
        for (int value : values) {
            if (value > 0) {
                total += value;
            }
        }
        return total;
    }

    private int positiveCount(int[] values) {
        int count = 0;
        for (int value : values) {
            if (value > 0) {
                count++;
            }
        }
        return count;
    }
}
