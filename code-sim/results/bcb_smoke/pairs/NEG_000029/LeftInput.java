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
    public static Properties loadProperties() {
        try {
            if (url == null) url = ClassLoader.getSystemResource("application.properties");
            if (url == null) throw new FileNotFoundException("application.properties");
            props = new Properties();
            props.load(url.openStream());
            Enumeration e = System.getProperties().propertyNames();
            String key;
            while (e.hasMoreElements()) {
                key = (String) e.nextElement();
                props.setProperty(key, System.getProperty(key));
            }
            return props;
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.toString(), e);
        }
        return null;
    }
}
