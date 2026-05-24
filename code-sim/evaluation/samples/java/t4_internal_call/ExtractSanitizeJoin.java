import java.util.List;

public class ExtractSanitizeJoin {
    public String join(List<String> names) {
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            String cleaned = clean(name);
            if (!cleaned.isEmpty()) {
                append(out, cleaned);
            }
        }
        return out.toString();
    }

    private String clean(String name) {
        return name.trim().toLowerCase();
    }

    private void append(StringBuilder out, String value) {
        if (!out.isEmpty()) {
            out.append(",");
        }
        out.append(value);
    }
}
