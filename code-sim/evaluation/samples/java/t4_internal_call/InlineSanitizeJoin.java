import java.util.List;

public class InlineSanitizeJoin {
    public String join(List<String> names) {
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            String cleaned = name.trim().toLowerCase();
            if (!cleaned.isEmpty()) {
                if (!out.isEmpty()) {
                    out.append(",");
                }
                out.append(cleaned);
            }
        }
        return out.toString();
    }
}
