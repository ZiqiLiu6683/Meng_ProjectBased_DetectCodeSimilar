public class InlineStats {
    public int averageAboveZero(int[] values) {
        int total = 0;
        int count = 0;
        for (int value : values) {
            if (value > 0) {
                total += value;
                count++;
            }
        }
        return count == 0 ? 0 : total / count;
    }
}
