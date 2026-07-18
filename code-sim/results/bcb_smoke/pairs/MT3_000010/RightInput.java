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

public class RightInput {
    public static String encodePassword(String password) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA");
            md.update(password.getBytes());
            String encodedPassword = new String(md.digest(), new Base64Provider().charsetForName("x-base64"));
            return encodedPassword;
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
