package ServidorMulti;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Set;

public class UnCliente implements Runnable {
    private static final String TAG_SYS = "[sistema] ";
    private static final String TAG_GAME = "[gato] ";
    private static final int LIMITE = 3;

    private final Socket socket;
    private final DataInputStream entrada;
    private final DataOutputStream salida;
    private final Database db;

    private String nombre;
    private boolean autenticado = false;
    private int mensajes = 0;
    private String grupoActual = "Todos";

    public UnCliente(Socket s, String nombreInicial) throws IOException {
        this.socket = s;
        this.entrada = new DataInputStream(s.getInputStream());
        this.salida = new DataOutputStream(s.getOutputStream());
        this.db = ServidorMulti.db;
        this.nombre = nombreInicial;
        db.unirseAGrupo("Todos", nombre);

        enviar(TAG_SYS + "Eres " + nombre + ". Solo puedes enviar " + LIMITE + " mensajes sin registrarte.");
        enviar(TAG_SYS + "Comandos disponibles:");
        enviar("REGISTER <usuario> <pass>");
        enviar("LOGIN <usuario> <pass>");
        enviar("CREARGRUPO <nombre>");
        enviar("ENTRAR <nombre>");
        enviar("SALIRGRUPO");
        enviar("GRUPOS");
        enviar("JUGAR <usuario>");
        enviar("ACEPTAR <usuario>");
        enviar("MOVER <1-9>");
        enviar("TABLERO");
        enviar("RENDIRSE");
        enviar("PARTIDAS");
        enviar("RANKING o RANKING <jugador1> <jugador2>");
    }

    @Override
    public void run() {
        try {
            while (true) {
                String msg = entrada.readUTF();
                if (msg == null) break;
                procesarMensaje(msg.trim());
            }
        } catch (IOException e) {
            System.err.println("Conexion cerrada: " + e.getMessage());
        } finally {
            desconectar();
        }
    }

    private void procesarMensaje(String msg) {
        String m = msg.toUpperCase();

        if (m.startsWith("REGISTER ")) { registrar(msg); return; }
        if (m.startsWith("LOGIN ")) { login(msg); return; }
        if (m.startsWith("CREARGRUPO ")) { crearGrupo(msg); return; }
        if (m.startsWith("ENTRAR ")) { entrarGrupo(msg); return; }
        if (m.equals("SALIRGRUPO")) { salirGrupo(); return; }
        if (m.equals("GRUPOS")) { listarGrupos(); return; }

        if (m.startsWith("JUGAR ")) { jugar(msg); return; }
        if (m.startsWith("ACEPTAR ")) { aceptar(msg); return; }
        if (m.startsWith("MOVER ")) { mover(msg); return; }
        if (m.equals("TABLERO")) { mostrarTablero(); return; }
        if (m.equals("RENDIRSE")) { rendirse(); return; }
        if (m.equals("PARTIDAS")) { listarJuegos(); return; }
        if (m.startsWith("RANKING")) { mostrarRanking(msg); return; }

        if (!autenticado && mensajes >= LIMITE) {
            enviar(TAG_SYS + "Has alcanzado el limite de mensajes. Registrate o inicia sesion.");
            return;
        }

        mensajes++;
        db.guardarMensaje(grupoActual, nombre, msg);
        for (UnCliente cli : ServidorMulti.clientes.values()) {
            if (cli != this && cli.grupoActual.equalsIgnoreCase(grupoActual)) {
                cli.enviar("[" + grupoActual + "] " + nombre + ": " + msg);
            }
        }
    }

    // ---------------- REGISTRO Y LOGIN ----------------
    private void registrar(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 3) { enviar(TAG_SYS + "Uso: REGISTER <usuario> <pass>"); return; }
        autenticado = true;
        cambiarNombre(p[1]);
        db.unirseAGrupo("Todos", nombre);
        enviar(TAG_SYS + "Registro exitoso.");
    }

    private void login(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 3) { enviar(TAG_SYS + "Uso: LOGIN <usuario> <pass>"); return; }
        if (ServidorMulti.clientes.containsKey(p[1])) {
            enviar(TAG_SYS + "Ese usuario ya esta conectado.");
            return;
        }
        autenticado = true;
        cambiarNombre(p[1]);
        db.unirseAGrupo("Todos", nombre);
        enviar(TAG_SYS + "Inicio de sesion correcto.");
    }

    // ---------------- GESTION DE GRUPOS ----------------
    private void crearGrupo(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) { enviar(TAG_SYS + "Uso: CREARGRUPO <nombre>"); return; }
        if (db.crearGrupo(p[1])) enviar(TAG_SYS + "Grupo creado: " + p[1]);
        else enviar(TAG_SYS + "No se pudo crear el grupo.");
    }

    private void entrarGrupo(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) { enviar(TAG_SYS + "Uso: ENTRAR <nombre>"); return; }
        grupoActual = p[1];
        db.unirseAGrupo(grupoActual, nombre);
        List<String> nuevos = db.mensajesNoLeidos(grupoActual, nombre);
        enviar(TAG_SYS + "Entraste a " + grupoActual + ". Mensajes no vistos:");
        for (String m : nuevos) enviar(m);
    }

    private void salirGrupo() {
        if (grupoActual.equalsIgnoreCase("Todos")) {
            enviar(TAG_SYS + "No puedes salir del grupo Todos.");
            return;
        }
        if (db.salirDeGrupo(grupoActual, nombre)) {
            enviar(TAG_SYS + "Saliste de " + grupoActual + ". Ahora estas en Todos.");
            grupoActual = "Todos";
        } else enviar(TAG_SYS + "No se pudo salir del grupo.");
    }

    private void listarGrupos() {
        List<String> grupos = db.gruposDeUsuario(nombre);
        if (grupos.isEmpty()) enviar(TAG_SYS + "No perteneces a ningun grupo.");
        else enviar(TAG_SYS + "Grupos: " + String.join(", ", grupos));
    }

    // ---------------- JUEGO DEL GATO ----------------
    private void jugar(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) { enviar(TAG_SYS + "Uso: JUGAR <usuario>"); return; }
        String otro = p[1];
        if (otro.equals(nombre)) { enviar(TAG_SYS + "No puedes jugar contigo mismo."); return; }
        if (!ServidorMulti.clientes.containsKey(otro)) { enviar(TAG_SYS + "Usuario no conectado."); return; }
        if (ServidorMulti.gestorJuegos.existe(nombre, otro)) { enviar(TAG_SYS + "Ya hay una partida con ese usuario."); return; }

        ServidorMulti.gestorJuegos.crear(nombre, otro);
        UnCliente cli = ServidorMulti.clientes.get(otro);
        cli.enviar(TAG_GAME + nombre + " te invito a jugar. Escribe: ACEPTAR " + nombre);
        enviar(TAG_GAME + "Invitacion enviada a " + otro);
    }

    private void aceptar(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) { enviar(TAG_SYS + "Uso: ACEPTAR <usuario>"); return; }
        String otro = p[1];
        if (!ServidorMulti.gestorJuegos.existe(nombre, otro)) { enviar(TAG_SYS + "No hay invitacion."); return; }

        Juego j = ServidorMulti.gestorJuegos.obtener(nombre, otro);
        UnCliente cli = ServidorMulti.clientes.get(otro);
        enviar(TAG_GAME + "Partida iniciada. Empieza: " + j.getTurno());
        cli.enviar(TAG_GAME + "Partida iniciada. Empieza: " + j.getTurno());
        enviar(j.mostrarTablero());
        cli.enviar(j.mostrarTablero());
    }

    private void mover(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) { enviar(TAG_SYS + "Uso: MOVER <1-9>"); return; }
        int pos;
        try { pos = Integer.parseInt(p[1]); } catch (NumberFormatException e) { enviar(TAG_SYS + "Posicion invalida."); return; }
        Juego j = obtenerPartidaActiva();
        if (j == null) { enviar(TAG_SYS + "No tienes partida activa."); return; }

        String res = j.realizarMovimiento(nombre, pos);
        UnCliente cli = ServidorMulti.clientes.get(j.getOponente(nombre));
        enviar(TAG_GAME + res);
        if (cli != null) cli.enviar(TAG_GAME + res);

        if (j.estaTerminado()) ServidorMulti.gestorJuegos.eliminar(j.getJugadorA(), j.getJugadorB());
    }

    private Juego obtenerPartidaActiva() {
        for (Juego j : ServidorMulti.gestorJuegos.todos()) {
            if (nombre.equals(j.getJugadorA()) || nombre.equals(j.getJugadorB())) return j;
        }
        return null;
    }

    private void mostrarTablero() {
        Juego j = obtenerPartidaActiva();
        if (j == null) { enviar(TAG_SYS + "No hay partida activa."); return; }
        enviar(TAG_GAME + j.mostrarTablero());
    }

    private void rendirse() {
        Juego j = obtenerPartidaActiva();
        if (j == null) { enviar(TAG_SYS + "No tienes partida activa."); return; }
        j.rendirse(nombre);
        UnCliente cli = ServidorMulti.clientes.get(j.getOponente(nombre));
        if (cli != null) cli.enviar(TAG_GAME + "Tu oponente se rindio. Ganaste.");
        enviar(TAG_GAME + "Te rendiste.");
        ServidorMulti.gestorJuegos.eliminar(j.getJugadorA(), j.getJugadorB());
    }

    private void listarJuegos() {
        StringBuilder sb = new StringBuilder();
        for (Juego j : ServidorMulti.gestorJuegos.todos()) {
            if (j.getJugadorA().equals(nombre) || j.getJugadorB().equals(nombre)) {
                sb.append("Contra ").append(j.getOponente(nombre))
                        .append(" - Turno: ").append(j.getTurno())
                        .append(j.estaTerminado() ? " (Finalizada)\n" : " (En curso)\n");
            }
        }
        if (sb.length() == 0) enviar(TAG_GAME + "No tienes partidas activas.");
        else enviar(sb.toString());
    }

    // ---------------- RANKING ----------------
    private void mostrarRanking(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length == 1) {
            ServidorMulti.db.mostrarRankingGeneral(this);
        } else if (p.length == 3) {
            ServidorMulti.db.mostrarComparacion(this, p[1], p[2]);
        } else {
            enviar(TAG_SYS + "Uso: RANKING o RANKING <jugador1> <jugador2>");
        }
    }

    // ---------------- UTILIDADES ----------------
    private void cambiarNombre(String nuevo) {
        ServidorMulti.clientes.remove(nombre);
        nombre = nuevo;
        ServidorMulti.clientes.put(nombre, this);
    }

    private void desconectar() {
        ServidorMulti.clientes.remove(nombre);
        try { socket.close(); } catch (IOException ignore) {}
    }

    void enviar(String msg) {
        try { salida.writeUTF(msg); } catch (IOException ignore) {}
    }
}
