import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;
import java.util.zip.*;
import java.security.*;

public class RightInput {
    private int binarySearch(List<File> l, File dir) {
        int low = 0;
        int high = l.size();
        String sdir = dir.getAbsolutePath();
        while (low < high) {
            int mid = (low + high) / 2;
            if (l.get(mid).getAbsolutePath().compareTo(sdir) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}
