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
    private String[] execSingleLineOutputCmd(String cmdWithParams) {
        String result = "";
        try {
            Process p = Runtime.getRuntime().exec(cmdWithParams.split(" "));
            BufferedReader sin = new BufferedReader(new InputStreamReader(p.getInputStream()));
            result = sin.readLine();
            sin.close();
            return result.split(" ");
        } catch (Exception ex) {
            System.out.println("ERROR: " + ex.getMessage());
            return null;
        }
    }
}
