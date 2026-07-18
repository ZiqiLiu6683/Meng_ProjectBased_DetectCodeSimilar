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
    public static Object[][] dimTransform(Object[][] obj) {
        if ((obj == null) || (obj.length <= 0)) {
            return null;
        }
        Object[][] newArr = new Object[obj[0].length][obj.length];
        for (int i = 0; i < newArr.length; ++i) {
            for (int j = 0; j < obj.length; ++j) {
                newArr[i][j] = obj[j][i];
            }
        }
        return newArr;
    }
}
