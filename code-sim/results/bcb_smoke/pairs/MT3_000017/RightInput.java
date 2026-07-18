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

public class RightInput {
    public Matrix transpose() {
        Matrix matrixT = new Matrix(columnCount, rowCount);
        int i, j;
        for (i = 0; i < rowCount; i++) {
            for (j = 0; j < columnCount; j++) {
                matrixT.matrix[j][i] = matrix[i][j];
            }
        }
        return matrixT;
    }
}
