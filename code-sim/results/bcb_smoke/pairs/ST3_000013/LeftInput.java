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
    public void copyFile(File in, File out) throws Exception {
        FileChannel ic = new FileInputStream(in).getChannel();
        FileChannel oc = new FileOutputStream(out).getChannel();
        ic.transferTo(0, ic.size(), oc);
        ic.close();
        oc.close();
    }
}
