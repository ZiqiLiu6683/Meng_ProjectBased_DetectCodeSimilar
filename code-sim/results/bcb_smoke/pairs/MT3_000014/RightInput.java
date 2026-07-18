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
    public void removeRoom(int thisRoom) {
        DBConnection con = null;
        try {
            con = DBServiceManager.allocateConnection();
            con.setAutoCommit(false);
            String query = "DELETE FROM cafe_Chat_Category WHERE cafe_Chat_Category_id=? ";
            PreparedStatement ps = con.prepareStatement(query);
            ps.setInt(1, thisRoom);
            ps.executeUpdate();
            query = "DELETE FROM cafe_Chatroom WHERE cafe_chatroom_category=? ";
            ps = con.prepareStatement(query);
            ps.setInt(1, thisRoom);
            ps.executeUpdate();
            con.commit();
            con.setAutoCommit(true);
        } catch (SQLException e) {
            try {
                con.rollback();
            } catch (SQLException sqle) {
            }
        } finally {
            if (con != null) con.release();
        }
    }
}
