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

    // ================== RANKING ==================

    private void inicializarRanking() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(
                "CREATE TABLE IF NOT EXISTS ranking (" +
                " usuario TEXT PRIMARY KEY," +
                " puntos INTEGER DEFAULT 0," +
                " victorias INTEGER DEFAULT 0," +
                " empates INTEGER DEFAULT 0," +
                " derrotas INTEGER DEFAULT 0)"
            );

            st.execute(
                "CREATE TABLE IF NOT EXISTS duelos (" +
                " j1 TEXT," +
                " j2 TEXT," +
                " jug1_gana INTEGER DEFAULT 0," +
                " jug2_gana INTEGER DEFAULT 0," +
                " empates INTEGER DEFAULT 0," +
                " PRIMARY KEY(j1, j2))"
            );
        }
    }

    public void actualizarResultadoVictoria(String ganador, String perdedor) {
        try {
            PreparedStatement ps1 = conn.prepareStatement(
                "INSERT INTO ranking(usuario, puntos, victorias, empates, derrotas) " +
                "VALUES(?,2,1,0,0) " +
                "ON CONFLICT(usuario) DO UPDATE SET " +
                " puntos = puntos + 2, victorias = victorias + 1"
            );
            ps1.setString(1, ganador);
            ps1.executeUpdate();
            ps1.close();

            PreparedStatement ps2 = conn.prepareStatement(
                "INSERT INTO ranking(usuario, puntos, victorias, empates, derrotas) " +
                "VALUES(?,0,0,0,1) " +
                "ON CONFLICT(usuario) DO UPDATE SET " +
                " derrotas = derrotas + 1"
            );
            ps2.setString(1, perdedor);
            ps2.executeUpdate();
            ps2.close();

            String a = ganador;
            String b = perdedor;
            boolean ganadorEsA = true;
            if (a.compareTo(b) > 0) {
                String tmp = a;
                a = b;
                b = tmp;
                ganadorEsA = false;
            }

            PreparedStatement ins = conn.prepareStatement(
                "INSERT OR IGNORE INTO duelos(j1, j2) VALUES(?, ?)"
            );
            ins.setString(1, a);
            ins.setString(2, b);
            ins.executeUpdate();
            ins.close();

            if (ganadorEsA) {
                PreparedStatement upd = conn.prepareStatement(
                    "UPDATE duelos SET jug1_gana = jug1_gana + 1 WHERE j1 = ? AND j2 = ?"
                );
                upd.setString(1, a);
                upd.setString(2, b);
                upd.executeUpdate();
                upd.close();
            } else {
                PreparedStatement upd = conn.prepareStatement(
                    "UPDATE duelos SET jug2_gana = jug2_gana + 1 WHERE j1 = ? AND j2 = ?"
                );
                upd.setString(1, a);
                upd.setString(2, b);
                upd.executeUpdate();
                upd.close();
            }
        } catch (SQLException e) {
            System.err.println("Error al actualizar victoria: " + e.getMessage());
        }
    }

    public void actualizarResultadoEmpate(String jugadorA, String jugadorB) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ranking(usuario, puntos, victorias, empates, derrotas) " +
                "VALUES(?,1,0,1,0) " +
                "ON CONFLICT(usuario) DO UPDATE SET " +
                " puntos = puntos + 1, empates = empates + 1"
            );
            ps.setString(1, jugadorA);
            ps.executeUpdate();
            ps.setString(1, jugadorB);
            ps.executeUpdate();
            ps.close();

            String a = jugadorA;
            String b = jugadorB;
            if (a.compareTo(b) > 0) {
                String tmp = a;
                a = b;
                b = tmp;
            }

            PreparedStatement ins = conn.prepareStatement(
                "INSERT OR IGNORE INTO duelos(j1, j2) VALUES(?, ?)"
            );
            ins.setString(1, a);
            ins.setString(2, b);
            ins.executeUpdate();
            ins.close();

            PreparedStatement upd = conn.prepareStatement(
                "UPDATE duelos SET empates = empates + 1 WHERE j1 = ? AND j2 = ?"
            );
            upd.setString(1, a);
            upd.setString(2, b);
            upd.executeUpdate();
            upd.close();
        } catch (SQLException e) {
            System.err.println("Error al actualizar empate: " + e.getMessage());
        }
    }

    public void mostrarRankingGeneral(UnCliente cli) {
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT usuario, puntos, victorias, empates, derrotas " +
                 "FROM ranking " +
                 "ORDER BY puntos DESC, victorias DESC, usuario ASC"
             );
             ResultSet rs = ps.executeQuery()) {

            StringBuilder sb = new StringBuilder();
            sb.append("[ranking] jugador  puntos  V/E/D\n");
            while (rs.next()) {
                String usuario = rs.getString("usuario");
                int puntos = rs.getInt("puntos");
                int v = rs.getInt("victorias");
                int e = rs.getInt("empates");
                int d = rs.getInt("derrotas");
                sb.append(String.format(" - %s: %d pts (%d/%d/%d)\n", usuario, puntos, v, e, d));
            }
            if (sb.length() == 0) {
                cli.enviar("[ranking] No hay datos aun.");
            } else {
                cli.enviar(sb.toString());
            }
        } catch (SQLException e) {
            cli.enviar("[ranking] Error al leer ranking.");
        }
    }

    public void mostrarComparacion(UnCliente cli, String j1, String j2) {
        String a = j1;
        String b = j2;
        boolean invertido = false;
        if (a.compareTo(b) > 0) {
            String tmp = a;
            a = b;
            b = tmp;
            invertido = true;
        }

        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT jug1_gana, jug2_gana, empates FROM duelos WHERE j1 = ? AND j2 = ?"
             )) {
            ps.setString(1, a);
            ps.setString(2, b);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                cli.enviar("[ranking] No hay partidas registradas entre " + j1 + " y " + j2 + ".");
                return;
            }
            int g1 = rs.getInt("jug1_gana");
            int g2 = rs.getInt("jug2_gana");
            int emp = rs.getInt("empates");
            int total = g1 + g2 + emp;
            if (total == 0) {
                cli.enviar("[ranking] No hay partidas registradas entre " + j1 + " y " + j2 + ".");
                return;
            }

            double p1 = (invertido ? g2 : g1) * 100.0 / total;
            double p2 = (invertido ? g1 : g2) * 100.0 / total;

            String res = "[ranking] Entre " + j1 + " y " + j2 + ":\n" +
                         "  Total partidas: " + total + "\n" +
                         "  " + j1 + " gana: " + String.format("%.1f", p1) + " %\n" +
                         "  " + j2 + " gana: " + String.format("%.1f", p2) + " %\n" +
                         "  Empates: " + emp;
            cli.enviar(res);
        } catch (SQLException e) {
            cli.enviar("[ranking] Error al comparar jugadores.");
        }
    }

    // ================== GRUPOS ==================

    private void inicializarGrupos() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS grupos (nombre TEXT PRIMARY KEY)");
            st.execute("CREATE TABLE IF NOT EXISTS grupo_miembros (" +
                       " grupo TEXT," +
                       " usuario TEXT," +
                       " ultimo_id INTEGER DEFAULT 0)");
            st.execute(
                "CREATE TABLE IF NOT EXISTS mensajes (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " grupo TEXT," +
                " usuario TEXT," +
                " texto TEXT," +
                " fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            st.execute("INSERT OR IGNORE INTO grupos(nombre) VALUES ('Todos')");
        }
    }

    public boolean crearGrupo(String grupo) {
        try (PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO grupos(nombre) VALUES (?)"
             )) {
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
            PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM grupos WHERE nombre = ?"
            );
            ps.setString(1, grupo);
            ps.executeUpdate();
            ps.close();

            conn.prepareStatement(
                "DELETE FROM grupo_miembros WHERE grupo = '" + grupo + "'"
            ).executeUpdate();
            conn.prepareStatement(
                "DELETE FROM mensajes WHERE grupo = '" + grupo + "'"
            ).executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean unirseAGrupo(String grupo, String usuario) {
        try (PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR IGNORE INTO grupo_miembros(grupo, usuario) VALUES(?, ?)"
             )) {
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
        try (PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM grupo_miembros WHERE grupo = ? AND usuario = ?"
             )) {
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
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT grupo FROM grupo_miembros WHERE usuario = ?"
             )) {
            ps.setString(1, usuario);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                lista.add(rs.getString(1));
            }
        } catch (SQLException ignore) {
        }
        return lista;
    }

    public void guardarMensaje(String grupo, String usuario, String texto) {
        try (PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO mensajes(grupo, usuario, texto) VALUES(?, ?, ?)"
             )) {
            ps.setString(1, grupo);
            ps.setString(2, usuario);
            ps.setString(3, texto);
            ps.executeUpdate();
        } catch (SQLException ignore) {
        }
    }

    public List<String> mensajesNoLeidos(String grupo, String usuario) {
        List<String> mensajes = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT m.id, m.usuario, m.texto " +
                 "FROM mensajes m " +
                 "JOIN grupo_miembros gm ON m.grupo = gm.grupo " +
                 "WHERE gm.usuario = ? AND m.grupo = ? AND m.id > gm.ultimo_id " +
                 "ORDER BY m.id"
             )) {
            ps.setString(1, usuario);
            ps.setString(2, grupo);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                mensajes.add(rs.getString("usuario") + ": " + rs.getString("texto"));
            }

            if (!mensajes.isEmpty()) {
                try (PreparedStatement upd = conn.prepareStatement(
                         "UPDATE grupo_miembros " +
                         "SET ultimo_id = (SELECT MAX(id) FROM mensajes WHERE grupo = ?) " +
                         "WHERE grupo = ? AND usuario = ?"
                     )) {
                    upd.setString(1, grupo);
                    upd.setString(2, grupo);
                    upd.setString(3, usuario);
                    upd.executeUpdate();
                }
            }
        } catch (SQLException ignore) {
        }
        return mensajes;
    }
}
