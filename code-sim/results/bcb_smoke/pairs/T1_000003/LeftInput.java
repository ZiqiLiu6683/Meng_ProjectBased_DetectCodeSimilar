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
    public static boolean isPalindrome(String string) {
        if (string.length() == 0) return true;
        int limit = string.length() / 2;
        for (int forward = 0, backward = string.length() - 1; forward < limit; forward++, backward--) if (string.charAt(forward) != string.charAt(backward)) return false;
        return true;
    }
}
