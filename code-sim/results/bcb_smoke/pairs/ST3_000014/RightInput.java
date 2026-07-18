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
    public static void mattran_f77(double a[][], double at[][], int n, int p) {
        int i, j;
        for (i = 1; i <= n; i++) {
            for (j = 1; j <= p; j++) {
                at[j][i] = a[i][j];
            }
        }
    }
}
