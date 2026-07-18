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
    public static void main(String[] args) {
        try {
            String jar = "./test340.jar";
            URLClassLoader theLoader = new URLClassLoader(new URL[] { new URL("file:" + jar) });
            Object theLoadedClass = Class.forName("test340c", true, theLoader).newInstance();
            String[] array = new String[] {};
            Method main = theLoadedClass.getClass().getMethod("main", new Class[] { array.getClass() });
            main.invoke(theLoadedClass, new Object[] { new String[] {} });
        } catch (Throwable t) {
            System.exit(42);
        }
        System.exit(43);
    }
}
