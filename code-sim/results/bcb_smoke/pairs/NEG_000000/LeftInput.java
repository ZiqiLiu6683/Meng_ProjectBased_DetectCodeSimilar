import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class LeftInput {
    public static void printToStream(InputStream is, OutputStream os) throws IOException {
        byte[] buff = new byte[4096];
        int len = 0;
        while ((len = is.read(buff)) != -1) os.write(buff, 0, len);
        is.close();
    }
}
