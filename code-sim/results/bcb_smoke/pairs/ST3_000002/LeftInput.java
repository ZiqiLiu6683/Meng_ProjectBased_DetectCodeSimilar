import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class LeftInput {
    private void sort() {
        boolean unsortiert = true;
        Datei tmp = null;
        while (unsortiert) {
            unsortiert = false;
            for (int i = 0; i < this.size - 1; i++) {
                if (dateien[i] != null && dateien[i + 1] != null) {
                    if (dateien[i].compareTo(dateien[i + 1]) < 0) {
                        tmp = dateien[i];
                        dateien[i] = dateien[i + 1];
                        dateien[i + 1] = tmp;
                        unsortiert = true;
                    }
                }
            }
        }
    }
}
