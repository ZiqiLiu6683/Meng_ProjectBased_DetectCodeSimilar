public class PasswordMasker {
    public String mask(String password) {
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < password.length(); i++) {
            masked.append('*');
        }
        return masked.toString();
    }
}
