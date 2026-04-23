import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;

// Type-1 clone of B1: body is byte-for-byte identical, only class name differs.
// Expected: S1≈100%  S2≈100%  S3≈100%  S4≈100%  S5=100%
public class B1_type1 {

    public List<Integer> filterPositive(int[] nums) {
        List<Integer> result = new ArrayList<>();
        for (int x : nums) {
            if (x > 0) result.add(x);
        }
        Collections.sort(result);
        return result;
    }

    public boolean contains(List<Integer> list, int target) {
        Iterator<Integer> it = list.iterator();
        while (it.hasNext()) {
            if (it.next() == target) return true;
        }
        return false;
    }
}
