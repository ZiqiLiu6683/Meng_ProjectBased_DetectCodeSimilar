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
    public double[] readDataFrameVariable(int frame) {
        if (!getDataWindow().isWithinWindowRange(frame)) {
            System.out.println("CAUTION! accessing frame " + frame + ", outside variable scaling window!");
        }
        double[] tempret = new double[getDataLayout().getChannelCount()];
        for (int k = 0; k < tempret.length; k++) {
            tempret[k] = readDataPointVariableImpl(k, frame);
        }
        return tempret;
    }
}
