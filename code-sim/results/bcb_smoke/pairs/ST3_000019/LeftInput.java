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

public class LeftInput {
    public static void main(String[] args) {
        System.out.println("Chapter 6 example 6: Absolute Positioning of an Image");
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, new FileOutputStream("Chap0606.pdf"));
            document.open();
            Image png = Image.getInstance("hitchcock.png");
            png.setAbsolutePosition(171, 250);
            document.add(png);
            png.setAbsolutePosition(342, 500);
            document.add(png);
        } catch (DocumentException de) {
            System.err.println(de.getMessage());
        } catch (IOException ioe) {
            System.err.println(ioe.getMessage());
        }
        document.close();
    }
}
