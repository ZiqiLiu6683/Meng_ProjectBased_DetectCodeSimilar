import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InlineFilterSort {
    public List<Integer> filterAndSort(int[] values) {
        List<Integer> result = new ArrayList<>();
        for (int value : values) {
            if (value > 0) {
                result.add(value);
            }
        }
        Collections.sort(result);
        return result;
    }
}
