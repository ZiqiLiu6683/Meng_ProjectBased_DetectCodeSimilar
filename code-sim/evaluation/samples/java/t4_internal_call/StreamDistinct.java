import java.util.Arrays;

public class StreamDistinct {
    public int distinctCount(int[] values) {
        return (int) Arrays.stream(values).distinct().count();
    }
}
