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
    public static Object invokeInitializer(Class cls, int methodID, int argAddress, boolean isJvalue, boolean isDotDotStyle) throws Exception {
        VM_Method mth = VM_MethodDictionary.getValue(methodID);
        VM_Type[] argTypes = mth.getParameterTypes();
        Class[] argClasses = new Class[argTypes.length];
        for (int i = 0; i < argClasses.length; i++) {
            argClasses[i] = argTypes[i].getClassForType();
        }
        Constructor constMethod = cls.getConstructor(argClasses);
        if (constMethod == null) throw new Exception("Constructor not found");
        int varargAddress;
        if (isDotDotStyle) varargAddress = pushVarArgToSpillArea(methodID, false); else varargAddress = argAddress;
        Object argObjs[];
        if (isJvalue) argObjs = packageParameterFromJValue(mth, argAddress); else argObjs = packageParameterFromVarArg(mth, varargAddress);
        Object newobj = constMethod.newInstance(argObjs);
        return newobj;
    }
}
