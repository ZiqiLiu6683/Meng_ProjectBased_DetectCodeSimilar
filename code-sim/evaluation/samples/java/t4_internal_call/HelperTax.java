public class HelperTax {
    public int totalWithTax(int[] prices) {
        int total = 0;
        for (int price : prices) {
            total += applyTax(price);
        }
        return total;
    }

    private int applyTax(int price) {
        int tax = price / 10;
        return price + tax;
    }
}
