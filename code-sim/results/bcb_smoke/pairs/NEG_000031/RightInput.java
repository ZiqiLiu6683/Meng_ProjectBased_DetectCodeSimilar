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
	public static <T extends Comparable<T>> int binarySearch4(T[] arr, T key) {
		int imin = 0;
		int imax = arr.length - 1;
		while(imin <= imax) {
			int imid = imin + (imax - imin)/2;
			if (key.compareTo(arr[imid]) < 0)//(key < arr[imid])
				imax = imid-1;
			else if (key.compareTo(arr[imid]) > 0)//(key > arr[imid])
				imin = imid + 1;
			else
				return imid;
		}
		return -1;
	}
}
