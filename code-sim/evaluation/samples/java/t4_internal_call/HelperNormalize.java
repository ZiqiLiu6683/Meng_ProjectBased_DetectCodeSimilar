public class HelperNormalize {
    public String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return cleanup(raw);
    }

    private String cleanup(String raw) {
        return raw.trim().toLowerCase().replace(" ", "-");
    }
}
