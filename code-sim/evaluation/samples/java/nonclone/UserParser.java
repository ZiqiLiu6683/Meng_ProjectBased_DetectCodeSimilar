public class UserParser {
    public String extractDomain(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return "";
        }
        return email.substring(at + 1).toLowerCase();
    }
}
