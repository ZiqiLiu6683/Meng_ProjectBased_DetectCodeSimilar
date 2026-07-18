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

public class RightInput {
    public void doSaveAs() {
        final JFileChooser chooser = new JFileChooser();
        chooser.addChoosableFileFilter(new FileFilter() {

            public boolean accept(final File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".png");
            }

            public String getDescription() {
                return "PNG files";
            }
        });
        if (chooser.showSaveDialog(x_main) == JFileChooser.APPROVE_OPTION) {
            saveAs(chooser.getSelectedFile().getAbsolutePath());
        }
    }
}
