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
import java.lang.reflect.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class LeftInput {
    public static void extractFile(String input, String output) throws ZipException, IOException {
        FileReader reader = new FileReader(input);
        InputStream in = reader.getInputStream();
        OutputStream out = new FileOutputStream(new File(output));
        byte[] buf = new byte[512];
        int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        reader.close();
        out.close();
    }
}
