public class InlineStringScore {
    public int score(String text) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'a' && c <= 'z') {
                total += c - 'a' + 1;
            }
        }
        return total;
    }
}
