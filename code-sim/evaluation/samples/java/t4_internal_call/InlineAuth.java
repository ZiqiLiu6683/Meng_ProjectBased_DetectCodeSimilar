public class InlineAuth {
    public boolean canLogin(String user, String password, boolean locked) {
        if (user == null || password == null) {
            return false;
        }
        String cleanUser = user.trim().toLowerCase();
        if (cleanUser.isEmpty() || locked) {
            return false;
        }
        return password.length() >= 8;
    }
}
