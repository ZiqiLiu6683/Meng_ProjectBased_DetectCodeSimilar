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
    public void write(Message message) throws IOException {
        if (this.startDate == null) {
            writeHeader();
            this.startDate = message.getDate();
            this.lastDate = this.startDate;
        }
        long date = message.getDate().getTime() / 1000 - this.lastDate.getTime() / 1000;
        this.lastDate = message.getDate();
        String chan = message.getChannel();
        if (!this.channels.contains(chan)) this.channels.add(chan);
        String src = message.getAvatar();
        if (!this.sources.contains(src)) this.sources.add(src);
        this.out.println("\t[" + date + "," + this.channels.indexOf(chan) + "," + this.sources.indexOf(src) + ",\"" + escape(message.getContent()) + "\"],");
    }
}
