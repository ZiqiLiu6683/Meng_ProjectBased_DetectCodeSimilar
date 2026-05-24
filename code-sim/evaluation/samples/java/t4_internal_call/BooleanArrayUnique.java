public class BooleanArrayUnique {
    public int uniqueCount(int[] values) {
        boolean[] seen = new boolean[101];
        int count = 0;
        for (int value : values) {
            if (value >= 0 && value <= 100 && !seen[value]) {
                seen[value] = true;
                count++;
            }
        }
        return count;
    }
}
