public class RenameVariant {
    public int computeAmount(int[] numbers) {
        int total = 0;
        for (int item : numbers) {
            if (item > 10) {
                total += item * 2;
            } else {
                total += item;
            }
        }
        return total;
    }
}
