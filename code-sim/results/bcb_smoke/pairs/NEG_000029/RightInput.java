import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class RightInput {
	public static String downloadWebpage3(String address) throws ClientProtocolException, IOException {
		HttpClient client = HttpClientBuilder.create().build();
		HttpGet request = new HttpGet(address);
		HttpResponse response = client.execute(request);
		BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
		String line;
		String page = "";
		while((line = br.readLine()) != null) {
			page += line + "\n";
		}
		br.close();
		return page;
	}
}
