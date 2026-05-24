public class InlineRiskScore {
    public int risk(int age, int incidents, boolean verified) {
        int score = 0;
        if (age < 21) {
            score += 20;
        }
        if (incidents > 2) {
            score += incidents * 10;
        }
        if (!verified) {
            score += 15;
        }
        return score;
    }
}
