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
