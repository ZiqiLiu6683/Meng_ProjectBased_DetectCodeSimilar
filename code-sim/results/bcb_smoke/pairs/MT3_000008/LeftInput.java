import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class LeftInput {
    public static void copyFile(File inputFile, File outputFile) throws IOException {
        BufferedInputStream fr = new BufferedInputStream(new FileInputStream(inputFile));
        BufferedOutputStream fw = new BufferedOutputStream(new FileOutputStream(outputFile));
        byte[] buf = new byte[8192];
        int n;
        while ((n = fr.read(buf)) >= 0) fw.write(buf, 0, n);
        fr.close();
        fw.close();
    }
}
