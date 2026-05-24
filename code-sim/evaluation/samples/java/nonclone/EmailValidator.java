public class EmailValidator {
    public boolean valid(String email) {
        if (email == null) {
            return false;
        }
        int at = email.indexOf('@');
        return at > 0 && at < email.length() - 1;
    }
}
