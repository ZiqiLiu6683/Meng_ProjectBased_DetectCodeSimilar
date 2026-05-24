public class InlineNormalize {
    public String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase().replace(" ", "-");
    }
}
