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
    public String zip(String filename) {
        String rvalue = "no";
        try {
            con = getConnection();
            String folder = dms_home + FS + "www" + FS + "datafiles";
            String zipfilename = dms_home + FS + "DataBackup" + FS + filename + ".zip";
            String outFilename = zipfilename;
            File fd = new File(folder);
            File filenames[] = fd.listFiles();
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outFilename));
            byte[] buf = new byte[BUFFER];
            String tempfilename = "";
            for (int i = 0; i < filenames.length; i++) {
                FileInputStream in = new FileInputStream(filenames[i]);
                tempfilename = filenames[i].getName();
                out.putNextEntry(new ZipEntry(tempfilename));
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.closeEntry();
                in.close();
            }
            out.close();
            rvalue = "yes";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rvalue;
    }
}
