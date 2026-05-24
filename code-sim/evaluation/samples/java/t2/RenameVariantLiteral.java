public class RenameLiteralVariant {
    public int computeAmount(int[] numbers) {
        int total = 0;
        for (int item : numbers) {
            if (item > 12) {
                total += item * 2;
            } else {
                total += item;
            }
        }
        return total;
    }
}
