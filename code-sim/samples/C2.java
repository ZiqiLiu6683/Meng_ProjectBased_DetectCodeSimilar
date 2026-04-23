import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;

// Type-4 clone of C1.
// Filtering logic is extracted into the private helper populate().
// Loop style changed (while instead of for).
// Variable names differ throughout.
// External API calls remain identical: {add, sort, iterator, hasNext, next}.
// populate() is an INTERNAL call (defined in this file) and is filtered out by S5.
public class C2 {

    // Private helper: populate out-list with positive entries from src.
    // This call is internal → filtered by S5; only its body's external calls count.
    private void populate(List<Integer> out, int[] src) {
        int idx = 0;
        while (idx < src.length) {
            if (src[idx] > 0) out.add(src[idx]);
            idx++;
        }
    }

    public List<Integer> filterAndSort(int[] src) {
        List<Integer> out = new ArrayList<>();
        populate(out, src);          // internal call — S5 ignores this
        Collections.sort(out);
        return out;
    }

    public boolean containsValue(List<Integer> lst, int val) {
        Iterator<Integer> iter = lst.iterator();
        while (iter.hasNext()) {
            int cur = iter.next();
            if (cur == val) return true;
        }
        return false;
    }
}
