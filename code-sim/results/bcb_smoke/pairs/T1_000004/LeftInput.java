import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class LeftInput {
    private String[] execSingleLineOutputCmd(String cmdWithParams) {
        String result = "";
        try {
            Process p = Runtime.getRuntime().exec(cmdWithParams.split(" "));
            BufferedReader sin = new BufferedReader(new InputStreamReader(p.getInputStream()));
            result = sin.readLine();
            sin.close();
            return result.split(" ");
        } catch (Exception ex) {
            System.out.println("ERROR: " + ex.getMessage());
            return null;
        }
    }
}
