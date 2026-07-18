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
    public static void main(String[] argv) throws Exception {
        if (argv.length == 0) {
            printUsage();
            return;
        }
        for (int iArg = 0; iArg < argv.length; iArg++) {
            String arg = argv[iArg];
            if (arg.startsWith("-h")) {
                printUsage();
                return;
            }
            System.out.println("**** START OF EXECUTION of " + arg + "." + methodToRun + " " + signatureToPrintOut + " ****.");
            Class klass = Class.forName(arg);
            Method method = klass.getDeclaredMethod(methodToRun, noparams);
            Object result = method.invoke(null, (Object[]) noparams);
            System.out.println("**** RESULT: " + result);
        }
    }
}
