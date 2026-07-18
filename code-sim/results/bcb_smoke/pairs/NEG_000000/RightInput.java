import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class RightInput {
	public static void copyFile4(File srcFile, File destFile) throws IOException {
		InputStream in = new FileInputStream(srcFile);
		OutputStream out = new FileOutputStream(destFile);
		IOUtils.copy(in, out);
		in.close();
		out.close();
	}
}
