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
    private void copyFileIfModified(File source, File destination) throws IOException {
        if (destination.lastModified() < source.lastModified()) {
            FileUtils.copyFile(source.getCanonicalFile(), destination);
            destination.setLastModified(source.lastModified());
        }
    }
}
