package servidormulti;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public class Database {
    private static final String DB_URL = "jdbc:sqlite:chat.db";

    public Database() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement st = conn.createStatement()) {

            // Crear tablas si no existen
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS usuarios (
                    usuario TEXT PRIMARY KEY,
                    pass TEXT NOT NULL
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS bloqueos (
                    bloqueador TEXT,
                    bloqueado TEXT,
                    PRIMARY KEY (bloqueador, bloqueado)
                )
            """);

        } catch (SQLException e) {
            System.err.println("Error inicializando la base de datos: " + e.getMessage());
        }
    }

    // ----------- Usuarios -----------
    public boolean registrar(String usuario, String pass) {
        String sql = "INSERT INTO usuarios (usuario, pass) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setString(2, pass);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean verificarLogin(String usuario, String pass) {
        String sql = "SELECT * FROM usuarios WHERE usuario = ? AND pass = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setString(2, pass);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean usuarioExiste(String usuario) {
        String sql = "SELECT usuario FROM usuarios WHERE usuario = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    // ----------- Bloqueos -----------
    public boolean bloquear(String bloqueador, String bloqueado) {
        String sql = "INSERT OR IGNORE INTO bloqueos VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bloqueador);
            ps.setString(2, bloqueado);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean desbloquear(String bloqueador, String bloqueado) {
        String sql = "DELETE FROM bloqueos WHERE bloqueador = ? AND bloqueado = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bloqueador);
            ps.setString(2, bloqueado);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean estaBloqueado(String bloqueador, String candidato) {
        String sql = "SELECT 1 FROM bloqueos WHERE bloqueador = ? AND bloqueado = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bloqueador);
            ps.setString(2, candidato);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public Set<String> listaBloqueados(String bloqueador) {
        Set<String> lista = new HashSet<>();
        String sql = "SELECT bloqueado FROM bloqueos WHERE bloqueador = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bloqueador);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(rs.getString("bloqueado"));
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo bloqueos: " + e.getMessage());
        }
        return lista;
    }
}
