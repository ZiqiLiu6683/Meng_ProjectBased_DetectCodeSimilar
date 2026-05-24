import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HelperFilterSort {
    public List<Integer> filterAndSort(int[] values) {
        List<Integer> result = new ArrayList<>();
        addPositiveValues(result, values);
        Collections.sort(result);
        return result;
    }

    private void addPositiveValues(List<Integer> result, int[] values) {
        for (int value : values) {
            if (value > 0) {
                result.add(value);
            }
        }
    }
}
