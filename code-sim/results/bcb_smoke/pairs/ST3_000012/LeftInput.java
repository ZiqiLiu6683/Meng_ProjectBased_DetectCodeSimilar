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
    public static void copyFile(File src, File dst) throws IOException {
        FileChannel from = new FileInputStream(src).getChannel();
        FileChannel to = new FileOutputStream(dst).getChannel();
        from.transferTo(0, src.length(), to);
        from.close();
        to.close();
    }
}
