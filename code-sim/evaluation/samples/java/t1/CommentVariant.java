public class FormatBase {
    public int countPositive(int[] values) {
        // Count only numbers above zero.
        int total = 0;
        for (int value : values) {
            if (value > 0) {
                total++; // Same operation as the base file.
            }
        }
        return total;
    }
}
