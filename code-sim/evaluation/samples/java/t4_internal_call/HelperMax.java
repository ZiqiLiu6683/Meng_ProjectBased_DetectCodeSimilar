public class HelperMax {
    public int max(int[] values) {
        int best = values[0];
        for (int value : values) {
            best = larger(best, value);
        }
        return best;
    }

    private int larger(int left, int right) {
        return right > left ? right : left;
    }
}
