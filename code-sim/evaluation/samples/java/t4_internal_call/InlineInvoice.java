public class InlineInvoice {
    public int invoiceTotal(int[] prices, boolean member) {
        int subtotal = 0;
        for (int price : prices) {
            if (price > 0) {
                subtotal += price;
            }
        }
        int tax = subtotal / 10;
        int discount = member ? subtotal / 20 : 0;
        return subtotal + tax - discount;
    }
}
