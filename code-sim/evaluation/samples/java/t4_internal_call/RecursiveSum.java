public class RecursiveSum {
    public int sum(int[] values) {
        return sumFrom(values, 0);
    }

    private int sumFrom(int[] values, int index) {
        if (index >= values.length) {
            return 0;
        }
        return values[index] + sumFrom(values, index + 1);
    }
}
