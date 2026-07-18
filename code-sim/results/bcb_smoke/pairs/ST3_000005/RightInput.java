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
    public void removerQuestaoMultiplaEscolha(QuestaoMultiplaEscolha multiplaEscolha) throws ClassNotFoundException, SQLException {
        this.criaConexao(false);
        String sql = "DELETE FROM \"Disciplina\"    " + "      WHERE ID_Disciplina =  ? )";
        PreparedStatement stmt = null;
        try {
            stmt = connection.prepareStatement(sql);
            stmt.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            try {
                stmt.close();
                this.fechaConexao();
            } catch (SQLException e) {
                throw e;
            }
        }
    }
}
