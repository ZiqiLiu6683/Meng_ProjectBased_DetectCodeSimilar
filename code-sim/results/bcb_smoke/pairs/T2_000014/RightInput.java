import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class RightInput {
    public static void copyFile(File fileIn, File fileOut) throws IOException {
        FileChannel chIn = new FileInputStream(fileIn).getChannel();
        FileChannel chOut = new FileOutputStream(fileOut).getChannel();
        try {
            chIn.transferTo(0, chIn.size(), chOut);
        } catch (IOException e) {
            throw e;
        } finally {
            if (chIn != null) chIn.close();
            if (chOut != null) chOut.close();
        }
    }
}
