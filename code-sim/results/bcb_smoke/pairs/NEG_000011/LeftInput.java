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
    public void listen() {
        try {
            int port = 25;
            ServerSocket srv = new ServerSocket(port);
            int i = 0;
            log.debug("starting to listen on port" + port);
            while (i++ < 2) {
                Socket socket = srv.accept();
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                System.out.println("got a req");
                bw.write("220 POPAnything SMTP Server is ready n kicking\r\n");
                bw.flush();
                SMTPService ss = new SMTPService();
                ss.setSocket(socket);
                ss.run();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
