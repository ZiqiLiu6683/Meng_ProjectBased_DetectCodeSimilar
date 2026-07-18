import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class LeftInput {
    public static void mergeSort(int fromIndex, int toIndex, IntComparator c, Swapper swapper) {
        int length = toIndex - fromIndex;
        if (length < SMALL) {
            for (int i = fromIndex; i < toIndex; i++) {
                for (int j = i; j > fromIndex && (c.compare(j - 1, j) > 0); j--) {
                    swapper.swap(j, j - 1);
                }
            }
            return;
        }
        int mid = (fromIndex + toIndex) / 2;
        mergeSort(fromIndex, mid, c, swapper);
        mergeSort(mid, toIndex, c, swapper);
        if (c.compare(mid - 1, mid) <= 0) return;
        inplace_merge(fromIndex, mid, toIndex, c, swapper);
    }
}
