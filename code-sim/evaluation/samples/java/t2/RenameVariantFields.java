public class RenameBase {
    public int weightedTotal(int[] numbers) {
        int amount = 0;
        for (int number : numbers) {
            if (number > 10) {
                amount += number * 2;
            } else {
                amount += number;
            }
        }
        return amount;
    }
}
