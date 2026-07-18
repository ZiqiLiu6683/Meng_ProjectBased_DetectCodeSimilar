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
    private int binarySearch(double mouseCoord, int[][] pointCoords) {
        int left = 0;
        int right = pointCoords.length;
        while (left < right) {
            int middle = (left + right) / 2;
            if (pointCoords[middle][0] < mouseCoord - size / 2) {
                left = middle + 1;
            } else {
                right = middle;
            }
        }
        if (right < pointCoords.length && isInside(mouseCoord, pointCoords[right][0])) {
            return right;
        }
        return -1;
    }
}
