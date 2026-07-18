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
	public static void copyFile6(File srcFile, File destFile) throws FileNotFoundException {
		Scanner s = new Scanner(srcFile);
		PrintWriter pw = new PrintWriter(destFile);
		while(s.hasNextLine()) {
			pw.println(s.nextLine());
		}
		pw.close();
		s.close();
	}
}
