public class HelperGuard {
    public int countValid(int[] values) {
        int count = 0;
        for (int value : values) {
            if (isValid(value)) {
                count++;
            }
        }
        return count;
    }

    private boolean isValid(int value) {
        return value >= 10 && value <= 99;
    }
}
