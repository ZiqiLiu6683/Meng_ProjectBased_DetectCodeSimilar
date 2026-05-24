public class InlineTax {
    public int totalWithTax(int[] prices) {
        int total = 0;
        for (int price : prices) {
            int tax = price / 10;
            total += price + tax;
        }
        return total;
    }
}
