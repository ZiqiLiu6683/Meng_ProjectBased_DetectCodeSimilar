import java.util.HashSet;
import java.util.Set;

public class SetContains {
    public boolean contains(int[] values, int target) {
        Set<Integer> seen = new HashSet<>();
        for (int value : values) {
            seen.add(value);
        }
        return seen.contains(target);
    }
}
