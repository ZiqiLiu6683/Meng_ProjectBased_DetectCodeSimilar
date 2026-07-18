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
    public static double[][] transpose(double[][] matrix) {
        double[][] transposed = new double[matrix[0].length][matrix.length];
        int mLength = matrix.length, m0Length = matrix[0].length;
        for (int i = 0; i < mLength; i++) {
            for (int j = 0; j < m0Length; j++) {
                transposed[j][i] = matrix[i][j];
            }
        }
        return transposed;
    }
}
