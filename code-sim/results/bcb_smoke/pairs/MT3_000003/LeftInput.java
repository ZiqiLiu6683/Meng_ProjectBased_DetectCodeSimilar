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
import java.lang.reflect.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class LeftInput {
    public static void zipDirectory(File inputFile, ZipOutputStream zipOutputStream) throws IOException {
        String[] dirList = inputFile.list();
        byte[] readBuffer = new byte[1024];
        int bytesIn = 0;
        for (int i = 0; i < dirList.length; i++) {
            File file = new File(inputFile, dirList[i]);
            if (file.isDirectory()) {
                zipDirectory(file, zipOutputStream);
                continue;
            }
            FileInputStream fileInputStream = new FileInputStream(file);
            ZipEntry zipEntry = new ZipEntry(file.getPath());
            zipOutputStream.putNextEntry(zipEntry);
            while ((bytesIn = fileInputStream.read(readBuffer)) != -1) {
                zipOutputStream.write(readBuffer, 0, bytesIn);
            }
            fileInputStream.close();
        }
    }
}
