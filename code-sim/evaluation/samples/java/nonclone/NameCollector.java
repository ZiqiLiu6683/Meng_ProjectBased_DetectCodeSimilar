import java.util.ArrayList;
import java.util.List;

public class NameCollector {
    public List<String> collectLongNames(List<String> names) {
        List<String> result = new ArrayList<>();
        for (String name : names) {
            if (name.length() > 3) {
                result.add(name.toUpperCase());
            }
        }
        return result;
    }
}
