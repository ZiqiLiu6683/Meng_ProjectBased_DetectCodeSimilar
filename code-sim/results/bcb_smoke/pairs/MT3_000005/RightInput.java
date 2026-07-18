import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class RightInput {
    public static double[][] transposeMatrix(double[][] vector) {
        double[][] tm = new double[vector[0].length][vector.length];
        for (int i = 0; i < tm.length; i++) {
            for (int j = 0; j < tm[i].length; j++) {
                tm[i][j] = vector[j][i];
            }
        }
        return tm;
    }
}
