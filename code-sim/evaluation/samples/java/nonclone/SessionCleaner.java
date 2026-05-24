import java.util.Iterator;
import java.util.Map;

public class SessionCleaner {
    public void removeExpired(Map<String, Long> sessions, long now) {
        Iterator<Map.Entry<String, Long>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < now) {
                it.remove();
            }
        }
    }
}
