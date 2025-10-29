package ServidorMulti;

import java.io.*;
import java.net.Socket;
import java.util.Set;

public class UnCliente implements Runnable {
    private static final String TAG_SYS = "[system] ";
    private static final String TAG_GAME = "[gato] ";
    private static final int LIMITE = 3;

    private final Socket socket;
    private final DataInputStream entrada;
    private final DataOutputStream salida;
    private final Database db;

    private String nombre;
    private boolean autenticado = false;
    private int mensajes = 0;

    public UnCliente(Socket s, String nombreInicial) throws IOException {
        this.socket = s;
        this.entrada = new DataInputStream(s.getInputStream());
        this.salida = new DataOutputStream(s.getOutputStream());
        this.db = ServidorMulti.db;
        this.nombre = nombreInicial;

        enviar(TAG_SYS + "Eres " + nombre + ". Solo puedes enviar " + LIMITE + " mensajes sin registrarte.");
        enviar(TAG_SYS + "Comandos: REGISTER <user> <pass>, LOGIN <user> <pass>, BLOCK <user>, UNBLOCK <user>, BLOCKS");
        enviar(TAG_SYS + "Juego: JUGAR <user>, ACEPTAR <user>, MOVER <1-9>, TABLERO, RENDIRSE, RANKING, RANK <a> <b>");
    }

    @Override
    public void run() {
        try {
            while (true) {
                String msg = entrada.readUTF();
                if (msg == null) break;
                procesar(msg.trim());
            }
        } catch (IOException e) {
            System.err.println("Conexion cerrada: " + e.getMessage());
        } finally {
            desconectar();
        }
    }

    private void procesar(String msg) {
        String m = msg.toUpperCase();

        if (m.startsWith("REGISTER ")) { registrar(msg); return; }
        if (m.startsWith("LOGIN ")) { login(msg); return; }
        if (m.startsWith("BLOCK ")) { bloquear(msg); return; }
        if (m.startsWith("UNBLOCK ")) { desbloquear(msg); return; }
        if (m.equals("BLOCKS")) { listarBloqueos(); return; }

        if (m.startsWith("JUGAR ")) { jugar(msg); return; }
        if (m.startsWith("ACEPTAR ")) { aceptar(msg); return; }
        if (m.startsWith("MOVER ")) { mover(msg); return; }
        if (m.equals("TABLERO")) { tablero(); return; }
        if (m.equals("RENDIRSE")) { rendirse(); return; }

        if (m.equals("RANKING")) { enviar(TAG_SYS + db.obtenerRankingGeneral()); return; }
        if (m.startsWith("RANK ")) { rankDos(msg); return; }

        SalaJuego sala = ServidorMulti.gestorJuegos.obtenerSala(nombre);
        if (sala != null) {
            UnCliente op = ServidorMulti.clientes.get(sala.getOponente(nombre));
            if (op != null) op.enviar("[privado][" + nombre + "]: " + msg);
            return;
        }

        if (!autenticado && mensajes >= LIMITE) {
            enviar(TAG_SYS + "Has alcanzado el limite. Registrate o inicia sesion.");
            return;
        }

        mensajes++;
        for (UnCliente c : ServidorMulti.clientes.values()) {
            if (c == this) continue;
            if (ServidorMulti.db.estaBloqueado(c.nombre, nombre)) continue;
            c.enviar(nombre + ": " + msg);
        }
    }

    private void registrar(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 3) { enviar(TAG_SYS + "Uso: REGISTER <user> <pass>"); return; }
        String u = p[1], pass = p[2];
        if (db.existeUsuario(u)) { enviar(TAG_SYS + "Ese usuario ya existe."); return; }
        if (db.registrar(u, pass)) { autenticado = true; cambiarNombre(u); enviar(TAG_SYS + "Registro ok."); }
        else enviar(TAG_SYS + "Error registrar.");
    }

    private void login(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 3) { enviar(TAG_SYS + "Uso: LOGIN <user> <pass>"); return; }
        String u = p[1], pass = p[2];
        if (!db.existeUsuario(u)) { enviar(TAG_SYS + "Usuario no existe."); return; }
        if (ServidorMulti.clientes.containsKey(u)) { enviar(TAG_SYS + "Usuario ya conectado."); return; }
        if (db.login(u, pass)) { autenticado = true; cambiarNombre(u); enviar(TAG_SYS + "Login ok."); }
        else enviar(TAG_SYS + "Password incorrecto.");
    }

    private void jugar(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) { enviar(TAG_SYS + "Uso: JUGAR <user>"); return; }
        String otro = p[1];
        if (otro.equals(nombre)) { enviar(TAG_SYS + "No puedes jugar contigo mismo."); return; }
        if (!db.existeUsuario(otro)) { enviar(TAG_SYS + "Usuario no existe."); return; }
        if (!ServidorMulti.clientes.containsKey(otro)) { enviar(TAG_SYS + "Usuario no conectado."); return; }
        if (ServidorMulti.gestorJuegos.existeSala(nombre, otro)) { enviar(TAG_SYS + "Ya hay partida con ese usuario."); return; }
        ServidorMulti.gestorJuegos.crearSala(nombre, otro);
        UnCliente cli = ServidorMulti.clientes.get(otro);
        cli.enviar(TAG_GAME + nombre + " te invito a jugar. Usa ACEPTAR " + nombre);
        enviar(TAG_GAME + "Invitacion enviada a " + otro);
    }

    private void aceptar(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) { enviar(TAG_SYS + "Uso: ACEPTAR <user>"); return; }
        String otro = p[1];
        SalaJuego s = ServidorMulti.gestorJuegos.obtenerSala(nombre);
        if (s == null || !s.contiene(otro)) { enviar(TAG_SYS + "No hay invitacion."); return; }
        Juego j = s.getJuego();
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
        SalaJuego s = ServidorMulti.gestorJuegos.obtenerSala(nombre);
        if (s == null) { enviar(TAG_SYS + "No tienes partida activa."); return; }
        Juego j = s.getJuego();
        String r = j.realizarMovimiento(nombre, pos);
        UnCliente op = ServidorMulti.clientes.get(s.getOponente(nombre));
        enviar(TAG_GAME + r);
        if (op != null) op.enviar(TAG_GAME + r);
        if (j.estaTerminado()) ServidorMulti.gestorJuegos.eliminarSala(nombre, s.getOponente(nombre));
    }

    private void tablero() {
        SalaJuego s = ServidorMulti.gestorJuegos.obtenerSala(nombre);
        if (s == null) { enviar(TAG_SYS + "No hay partida activa."); return; }
        enviar(TAG_GAME + s.getJuego().mostrarTablero());
    }

    private void rendirse() {
        SalaJuego s = ServidorMulti.gestorJuegos.obtenerSala(nombre);
        if (s == null) { enviar(TAG_SYS + "No tienes partida activa."); return; }
        s.getJuego().rendirse(nombre);
        UnCliente op = ServidorMulti.clientes.get(s.getOponente(nombre));
        if (op != null) op.enviar(TAG_GAME + "Tu oponente se rindio. Ganaste.");
        enviar(TAG_GAME + "Te rendiste.");
        ServidorMulti.gestorJuegos.eliminarSala(nombre, s.getOponente(nombre));
    }

    private void rankDos(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 3) { enviar(TAG_SYS + "Uso: RANK <user1> <user2>"); return; }
        enviar(TAG_SYS + db.compararHeadToHead(p[1], p[2]));
    }

    private void bloquear(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) { enviar(TAG_SYS + "Uso: BLOCK <user>"); return; }
        String obj = p[1];
        if (obj.equals(nombre)) { enviar(TAG_SYS + "No puedes bloquearte."); return; }
        if (!db.existeUsuario(obj)) { enviar(TAG_SYS + "Usuario no existe."); return; }
        if (db.bloquear(nombre, obj)) enviar(TAG_SYS + "Has bloqueado a " + obj);
        else enviar(TAG_SYS + "Ya estaba bloqueado.");
    }

    private void desbloquear(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) { enviar(TAG_SYS + "Uso: UNBLOCK <user>"); return; }
        String obj = p[1];
        if (db.desbloquear(nombre, obj)) enviar(TAG_SYS + "Has desbloqueado a " + obj);
        else enviar(TAG_SYS + "No estaba bloqueado.");
    }

    private void listarBloqueos() {
        Set<String> s = db.obtenerBloqueados(nombre);
        if (s.isEmpty()) enviar(TAG_SYS + "Sin bloqueos.");
        else enviar(TAG_SYS + "Bloqueados: " + String.join(", ", s));
    }

    private void cambiarNombre(String nuevo) {
        ServidorMulti.clientes.remove(this.nombre);
        this.nombre = nuevo;
        ServidorMulti.clientes.put(this.nombre, this);
    }

    private void desconectar() {
        ServidorMulti.clientes.remove(nombre);
        SalaJuego s = ServidorMulti.gestorJuegos.obtenerSala(nombre);
        if (s != null) {
            String op = s.getOponente(nombre);
            s.getJuego().rendirse(nombre);
            UnCliente cli = ServidorMulti.clientes.get(op);
            if (cli != null) cli.enviar(TAG_GAME + "Tu oponente se desconecto. Ganaste por abandono.");
            ServidorMulti.gestorJuegos.eliminarSala(nombre, op);
        }
        try { socket.close(); } catch (IOException ignore) {}
    }

    void enviar(String msg) {
        try { salida.writeUTF(msg); } catch (IOException ignore) {}
    }
}
