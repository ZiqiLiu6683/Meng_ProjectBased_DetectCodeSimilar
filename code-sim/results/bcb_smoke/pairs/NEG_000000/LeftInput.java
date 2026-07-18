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
    public static void printToStream(InputStream is, OutputStream os) throws IOException {
        byte[] buff = new byte[4096];
        int len = 0;
        while ((len = is.read(buff)) != -1) os.write(buff, 0, len);
        is.close();
    }
}
