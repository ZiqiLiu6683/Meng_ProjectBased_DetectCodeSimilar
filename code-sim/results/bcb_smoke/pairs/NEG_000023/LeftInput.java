import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class LeftInput {
    public static boolean isLocked() {
        AppInfo.checkState();
        file = FS.makeConfigPath(AppInfo.internalName + ".lock");
        try {
            lock = new FileOutputStream(file).getChannel().tryLock();
        } catch (FileNotFoundException exception) {
            return false;
        } catch (IOException exception) {
            return false;
        }
        return (lock == null);
    }
}
