public class ExtractOrderTotal {
    public int total(int[] prices) {
        int subtotal = subtotal(prices);
        int tax = subtotal / 10;
        return subtotal + tax;
    }

    private int subtotal(int[] prices) {
        int sum = 0;
        for (int price : prices) {
            if (price > 0) {
                sum += price;
            }
        }
        return sum;
    }
}
