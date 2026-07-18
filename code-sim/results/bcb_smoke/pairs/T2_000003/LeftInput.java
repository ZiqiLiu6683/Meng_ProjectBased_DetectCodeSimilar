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
    public static boolean decodeFileToFile(String infile, String outfile) {
        boolean success = false;
        java.io.InputStream in = null;
        java.io.OutputStream out = null;
        try {
            in = new Base64.InputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(infile)), Base64.DECODE);
            out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(outfile));
            byte[] buffer = new byte[65536];
            int read = -1;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            success = true;
        } catch (java.io.IOException exc) {
            exc.printStackTrace();
        } finally {
            try {
                in.close();
            } catch (Exception exc) {
            }
            try {
                out.close();
            } catch (Exception exc) {
            }
        }
        return success;
    }
}
