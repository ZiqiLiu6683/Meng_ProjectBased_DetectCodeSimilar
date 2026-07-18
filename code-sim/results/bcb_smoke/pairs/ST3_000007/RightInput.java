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
    public void removeRealm(final List<Integer> realmIds) {
        try {
            connection.setAutoCommit(false);
            new ProcessEnvelope().executeNull(new ExecuteProcessAbstractImpl(connection, false, false, true, true) {

                @Override
                public void executeProcessReturnNull() throws SQLException {
                    psImpl = connImpl.prepareStatement(sqlCommands.getProperty("realm.remove"));
                    Iterator<Integer> iter = realmIds.iterator();
                    int realmId;
                    while (iter.hasNext()) {
                        realmId = iter.next();
                        psImpl.setInt(1, realmId);
                        psImpl.executeUpdate();
                        cmDB.removeRealm(realmId);
                    }
                }
            });
            connection.commit();
        } catch (SQLException sqle) {
            log.error(sqle);
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                }
            }
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ex) {
                }
            }
        }
    }
}
