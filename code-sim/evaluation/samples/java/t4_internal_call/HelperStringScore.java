public class HelperStringScore {
    public int score(String text) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isLowercaseLetter(c)) {
                total += letterValue(c);
            }
        }
        return total;
    }

    private boolean isLowercaseLetter(char c) {
        return c >= 'a' && c <= 'z';
    }

    private int letterValue(char c) {
        return c - 'a' + 1;
    }
}
