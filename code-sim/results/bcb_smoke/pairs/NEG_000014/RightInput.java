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
	public static String downloadWebpage1(String address) throws MalformedURLException, IOException {
		URL url = new URL(address);
		BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
		String line;
		String page = "";
		while((line = br.readLine()) != null) {
			page += line + "\n";
		}
		br.close();
		return page;
	}
}
