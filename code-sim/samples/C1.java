import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;

// Type-4 pair – base (C1).
// Both C1 and C2 implement filterAndSort + containsValue using the same
// external APIs: {add, sort, iterator, hasNext, next}.
// However C2 refactors the filtering step into a private helper method,
// making S1/S2/S4 scores drop while S5 stays near 100%.
//
// Expected when compared against C2:
//   S1 medium (~40–60%)   — token sequences differ (extra method in C2)
//   S2 low–medium         — different subtree structure
//   S3 medium             — filterAndSort bodies differ (inline vs delegated)
//   S4 medium             — APTED reflects structural difference
//   S5 high (~100%)       — identical external API vocabulary {add,sort,iterator,hasNext,next}
public class C1 {

    public List<Integer> filterAndSort(int[] input) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < input.length; i++) {
            if (input[i] > 0) {
                result.add(input[i]);
            }
        }
        Collections.sort(result);
        return result;
    }

    public boolean containsValue(List<Integer> list, int target) {
        Iterator<Integer> it = list.iterator();
        while (it.hasNext()) {
            if (it.next() == target) return true;
        }
        return false;
    }
}
