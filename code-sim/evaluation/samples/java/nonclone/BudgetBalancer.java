public class BudgetBalancer {
    public int remaining(int[] costs, int budget) {
        int spent = 0;
        for (int cost : costs) {
            spent += cost;
        }
        return budget - spent;
    }
}
