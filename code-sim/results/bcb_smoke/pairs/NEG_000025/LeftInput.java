import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

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
