import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class LeftInput {
    public static void copy(File inputFile, File target) throws IOException {
        if (!inputFile.exists()) return;
        OutputStream output = new FileOutputStream(target);
        InputStream input = new BufferedInputStream(new FileInputStream(inputFile));
        int b;
        while ((b = input.read()) != -1) output.write(b);
        output.close();
        input.close();
    }
}
