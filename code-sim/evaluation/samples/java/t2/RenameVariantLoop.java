public class AmountCalculator {
    public int weightedTotal(int[] input) {
        int result = 0;
        for (int current : input) {
            if (current > 10) {
                result += current * 2;
            } else {
                result += current;
            }
        }
        return result;
    }
}
