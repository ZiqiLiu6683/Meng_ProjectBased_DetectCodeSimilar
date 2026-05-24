public class PaymentRules {
    public int calculateWeightedSum(int[] values) {
        int score = 0;
        for (int value : values) {
            if (value > 10) {
                score += value * 2;
            } else {
                score += value;
            }
        }
        return score;
    }
}
