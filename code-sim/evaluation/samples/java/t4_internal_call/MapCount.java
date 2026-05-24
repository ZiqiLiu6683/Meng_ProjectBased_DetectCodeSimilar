import java.util.HashMap;
import java.util.Map;

public class MapCount {
    public int countLetterA(String text) {
        Map<Character, Integer> counts = new HashMap<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            counts.put(c, counts.getOrDefault(c, 0) + 1);
        }
        return counts.getOrDefault('a', 0);
    }
}
