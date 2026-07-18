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
    public static void copy(Reader reader, Writer writer) throws IOException {
        char[] buf = new char[COPY_BUF_SIZE];
        int read = 0;
        while ((read = reader.read(buf)) != -1) {
            writer.write(buf, 0, read);
        }
    }
}
