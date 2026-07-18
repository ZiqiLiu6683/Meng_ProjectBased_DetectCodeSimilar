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
    public static void copyFile(File in, File out) throws IOException {
        FileChannel sourceChannel = new FileInputStream(in).getChannel();
        FileChannel destinationChannel = new FileOutputStream(out).getChannel();
        try {
            sourceChannel.transferTo(0, sourceChannel.size(), destinationChannel);
        } finally {
            sourceChannel.close();
            destinationChannel.close();
        }
    }
}
