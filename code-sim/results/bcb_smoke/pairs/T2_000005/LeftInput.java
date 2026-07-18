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
    public static final Object concat(Object ary, Object ary1) {
        int len = Array.getLength(ary) + Array.getLength(ary1);
        if (!ary.getClass().getComponentType().equals(ary1.getClass().getComponentType())) throw new IllegalArgumentException("These concated array component type are different.");
        Object dst = Array.newInstance(ary.getClass().getComponentType(), len);
        System.arraycopy(ary, 0, dst, 0, Array.getLength(ary));
        System.arraycopy(ary1, 0, dst, Array.getLength(ary), Array.getLength(ary1));
        return dst;
    }
}
