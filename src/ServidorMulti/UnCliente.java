package ServidorMulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Set;

public class UnCliente implements Runnable {

    private static final String TAG_SYS = "[system] ";
    private static final String TAG_GAME = "[gato] ";
    private static final int LIMITE = 3;

    private final Socket socket;
    private final DataInputStream entrada;
    private final DataOutputStream salida;
    private final Database baseDatos;

    private String nombre;
    private boolean autenticado = false;
    private int mensajesEnviados = 0;

    public UnCliente(Socket socket, String nombreInicial) throws IOException {
        this.socket = socket;
        this.entrada = new DataInputStream(socket.getInputStream());
        this.salida = new DataOutputStream(socket.getOutputStream());
        this.baseDatos = ServidorMulti.db;
        this.nombre = nombreInicial;

        enviarSistema("Eres " + nombre + ". Solo puedes enviar " + LIMITE + " mensajes sin registrarte.");
        enviarSistema("Comandos: REGISTER <usuario> <pass>, LOGIN <usuario> <pass>, BLOCK <usuario>, UNBLOCK <usuario>, BLOCKS");
        enviarSistema("Juego: JUGAR <usuario>, ACEPTAR <usuario>, MOVER <1-9>, TABLERO, RENDIRSE, PARTIDAS, AYUDA");
    }

    @Override
    public void run() {
        try {
            while (true) {
                String mensaje = entrada.readUTF();
                if (mensaje == null) {
                    break;
                }
                String mayus = mensaje.toUpperCase();

                // ---- Comandos de usuario ----
                if (mayus.startsWith("REGISTER ")) {
                    registrar(mensaje);
                    continue;
                }
                if (mayus.startsWith("LOGIN ")) {
                    iniciarSesion(mensaje);
                    continue;
                }
                if (mayus.startsWith("BLOCK ")) {
                    bloquear(mensaje);
                    continue;
                }
                if (mayus.startsWith("UNBLOCK ")) {
                    desbloquear(mensaje);
                    continue;
                }
                if (mayus.equals("BLOCKS")) {
                    listarBloqueos();
                    continue;
                }

                // ---- Comandos del juego ----
                if (mayus.startsWith("JUGAR ")) {
                    jugar(mensaje);
                    continue;
                }
                if (mayus.startsWith("ACEPTAR ")) {
                    aceptar(mensaje);
                    continue;
                }
                if (mayus.startsWith("MOVER ")) {
                    mover(mensaje);
                    continue;
                }
                if (mayus.equals("TABLERO")) {
                    mostrarTableroActual();
                    continue;
                }
                if (mayus.equals("RENDIRSE")) {
                    rendirse();
                    continue;
                }
                if (mayus.equals("PARTIDAS")) {
                    listarJuegos();
                    continue;
                }
                if (mayus.equals("AYUDA")) {
                    enviarSistema("Comandos: JUGAR <usuario>, ACEPTAR <usuario>, MOVER <1-9>, TABLERO, RENDIRSE, PARTIDAS");
                    continue;
                }

                // ---- Limite de mensajes ----
                if (!autenticado && mensajesEnviados >= LIMITE) {
                    enviarSistema("Has alcanzado el limite de mensajes. Registrate o inicia sesion.");
                    continue;
                }

                mensajesEnviados++;
                for (UnCliente cliente : ServidorMulti.clientes.values()) {
                    if (cliente == this) {
                        continue;
                    }
                    if (ServidorMulti.db.estaBloqueado(cliente.nombre, this.nombre)) {
                        continue;
                    }
                    cliente.enviar(nombre + ": " + mensaje);
                }
            }
        } catch (IOException e) {
            System.err.println("Conexion cerrada: " + e.getMessage());
        } finally {
            manejarDesconexion();
        }
    }

    private void manejarDesconexion() {
        ServidorMulti.clientes.remove(this.nombre);
        for (Juego j : ServidorMulti.gestorJuegos.todos()) {
            if (this.nombre.equals(j.getJugadorA()) || this.nombre.equals(j.getJugadorB())) {
                j.rendirse(this.nombre);
                String otro = j.getOponente(this.nombre);
                UnCliente cli = ServidorMulti.clientes.get(otro);
                if (cli != null) {
                    cli.enviar(TAG_GAME + "Tu oponente " + this.nombre + " se desconecto. Ganaste por abandono.");
                    cli.enviar(TAG_GAME + j.mostrarTablero());
                }
                ServidorMulti.gestorJuegos.eliminar(j.getJugadorA(), j.getJugadorB());
            }
        }
        try {
            socket.close();
        } catch (IOException ignore) {
        }
    }

    // ------------------ Registro y login ------------------
    private void registrar(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 3) {
            enviarSistema("Uso: REGISTER <usuario> <pass>");
            return;
        }
        String user = p[1], pass = p[2];
        if (baseDatos.existeUsuario(user)) {
            enviarSistema("Ese usuario ya existe.");
            return;
        }
        if (baseDatos.registrar(user, pass)) {
            autenticado = true;
            cambiarNombre(user);
            enviarSistema("Registro exitoso. Puedes escribir sin limite.");
        } else {
            enviarSistema("Error al registrar usuario.");
        }
    }

    private void iniciarSesion(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 3) {
            enviarSistema("Uso: LOGIN <usuario> <pass>");
            return;
        }
        String user = p[1], pass = p[2];
        if (!baseDatos.existeUsuario(user)) {
            enviarSistema("Usuario no existe.");
            return;
        }
        if (baseDatos.login(user, pass)) {
            autenticado = true;
            cambiarNombre(user);
            enviarSistema("Inicio de sesion correcto.");
        } else {
            enviarSistema("Contrasena incorrecta.");
        }
    }

    // ------------------ Bloqueos ------------------
    private void bloquear(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) {
            enviarSistema("Uso: BLOCK <usuario>");
            return;
        }
        String objetivo = p[1];
        if (objetivo.equals(nombre)) {
            enviarSistema("No puedes bloquearte a ti mismo.");
            return;
        }
        if (!baseDatos.existeUsuario(objetivo)) {
            enviarSistema("Usuario no existe.");
            return;
        }
        if (baseDatos.bloquear(nombre, objetivo)) {
            enviarSistema("Has bloqueado a " + objetivo);
        } else {
            enviarSistema("Ese usuario ya estaba bloqueado.");
        }
    }

    private void desbloquear(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) {
            enviarSistema("Uso: UNBLOCK <usuario>");
            return;
        }
        String objetivo = p[1];
        if (baseDatos.desbloquear(nombre, objetivo)) {
            enviarSistema("Has desbloqueado a " + objetivo);
        } else {
            enviarSistema("Ese usuario no estaba bloqueado.");
        }
    }

    private void listarBloqueos() {
        Set<String> lista = baseDatos.obtenerBloqueados(nombre);
        if (lista.isEmpty()) {
            enviarSistema("No tienes usuarios bloqueados.");
        } else {
            enviarSistema("Usuarios bloqueados: " + String.join(", ", lista));
        }
    }

    // ------------------ Juego del gato ------------------
    private void jugar(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) {
            enviarSistema("Uso: JUGAR <usuario>");
            return;
        }
        String otro = p[1];
        if (otro.equals(nombre)) {
            enviarSistema("No puedes jugar contigo mismo.");
            return;
        }
        if (!ServidorMulti.db.existeUsuario(otro)) {
            enviarSistema("Usuario no existe.");
            return;
        }
        if (!ServidorMulti.clientes.containsKey(otro)) {
            enviarSistema("Usuario no conectado.");
            return;
        }
        if (ServidorMulti.gestorJuegos.existe(nombre, otro)) {
            enviarSistema("Ya existe una partida con ese usuario.");
            return;
        }

        ServidorMulti.gestorJuegos.crear(nombre, otro);
        UnCliente cli = ServidorMulti.clientes.get(otro);
        cli.enviar(TAG_GAME + nombre + " te ha invitado a jugar. Escribe: ACEPTAR " + nombre);
        enviar(TAG_GAME + "Invitacion enviada a " + otro);
    }

    private void aceptar(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) {
            enviarSistema("Uso: ACEPTAR <usuario>");
            return;
        }
        String otro = p[1];
        if (!ServidorMulti.gestorJuegos.existe(nombre, otro)) {
            enviarSistema("No hay invitacion pendiente con ese usuario.");
            return;
        }

        Juego j = ServidorMulti.gestorJuegos.obtener(nombre, otro);
        j.iniciar();
        UnCliente cli = ServidorMulti.clientes.get(otro);
        enviar(TAG_GAME + "Partida iniciada contra " + otro + ". Empieza: " + j.getTurno());
        cli.enviar(TAG_GAME + "Partida iniciada contra " + nombre + ". Empieza: " + j.getTurno());
        enviar(j.mostrarTablero());
        cli.enviar(j.mostrarTablero());
    }

    private void mover(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) {
            enviarSistema("Uso: MOVER <1-9>");
            return;
        }

        int pos;
        try {
            pos = Integer.parseInt(p[1]);
        } catch (NumberFormatException e) {
            enviarSistema("Posicion invalida.");
            return;
        }

        Juego j = obtenerPartidaActiva();
        if (j == null) {
            enviarSistema("No tienes partida activa.");
            return;
        }

        String resultado = j.realizarMovimiento(nombre, pos);
        UnCliente oponente = ServidorMulti.clientes.get(j.getOponente(nombre));

        enviar(TAG_GAME + resultado);
        if (oponente != null) {
            oponente.enviar(TAG_GAME + resultado);
        }

        if (j.estaTerminado()) {
            ServidorMulti.gestorJuegos.eliminar(j.getJugadorA(), j.getJugadorB());
        }
    }

    private void rendirse() {
        Juego j = obtenerPartidaActiva();
        if (j == null) {
            enviarSistema("No tienes partida activa.");
            return;
        }
        j.rendirse(nombre);
        UnCliente oponente = ServidorMulti.clientes.get(j.getOponente(nombre));
        if (oponente != null) {
            oponente.enviar(TAG_GAME + "Tu oponente se rindio. Ganaste.");
        }
        enviar(TAG_GAME + "Te rendiste.");
        ServidorMulti.gestorJuegos.eliminar(j.getJugadorA(), j.getJugadorB());
    }

    private void mostrarTableroActual() {
        Juego j = obtenerPartidaActiva();
        if (j == null) {
            enviarSistema("No tienes partida activa.");
            return;
        }
        enviar(TAG_GAME + j.mostrarTablero());
    }

    private Juego obtenerPartidaActiva() {
        for (Juego j : ServidorMulti.gestorJuegos.todos()) {
            if (nombre.equals(j.getJugadorA()) || nombre.equals(j.getJugadorB())) {
                return j;
            }
        }
        return null;
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
        if (sb.length() == 0) {
            enviar(TAG_GAME + "No tienes partidas activas.");
        } else {
            enviar(sb.toString());
        }
    }

    // ------------------ Utilidades ------------------
    private void cambiarNombre(String nuevo) {
        ServidorMulti.clientes.remove(this.nombre);
        this.nombre = nuevo;
        ServidorMulti.clientes.put(this.nombre, this);
    }

    void enviar(String msg) {
        try {
            salida.writeUTF(msg);
        } catch (IOException ignore) {
        }
    }

    void enviarSistema(String msg) {
        enviar(TAG_SYS + msg);
    }
}
