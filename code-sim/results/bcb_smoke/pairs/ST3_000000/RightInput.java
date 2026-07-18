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
    protected void onlyFileCopy(File in, File out) throws IOException {
        FileChannel inChannel = new FileInputStream(in).getChannel();
        FileChannel outChannel = new FileOutputStream(out).getChannel();
        try {
            int maxCount = (1024 * 1024 * 64) - (1024 * 32);
            long size = inChannel.size();
            long pos = 0;
            while (pos < size) {
                pos += inChannel.transferTo(pos, maxCount, outChannel);
            }
        } catch (IOException e) {
            throw e;
        } finally {
            if (inChannel != null) inChannel.close();
            if (outChannel != null) outChannel.close();
        }
    }
}
