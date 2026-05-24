import java.util.List;

public class ListPrinter {
    public String joinNames(List<String> names) {
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            if (!out.isEmpty()) {
                out.append(",");
            }
            out.append(name);
        }
        return out.toString();
    }
}
