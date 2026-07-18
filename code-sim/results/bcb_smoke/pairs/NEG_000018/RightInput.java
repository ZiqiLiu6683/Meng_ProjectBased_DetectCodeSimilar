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

public class RightInput {
	public static void ZipFiles(File zipfile, File[] files) throws IOException {
		byte[] buffer = new byte[1024];
		FileOutputStream fos = new FileOutputStream(zipfile);
		ZipOutputStream zos = new ZipOutputStream(fos);
		
		// For Each File
		for(int i = 0; i < files.length; i++) {
			// Open File
			File src = files[i];
			FileInputStream fis = new FileInputStream(src);
			
			//Create new zip entry
			ZipEntry entry = new ZipEntry(src.getName());
			zos.putNextEntry(entry);
			
			//Write the file to the entry in the zip file (compressed)
			int length;
			while((length = fis.read(buffer)) > 0) {
				zos.write(buffer, 0, length);
			}
			zos.closeEntry();
			
			//Close Original File
			fis.close();
		}
		
		// Close Zip File
		zos.close();
	}
}
