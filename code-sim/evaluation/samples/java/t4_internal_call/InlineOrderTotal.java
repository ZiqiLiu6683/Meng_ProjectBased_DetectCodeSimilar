public class InlineOrderTotal {
    public int total(int[] prices) {
        int subtotal = 0;
        for (int price : prices) {
            if (price > 0) {
                subtotal += price;
            }
        }
        int tax = subtotal / 10;
        return subtotal + tax;
    }
}
