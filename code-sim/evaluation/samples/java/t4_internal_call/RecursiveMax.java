public class RecursiveMax {
    public int max(int[] values) {
        return maxFrom(values, 0, values[0]);
    }

    private int maxFrom(int[] values, int index, int best) {
        if (index >= values.length) {
            return best;
        }
        int nextBest = values[index] > best ? values[index] : best;
        return maxFrom(values, index + 1, nextBest);
    }
}
