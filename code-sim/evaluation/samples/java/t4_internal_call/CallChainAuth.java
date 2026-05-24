public class CallChainAuth {
    public boolean canLogin(String user, String password, boolean locked) {
        if (!hasCredentials(user, password)) {
            return false;
        }
        if (!activeUser(user, locked)) {
            return false;
        }
        return strongPassword(password);
    }

    private boolean hasCredentials(String user, String password) {
        return user != null && password != null;
    }

    private boolean activeUser(String user, boolean locked) {
        return !user.trim().toLowerCase().isEmpty() && !locked;
    }

    private boolean strongPassword(String password) {
        return password.length() >= 8;
    }
}
