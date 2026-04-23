import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;

// Type-1 / Type-2 base file.
// B1 vs B1_type1 → Type-1 clone (identical body, class name differs)
// B1 vs B2       → Type-2 clone (all variable/method names renamed)
public class B1 {

    /** Filter positive values and return them sorted. */
    public List<Integer> filterPositive(int[] nums) {
        List<Integer> result = new ArrayList<>();
        for (int x : nums) {
            if (x > 0) result.add(x);
        }
        Collections.sort(result);
        return result;
    }

    /** Check whether target appears in the list. */
    public boolean contains(List<Integer> list, int target) {
        Iterator<Integer> it = list.iterator();
        while (it.hasNext()) {
            if (it.next() == target) return true;
        }
        return false;
    }
}
