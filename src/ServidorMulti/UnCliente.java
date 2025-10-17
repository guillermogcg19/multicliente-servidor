package servidormulti;

import java.io.*;
import java.net.Socket;
import java.util.Set;

public class UnCliente implements Runnable {

    private static final String TAG_SYS = "[sistema] ";
    private static final int LIMITE = 3;
    private final Socket socket;
    private final DataInputStream entrada;
    private final DataOutputStream salida;
    private final Database db;

    private String nombre;
    private boolean autenticado = false;
    private int mensajesEnviados = 0;

    UnCliente(Socket s, String nombreInicial, Database db) throws IOException {
        this.socket = s;
        this.entrada = new DataInputStream(s.getInputStream());
        this.salida = new DataOutputStream(s.getOutputStream());
        this.db = db;
        this.nombre = nombreInicial;

        enviarSistema("Eres " + nombre + ". Solo puedes enviar " + LIMITE + " mensajes sin registrarte.");
        enviarSistema("Comandos: REGISTER <usuario> <pass>, LOGIN <usuario> <pass>, BLOCK <usuario>, UNBLOCK <usuario>, BLOCKS");
    }

    @Override
    public void run() {
        try {
            while (true) {
                String msg = entrada.readUTF();
                if (msg == null) break;

                if (msg.toUpperCase().startsWith("REGISTER ")) { registrar(msg); continue; }
                if (msg.toUpperCase().startsWith("LOGIN ")) { login(msg); continue; }
                if (msg.toUpperCase().startsWith("BLOCK ")) { bloquear(msg); continue; }
                if (msg.toUpperCase().startsWith("UNBLOCK ")) { desbloquear(msg); continue; }
                if (msg.equalsIgnoreCase("BLOCKS")) { listarBloqueos(); continue; }

                if (!autenticado && mensajesEnviados >= LIMITE) {
                    enviarSistema("Has alcanzado el límite de mensajes. Regístrate o inicia sesión.");
                    continue;
                }

                mensajesEnviados++;
                for (UnCliente c : ServidorMulti.clientes.values()) {
                    if (c == this) continue;
                    if (ServidorMulti.db.estaBloqueado(c.nombre, this.nombre)) continue;
                    c.enviar(nombre + ": " + msg);
                }
            }
        } catch (IOException e) {
            System.err.println("Conexión cerrada: " + e.getMessage());
        } finally {
            ServidorMulti.clientes.remove(this.nombre);
            try { socket.close(); } catch (IOException ignore) {}
        }
    }

    // --- Métodos de base de datos ---
    private void registrar(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 3) { enviarSistema("Uso: REGISTER <usuario> <pass>"); return; }

        String user = p[1], pass = p[2];
        if (db.usuarioExiste(user)) { enviarSistema("Ese usuario ya existe."); return; }

        if (db.registrar(user, pass)) {
            autenticado = true;
            cambiarNombre(user);
            enviarSistema("Registro exitoso. Ahora puedes escribir sin límite.");
        } else {
            enviarSistema("Error al registrar usuario.");
        }
    }

    private void login(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 3) { enviarSistema("Uso: LOGIN <usuario> <pass>"); return; }

        String user = p[1], pass = p[2];
        if (!db.usuarioExiste(user)) { enviarSistema("El usuario no existe."); return; }

        if (db.verificarLogin(user, pass)) {
            autenticado = true;
            cambiarNombre(user);
            enviarSistema("Inicio de sesión correcto.");
        } else {
            enviarSistema("Contraseña incorrecta.");
        }
    }

    private void bloquear(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) { enviarSistema("Uso: BLOCK <usuario>"); return; }

        String objetivo = p[1];
        if (objetivo.equals(nombre)) { enviarSistema("No puedes bloquearte a ti mismo."); return; }
        if (!db.usuarioExiste(objetivo)) { enviarSistema("El usuario no existe."); return; }

        if (db.bloquear(nombre, objetivo))
            enviarSistema("Has bloqueado a '" + objetivo + "'.");
        else
            enviarSistema("Ya lo tenías bloqueado.");
    }

    private void desbloquear(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) { enviarSistema("Uso: UNBLOCK <usuario>"); return; }

        String objetivo = p[1];
        if (db.desbloquear(nombre, objetivo))
            enviarSistema("Has desbloqueado a '" + objetivo + "'.");
        else
            enviarSistema("Ese usuario no estaba bloqueado.");
    }

    private void listarBloqueos() {
        Set<String> lista = db.listaBloqueados(nombre);
        if (lista.isEmpty())
            enviarSistema("No tienes usuarios bloqueados.");
        else
            enviarSistema("Usuarios bloqueados: " + String.join(", ", lista));
    }

    private void cambiarNombre(String nuevo) {
        ServidorMulti.clientes.remove(this.nombre);
        this.nombre = nuevo;
        ServidorMulti.clientes.put(this.nombre, this);
    }

    // --- Utilidades ---
    void enviar(String msg) {
        try { salida.writeUTF(msg); } catch (IOException ignore) {}
    }

    void enviarSistema(String msg) {
        enviar(TAG_SYS + msg);
    }
}
