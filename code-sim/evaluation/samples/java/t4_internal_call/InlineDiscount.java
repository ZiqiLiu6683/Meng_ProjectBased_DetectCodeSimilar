public class InlineDiscount {
    public int finalPrice(int price, boolean member) {
        if (member && price > 100) {
            return price - 20;
        }
        if (member) {
            return price - 5;
        }
        return price;
    }
}
