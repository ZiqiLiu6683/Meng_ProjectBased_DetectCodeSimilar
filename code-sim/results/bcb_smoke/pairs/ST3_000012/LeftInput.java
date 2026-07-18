import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class LeftInput {
    public static void copyFile(File src, File dst) throws IOException {
        FileChannel from = new FileInputStream(src).getChannel();
        FileChannel to = new FileOutputStream(dst).getChannel();
        from.transferTo(0, src.length(), to);
        from.close();
        to.close();
    }
}
