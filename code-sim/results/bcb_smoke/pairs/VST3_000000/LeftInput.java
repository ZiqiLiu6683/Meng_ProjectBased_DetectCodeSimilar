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
    public void doOpen() {
        final JFileChooser chooser = new JFileChooser();
        chooser.addChoosableFileFilter(new FileFilter() {

            public boolean accept(final File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".log");
            }

            public String getDescription() {
                return "Log files";
            }
        });
        if (chooser.showOpenDialog(x_main) == JFileChooser.APPROVE_OPTION) {
            loadFile(chooser.getSelectedFile().getAbsolutePath(), true);
        }
    }
}
