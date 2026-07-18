import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class LeftInput {
    private byte[] readBinaryData(RandomAccessFile raf) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int size = Utils.readUINT16(raf);
        for (int i = 0; i < size; i++) {
            bos.write(raf.read());
        }
        return bos.toByteArray();
    }
}
