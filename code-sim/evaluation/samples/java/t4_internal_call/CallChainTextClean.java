public class CallChainTextClean {
    public String clean(String raw) {
        return keepLettersAndDigits(normalize(raw));
    }

    private String normalize(String raw) {
        return raw.trim().toLowerCase();
    }

    private String keepLettersAndDigits(String lower) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
