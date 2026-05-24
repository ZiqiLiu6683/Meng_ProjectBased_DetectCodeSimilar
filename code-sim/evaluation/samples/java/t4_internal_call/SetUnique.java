import java.util.HashSet;
import java.util.Set;

public class SetUnique {
    public int uniqueCount(int[] values) {
        Set<Integer> seen = new HashSet<>();
        for (int value : values) {
            seen.add(value);
        }
        return seen.size();
    }
}
