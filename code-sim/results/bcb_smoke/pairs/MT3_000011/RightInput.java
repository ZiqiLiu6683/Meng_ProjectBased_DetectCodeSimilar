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
    public IntSquareMatrix copyLowerToUpper() {
        for (int i = 0; i < cols - 1; i++) {
            for (int j = i + 1; j < cols; j++) {
                flmat[i][j] = flmat[j][i];
            }
        }
        return this;
    }
}
