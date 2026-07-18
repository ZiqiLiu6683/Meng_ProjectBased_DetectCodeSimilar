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
    public synchronized void reading() {
        if (active_writers == 0 && writer_locks.size() == 0) ++active_readers; else {
            ++waiting_readers;
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
    }
}
