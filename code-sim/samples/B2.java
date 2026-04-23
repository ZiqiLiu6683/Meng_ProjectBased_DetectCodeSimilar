import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;

// Type-2 clone of B1: every variable name, parameter name, and method name
// has been renamed — the AST structure and control flow are identical.
// Our AST normaliser maps all identifiers to "ID", so S2/S3/S4 should
// remain high; S1 should also be high due to the same normalisation.
// Expected: S1≈high  S2≈high  S3≈high  S4≈high  S5=100%
public class B2 {

    /** Return sorted list of values above zero. */
    public List<Integer> getPositiveValues(int[] data) {
        List<Integer> out = new ArrayList<>();
        for (int val : data) {
            if (val > 0) out.add(val);
        }
        Collections.sort(out);
        return out;
    }

    /** Return true if val is found in lst. */
    public boolean search(List<Integer> lst, int val) {
        Iterator<Integer> iter = lst.iterator();
        while (iter.hasNext()) {
            if (iter.next() == val) return true;
        }
        return false;
    }
}
