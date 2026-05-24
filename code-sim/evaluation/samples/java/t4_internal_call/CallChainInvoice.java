public class CallChainInvoice {
    public int invoiceTotal(int[] prices, boolean member) {
        int subtotal = subtotal(prices);
        return subtotal + tax(subtotal) - discount(subtotal, member);
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

    private int tax(int subtotal) {
        return subtotal / 10;
    }

    private int discount(int subtotal, boolean member) {
        return member ? subtotal / 20 : 0;
    }
}
