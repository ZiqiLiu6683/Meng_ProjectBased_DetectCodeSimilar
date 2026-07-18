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
	public static String downloadWebpage2(String address) throws MalformedURLException, IOException {
		URL url = new URL(address);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		HttpURLConnection.setFollowRedirects(true);
		String encoding = conn.getContentEncoding();
		InputStream is = null;
		if(encoding != null && encoding.equalsIgnoreCase("gzip")) {
			is = new GZIPInputStream(conn.getInputStream());
		} else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
			is = new InflaterInputStream(conn.getInputStream());
		} else {
			is = conn.getInputStream();
		}
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line;
		String page = "";
		while((line = br.readLine()) != null) {
			page += line + "\n";
		}
		br.close();
		return page;
	}
}
