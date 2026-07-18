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
    private void copyParseFileToCodeFile() throws IOException {
        InputStream in = new FileInputStream(new File(filenameParse));
        OutputStream out = new FileOutputStream(new File(filenameMisc));
        byte[] buffer = new byte[1024];
        int length;
        while ((length = in.read(buffer)) > 0) out.write(buffer, 0, length);
        in.close();
        out.close();
    }
}
