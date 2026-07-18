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
    protected void addFile(File newEntry, String name) {
        if (newEntry.isDirectory()) {
            return;
        }
        try {
            ZipEntry ze = new ZipEntry(name);
            mZos.putNextEntry(ze);
            FileInputStream fis = new FileInputStream(newEntry);
            byte fdata[] = new byte[512];
            int readCount = 0;
            while ((readCount = fis.read(fdata)) != -1) {
                mZos.write(fdata, 0, readCount);
            }
            fis.close();
            mZos.closeEntry();
            mObserverCont.setNext(ze);
            mObserverCont.setCount(++miCurrentCount);
        } catch (Exception ex) {
            mObserverCont.setError(ex.getMessage());
        }
    }
}
