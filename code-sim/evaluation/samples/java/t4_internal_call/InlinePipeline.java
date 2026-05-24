public class InlinePipeline {
    public int process(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String cleaned = raw.trim().toLowerCase();
        int score = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c >= 'a' && c <= 'z') {
                score += c - 'a' + 1;
            }
        }
        return score;
    }
}
