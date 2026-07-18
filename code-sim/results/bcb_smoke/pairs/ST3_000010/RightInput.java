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
    public static void deltree(File directory) {
        if (directory.exists() && directory.isDirectory()) {
            File[] fileArray = directory.listFiles();
            if (fileArray != null) {
                for (int i = 0; i < fileArray.length; i++) {
                    if (fileArray[i].isDirectory()) {
                        deltree(fileArray[i]);
                    } else {
                        fileArray[i].delete();
                    }
                }
            }
            directory.delete();
        }
    }
}
