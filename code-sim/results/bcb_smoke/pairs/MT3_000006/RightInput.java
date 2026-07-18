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
    public static String readFromURL(String sURL) {
        logger.info("com.rooster.utils.URLReader.readFromURL - Entry");
        String sWebPage = "";
        try {
            URL url = new URL(sURL);
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            String inputLine = "";
            while ((inputLine = in.readLine()) != null) {
                sWebPage += inputLine;
            }
            in.close();
        } catch (Exception e) {
            logger.debug("com.rooster.utils.URLReader.readFromURL - Error" + e);
        }
        logger.info("com.rooster.utils.URLReader.readFromURL - Exit");
        return sWebPage;
    }
}
