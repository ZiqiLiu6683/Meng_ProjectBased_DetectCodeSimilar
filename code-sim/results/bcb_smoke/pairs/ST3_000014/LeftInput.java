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

public class LeftInput {
    public static void mattran_j(double a[][], double at[][], int n, int p) {
        int i, j;
        for (i = 0; i < n; i++) {
            for (j = 0; j < p; j++) {
                at[j][i] = a[i][j];
            }
        }
    }
}
