import java.util.Arrays;

public class SortThenScan {
    public int distinctCount(int[] values) {
        Arrays.sort(values);
        int count = 0;
        int previous = Integer.MIN_VALUE;
        for (int value : values) {
            if (value != previous) {
                count++;
                previous = value;
            }
        }
        return count;
    }
}
