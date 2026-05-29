package probe;

import java.util.ArrayList;
import java.util.List;

public class RobustnessProbe {
    public int sumPositive(List<Integer> values) {
        int total = 0;
        for (int value : values) {
            if (value > 0) {
                total += value;
            }
        }
        return total;
    }

    public List<String> selectLongNames(List<String> names, int minLength) {
        List<String> selected = new ArrayList<>();
        for (String name : names) {
            if (name != null && name.length() >= minLength) {
                selected.add(name.trim());
            }
        }
        return selected;
    }

    public int score(String label, int base) {
        int adjusted = normalize(base);
        if (label == null || label.trim().isEmpty()) {
            return adjusted - 5;
        }
        return adjusted + label.length();
    }

    private int normalize(int value) {
        if (value < 0) {
            return -value;
        }
        return value * 2;
    }
}
