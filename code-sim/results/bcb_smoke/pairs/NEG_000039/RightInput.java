import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

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
