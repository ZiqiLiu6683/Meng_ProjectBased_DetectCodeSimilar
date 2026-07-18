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
    private static String md5Encode(String pass) {
        String string;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(pass.getBytes());
            byte[] result = md.digest();
            string = bytes2hexStr(result);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("La libreria java.security no implemente MD5");
        }
        return string;
    }
}
