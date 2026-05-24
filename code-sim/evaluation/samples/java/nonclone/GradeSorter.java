import java.util.Collections;
import java.util.List;

public class GradeSorter {
    public void sortDescending(List<Integer> grades) {
        Collections.sort(grades);
        Collections.reverse(grades);
    }
}
