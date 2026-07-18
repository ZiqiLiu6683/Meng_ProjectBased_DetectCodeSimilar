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
    public Server(ServerConfig config) {
        super("server-" + config.name.replace(" ", "-").toLowerCase());
        this.config = config;
        this.debugEnabled = config.debugEnabled;
        if (config.readThreads <= 0 || config.writeThreads <= 0 || (config.enableWorkers && config.workerThreads <= 0)) {
            if (config.enableWorkers) {
                throw new RuntimeException("You should at least use one reader thread, one writer thread and one worker thread");
            } else {
                throw new RuntimeException("You should at least use one write thread and one read thread");
            }
        }
    }
}
