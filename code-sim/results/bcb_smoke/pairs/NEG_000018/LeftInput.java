import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class LeftInput {
    private void createEntry(final String name) throws IOException {
        LogUtils.infof(this, "adding to zip: opennms-system-report/%s", name);
        m_zipOutputStream.putNextEntry(new ZipEntry("opennms-system-report/" + name));
    }
}
