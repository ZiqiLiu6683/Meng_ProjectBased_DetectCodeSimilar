public class HelperDiscount {
    public int finalPrice(int price, boolean member) {
        return price - discount(price, member);
    }

    private int discount(int price, boolean member) {
        if (member && price > 100) {
            return 20;
        }
        if (member) {
            return 5;
        }
        return 0;
    }
}
