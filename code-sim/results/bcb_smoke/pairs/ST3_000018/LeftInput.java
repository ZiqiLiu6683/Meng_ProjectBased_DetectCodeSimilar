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
    public static void doVersionCheck(View view) {
        view.showWaitCursor();
        try {
            URL url = new URL(jEdit.getProperty("version-check.url"));
            InputStream in = url.openStream();
            BufferedReader bin = new BufferedReader(new InputStreamReader(in));
            String line;
            String version = null;
            String build = null;
            while ((line = bin.readLine()) != null) {
                if (line.startsWith(".version")) version = line.substring(8).trim(); else if (line.startsWith(".build")) build = line.substring(6).trim();
            }
            bin.close();
            if (version != null && build != null) {
                if (jEdit.getBuild().compareTo(build) < 0) newVersionAvailable(view, version, url); else {
                    GUIUtilities.message(view, "version-check" + ".up-to-date", new String[0]);
                }
            }
        } catch (IOException e) {
            String[] args = { jEdit.getProperty("version-check.url"), e.toString() };
            GUIUtilities.error(view, "read-error", args);
        }
        view.hideWaitCursor();
    }
}
