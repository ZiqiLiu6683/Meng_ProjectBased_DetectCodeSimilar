import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

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
