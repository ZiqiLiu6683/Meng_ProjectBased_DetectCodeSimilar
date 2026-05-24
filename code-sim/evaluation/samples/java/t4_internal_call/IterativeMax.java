public class IterativeMax {
    public int max(int[] values) {
        int best = values[0];
        for (int value : values) {
            if (value > best) {
                best = value;
            }
        }
        return best;
    }
}
