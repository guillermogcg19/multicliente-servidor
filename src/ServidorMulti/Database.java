package ServidorMulti;

import java.sql.*;
import java.util.*;

public class Database {
    private final Connection conn;

    public Database(String archivo) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + archivo);
        inicializarRanking();
        inicializarGrupos();
    }

    // ------------------ RANKING ------------------

    public void inicializarRanking() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS ranking (" +
                       "usuario TEXT PRIMARY KEY, " +
                       "puntos INTEGER DEFAULT 0, " +
                       "victorias INTEGER DEFAULT 0, " +
                       "empates INTEGER DEFAULT 0, " +
                       "derrotas INTEGER DEFAULT 0)");
        }
    }

    public void actualizarResultadoVictoria(String ganador, String perdedor) {
        try {
            PreparedStatement ps1 = conn.prepareStatement(
                "INSERT INTO ranking(usuario, puntos, victorias, empates, derrotas) VALUES(?,2,1,0,0) " +
                "ON CONFLICT(usuario) DO UPDATE SET puntos=puntos+2, victorias=victorias+1"
            );
            ps1.setString(1, ganador);
            ps1.executeUpdate();

            PreparedStatement ps2 = conn.prepareStatement(
                "INSERT INTO ranking(usuario, puntos, victorias, empates, derrotas) VALUES(?,0,0,0,1) " +
                "ON CONFLICT(usuario) DO UPDATE SET derrotas=derrotas+1"
            );
            ps2.setString(1, perdedor);
            ps2.executeUpdate();

            ps1.close();
            ps2.close();
        } catch (Exception e) {
            System.err.println("Error al actualizar victoria: " + e.getMessage());
        }
    }

    public void actualizarResultadoEmpate(String jugadorA, String jugadorB) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ranking(usuario, puntos, victorias, empates, derrotas) VALUES(?,1,0,1,0) " +
                "ON CONFLICT(usuario) DO UPDATE SET puntos=puntos+1, empates=empates+1"
            );
            ps.setString(1, jugadorA);
            ps.executeUpdate();
            ps.setString(1, jugadorB);
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            System.err.println("Error al actualizar empate: " + e.getMessage());
        }
    }

    public void mostrarRankingGeneral(UnCliente cli) {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT usuario, puntos, victorias, empates, derrotas FROM ranking ORDER BY puntos DESC"
        )) {
            ResultSet rs = ps.executeQuery();
            StringBuilder sb = new StringBuilder();
            sb.append("[ranking] Tabla general:\n");
            while (rs.next()) {
                sb.append(rs.getString("usuario")).append(" -> ")
                  .append("Pts: ").append(rs.getInt("puntos")).append(" | ")
                  .append("V: ").append(rs.getInt("victorias")).append(" | ")
                  .append("E: ").append(rs.getInt("empates")).append(" | ")
                  .append("D: ").append(rs.getInt("derrotas")).append("\n");
            }
            cli.enviar(sb.toString());
        } catch (Exception e) {
            cli.enviar("[ranking] Error al obtener el ranking general.");
        }
    }

    public void mostrarComparacion(UnCliente cli, String j1, String j2) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT usuario, victorias, empates, derrotas FROM ranking WHERE usuario IN (?, ?)"
            );
            ps.setString(1, j1);
            ps.setString(2, j2);
            ResultSet rs = ps.executeQuery();

            Map<String, int[]> datos = new HashMap<>();
            while (rs.next()) {
                datos.put(rs.getString("usuario"),
                          new int[]{rs.getInt("victorias"), rs.getInt("empates"), rs.getInt("derrotas")});
            }

            if (!datos.containsKey(j1) || !datos.containsKey(j2)) {
                cli.enviar("[ranking] Uno o ambos jugadores no tienen partidas registradas.");
                return;
            }

            int total1 = Arrays.stream(datos.get(j1)).sum();
            int total2 = Arrays.stream(datos.get(j2)).sum();
            if (total1 == 0 && total2 == 0) {
                cli.enviar("[ranking] No hay suficientes datos para comparar.");
                return;
            }

            double win1 = total1 > 0 ? (datos.get(j1)[0] * 100.0 / total1) : 0;
            double win2 = total2 > 0 ? (datos.get(j2)[0] * 100.0 / total2) : 0;

            cli.enviar("[ranking] Comparacion entre " + j1 + " y " + j2 + ":");
            cli.enviar(j1 + " -> " + String.format("%.1f", win1) + "% victorias");
            cli.enviar(j2 + " -> " + String.format("%.1f", win2) + "% victorias");
        } catch (Exception e) {
            cli.enviar("[ranking] Error al comparar jugadores.");
        }
    }

    // ------------------ GRUPOS ------------------

    public void inicializarGrupos() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS grupos (nombre TEXT PRIMARY KEY)");
            st.execute("CREATE TABLE IF NOT EXISTS grupo_miembros (grupo TEXT, usuario TEXT, ultimo_id INTEGER DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS mensajes (id INTEGER PRIMARY KEY AUTOINCREMENT, grupo TEXT, usuario TEXT, texto TEXT, fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            st.execute("INSERT OR IGNORE INTO grupos(nombre) VALUES ('Todos')");
        }
    }

    public boolean crearGrupo(String grupo) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO grupos(nombre) VALUES (?)")) {
            ps.setString(1, grupo);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean borrarGrupo(String grupo) {
        if (grupo.equalsIgnoreCase("Todos")) return false;
        try {
            PreparedStatement ps = conn.prepareStatement("DELETE FROM grupos WHERE nombre=?");
            ps.setString(1, grupo);
            ps.executeUpdate();
            ps.close();

            conn.prepareStatement("DELETE FROM grupo_miembros WHERE grupo='" + grupo + "'").executeUpdate();
            conn.prepareStatement("DELETE FROM mensajes WHERE grupo='" + grupo + "'").executeUpdate();

            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean unirseAGrupo(String grupo, String usuario) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO grupo_miembros(grupo,usuario) VALUES(?,?)")) {
            ps.setString(1, grupo);
            ps.setString(2, usuario);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean salirDeGrupo(String grupo, String usuario) {
        if (grupo.equalsIgnoreCase("Todos")) return false;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM grupo_miembros WHERE grupo=? AND usuario=?")) {
            ps.setString(1, grupo);
            ps.setString(2, usuario);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public List<String> gruposDeUsuario(String usuario) {
        List<String> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT grupo FROM grupo_miembros WHERE usuario=?")) {
            ps.setString(1, usuario);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) lista.add(rs.getString(1));
        } catch (SQLException ignore) {}
        return lista;
    }

    public void guardarMensaje(String grupo, String usuario, String texto) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO mensajes(grupo,usuario,texto) VALUES(?,?,?)")) {
            ps.setString(1, grupo);
            ps.setString(2, usuario);
            ps.setString(3, texto);
            ps.executeUpdate();
        } catch (SQLException ignore) {}
    }

    public List<String> mensajesNoLeidos(String grupo, String usuario) {
        List<String> mensajes = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT m.id, m.usuario, m.texto FROM mensajes m " +
            "JOIN grupo_miembros gm ON m.grupo=gm.grupo " +
            "WHERE gm.usuario=? AND m.grupo=? AND m.id > gm.ultimo_id ORDER BY m.id"
        )) {
            ps.setString(1, usuario);
            ps.setString(2, grupo);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                mensajes.add(rs.getString("usuario") + ": " + rs.getString("texto"));
            }

            if (!mensajes.isEmpty()) {
                try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE grupo_miembros SET ultimo_id=(SELECT MAX(id) FROM mensajes WHERE grupo=?) " +
                    "WHERE grupo=? AND usuario=?"
                )) {
                    upd.setString(1, grupo);
                    upd.setString(2, grupo);
                    upd.setString(3, usuario);
                    upd.executeUpdate();
                }
            }
        } catch (SQLException ignore) {}
        return mensajes;
    }
}
