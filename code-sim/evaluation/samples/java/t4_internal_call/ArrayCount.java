public class ArrayCount {
    public int countLetterA(String text) {
        int[] counts = new int[26];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'a' && c <= 'z') {
                counts[c - 'a']++;
            }
        }
        return counts[0];
    }
}
