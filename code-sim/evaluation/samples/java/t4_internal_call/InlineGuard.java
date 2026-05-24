public class InlineGuard {
    public int countValid(int[] values) {
        int count = 0;
        for (int value : values) {
            if (value >= 10 && value <= 99) {
                count++;
            }
        }
        return count;
    }
}
