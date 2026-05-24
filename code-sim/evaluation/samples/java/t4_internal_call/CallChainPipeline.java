public class CallChainPipeline {
    public int process(String raw) {
        if (!hasText(raw)) {
            return 0;
        }
        return score(normalize(raw));
    }

    private boolean hasText(String raw) {
        return raw != null && !raw.isBlank();
    }

    private String normalize(String raw) {
        return raw.trim().toLowerCase();
    }

    private int score(String cleaned) {
        int total = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c >= 'a' && c <= 'z') {
                total += c - 'a' + 1;
            }
        }
        return total;
    }
}
