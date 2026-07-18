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
    public static void copyFile(File src, File dest) throws IOException {
        FileInputStream fis = new FileInputStream(src);
        FileOutputStream fos = new FileOutputStream(dest);
        java.nio.channels.FileChannel channelSrc = fis.getChannel();
        java.nio.channels.FileChannel channelDest = fos.getChannel();
        channelSrc.transferTo(0, channelSrc.size(), channelDest);
        fis.close();
        fos.close();
    }
}
