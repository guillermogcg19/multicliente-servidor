package ServidorMulti;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public class Database {
    private final Connection conn;

    public Database(String ruta) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + ruta);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
        }
        inicializar();
    }

    private void inicializar() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS usuarios(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  nombre TEXT UNIQUE NOT NULL,
                  pass TEXT NOT NULL
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS bloqueos(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  bloqueador TEXT NOT NULL,
                  bloqueado TEXT NOT NULL,
                  UNIQUE(bloqueador,bloqueado)
                )
            """);
        }
    }

    public void inicializarRanking() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
              CREATE TABLE IF NOT EXISTS ranking(
                usuario TEXT PRIMARY KEY,
                puntos INTEGER DEFAULT 0,
                victorias INTEGER DEFAULT 0,
                empates INTEGER DEFAULT 0,
                derrotas INTEGER DEFAULT 0
              )
            """);
            st.execute("""
              CREATE TABLE IF NOT EXISTS duelos(
                a TEXT NOT NULL,
                b TEXT NOT NULL,
                victorias_a INTEGER DEFAULT 0,
                victorias_b INTEGER DEFAULT 0,
                empates INTEGER DEFAULT 0,
                PRIMARY KEY(a,b)
              )
            """);
        }
    }

    public boolean registrar(String u, String p) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO usuarios(nombre,pass) VALUES(?,?)")) {
            ps.setString(1, u); ps.setString(2, p); ps.executeUpdate(); return true;
        } catch (SQLException e) { return false; }
    }

    public boolean login(String u, String p) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM usuarios WHERE nombre=? AND pass=?")) {
            ps.setString(1, u); ps.setString(2, p);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    public boolean existeUsuario(String u) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM usuarios WHERE nombre=?")) {
            ps.setString(1, u);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    public boolean bloquear(String de, String a) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO bloqueos(bloqueador,bloqueado) VALUES(?,?)")) {
            ps.setString(1, de); ps.setString(2, a); return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean desbloquear(String de, String a) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM bloqueos WHERE bloqueador=? AND bloqueado=?")) {
            ps.setString(1, de); ps.setString(2, a); return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean estaBloqueado(String receptor, String emisor) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM bloqueos WHERE bloqueador=? AND bloqueado=?")) {
            ps.setString(1, receptor); ps.setString(2, emisor);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    public Set<String> obtenerBloqueados(String de) {
        Set<String> out = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT bloqueado FROM bloqueos WHERE bloqueador=?")) {
            ps.setString(1, de);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(rs.getString(1)); }
        } catch (SQLException e) {}
        return out;
    }

    public void actualizarResultadoVictoria(String ganador, String perdedor) {
        try {
            sumarRanking(ganador, 2, "victorias");
            sumarRanking(perdedor, 0, "derrotas");
            sumarDuelosVictoria(ganador, perdedor);
        } catch (SQLException e) {
            System.err.println("ranking win error: " + e.getMessage());
        }
    }

    public void actualizarResultadoEmpate(String a, String b) {
        try {
            sumarRanking(a, 1, "empates");
            sumarRanking(b, 1, "empates");
            sumarDuelosEmpate(a, b);
        } catch (SQLException e) {
            System.err.println("ranking draw error: " + e.getMessage());
        }
    }

    private void sumarRanking(String u, int pts, String campo) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO ranking(usuario,puntos,victorias,empates,derrotas)
            VALUES(?,0,0,0,0)
            ON CONFLICT(usuario) DO NOTHING
        """)) {
            ps.setString(1, u); ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ranking SET puntos=puntos+?, "+campo+"="+campo+"+1 WHERE usuario=?")) {
            ps.setInt(1, pts); ps.setString(2, u); ps.executeUpdate();
        }
    }

    private void ensureDuelos(String x, String y) throws SQLException {
        String a = x.compareTo(y) <= 0 ? x : y;
        String b = x.compareTo(y) <= 0 ? y : x;
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO duelos(a,b,victorias_a,victorias_b,empates)
            VALUES(?,?,0,0,0)
            ON CONFLICT(a,b) DO NOTHING
        """)) {
            ps.setString(1, a); ps.setString(2, b); ps.executeUpdate();
        }
    }

    private void sumarDuelosVictoria(String winner, String loser) throws SQLException {
        ensureDuelos(winner, loser);
        boolean winnerIsA = winner.compareTo(loser) <= 0;
        String sql = winnerIsA ?
            "UPDATE duelos SET victorias_a = victorias_a + 1 WHERE a=? AND b=?" :
            "UPDATE duelos SET victorias_b = victorias_b + 1 WHERE a=? AND b=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String a = winner.compareTo(loser) <= 0 ? winner : loser;
            String b = winner.compareTo(loser) <= 0 ? loser : winner;
            ps.setString(1, a); ps.setString(2, b); ps.executeUpdate();
        }
    }

    private void sumarDuelosEmpate(String x, String y) throws SQLException {
        ensureDuelos(x, y);
        String a = x.compareTo(y) <= 0 ? x : y;
        String b = x.compareTo(y) <= 0 ? y : x;
        try (PreparedStatement ps = conn.prepareStatement("UPDATE duelos SET empates=empates+1 WHERE a=? AND b=?")) {
            ps.setString(1, a); ps.setString(2, b); ps.executeUpdate();
        }
    }

    public String obtenerRankingGeneral() {
        StringBuilder sb = new StringBuilder("Ranking:\n");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT usuario,puntos,victorias,empates,derrotas FROM ranking ORDER BY puntos DESC, victorias DESC, usuario ASC")) {
            int pos = 1;
            while (rs.next()) {
                sb.append(pos++).append(". ")
                  .append(rs.getString(1)).append(" - ")
                  .append(rs.getInt(2)).append(" pts (")
                  .append(rs.getInt(3)).append("V ")
                  .append(rs.getInt(4)).append("E ")
                  .append(rs.getInt(5)).append("D)\n");
            }
        } catch (SQLException e) { return "Error ranking."; }
        return sb.toString();
    }

    public String compararHeadToHead(String x, String y) {
        String a = x.compareTo(y) <= 0 ? x : y;
        String b = x.compareTo(y) <= 0 ? y : x;
        try (PreparedStatement ps = conn.prepareStatement("SELECT victorias_a,victorias_b,empates FROM duelos WHERE a=? AND b=?")) {
            ps.setString(1, a); ps.setString(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "Sin enfrentamientos entre " + x + " y " + y;
                int va = rs.getInt(1), vb = rs.getInt(2), e = rs.getInt(3);
                int total = va + vb + e;
                if (total == 0) return "Sin enfrentamientos entre " + x + " y " + y;
                double px = (x.equals(a) ? va : vb) * 100.0 / total;
                double py = (y.equals(b) ? vb : va) * 100.0 / total;
                return x + ": " + String.format("%.1f", px) + "% | " + y + ": " + String.format("%.1f", py) + "%  (" + total + " juegos)";
            }
        } catch (SQLException e) { return "Error comparacion."; }
    }
}
