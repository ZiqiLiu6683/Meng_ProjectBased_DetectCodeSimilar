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
    private void writeGeneProductCount(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null && !abort) {
            progress = calculateProgress(line.length());
            String data[] = line.split(SEPARATOR_TAB);
            GeneProductCount entry = new GeneProductCount();
            entry.setTerm((Term) session.get(Term.class, Integer.valueOf(data[0])));
            entry.setCode(data[1]);
            if (!data[2].equals("\\N")) entry.setSpeciesdbname(data[2]);
            if (!data[3].equals("\\N")) entry.setSpecies((Species) session.get(Species.class, Integer.valueOf(data[3])));
            entry.setProductCount(Integer.valueOf(data[4]));
            insertObject(entry);
        }
        reader.close();
    }
}
