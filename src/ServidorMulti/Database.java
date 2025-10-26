package ServidorMulti;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public class Database {
    private final Connection conn;

    public Database(String ruta) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + ruta);
        inicializar();
    }

    private void inicializar() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS usuarios (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT UNIQUE NOT NULL,
                    pass TEXT NOT NULL
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS bloqueos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    bloqueador TEXT NOT NULL,
                    bloqueado TEXT NOT NULL,
                    UNIQUE(bloqueador, bloqueado)
                )
            """);
        }
    }

    public boolean registrar(String usuario, String pass) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO usuarios (nombre, pass) VALUES (?, ?)")) {
            ps.setString(1, usuario);
            ps.setString(2, pass);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error al registrar: " + e.getMessage());
            return false;
        }
    }

    public boolean login(String usuario, String pass) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM usuarios WHERE nombre=? AND pass=?")) {
            ps.setString(1, usuario);
            ps.setString(2, pass);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error al iniciar sesión: " + e.getMessage());
            return false;
        }
    }

    public boolean existeUsuario(String usuario) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM usuarios WHERE nombre=?")) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean bloquear(String bloqueador, String bloqueado) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO bloqueos (bloqueador, bloqueado) VALUES (?, ?)")) {
            ps.setString(1, bloqueador);
            ps.setString(2, bloqueado);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean desbloquear(String bloqueador, String bloqueado) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM bloqueos WHERE bloqueador=? AND bloqueado=?")) {
            ps.setString(1, bloqueador);
            ps.setString(2, bloqueado);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean estaBloqueado(String bloqueador, String bloqueado) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM bloqueos WHERE bloqueador=? AND bloqueado=?")) {
            ps.setString(1, bloqueador);
            ps.setString(2, bloqueado);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public Set<String> obtenerBloqueados(String bloqueador) {
        Set<String> res = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT bloqueado FROM bloqueos WHERE bloqueador=?")) {
            ps.setString(1, bloqueador);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) res.add(rs.getString("bloqueado"));
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener bloqueados: " + e.getMessage());
        }
        return res;
    }
}
