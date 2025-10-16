package servidormulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Set;

public class UnCliente implements Runnable {

    private static final String TAG_SYS = "[sistema] ";
    private static final String CMD_BLOCK = "BLOCK ";
    private static final String CMD_UNBLOCK = "UNBLOCK ";
    private static final String CMD_BLOCKS = "BLOCKS";
    private static final String PREFIX_PRIV = "@";

    private final Socket socket;
    final DataOutputStream salida;
    final DataInputStream entrada;

    private String nombre;
    private int mensajesEnviados = 0;
    private static final int LIMITE = 3;

    UnCliente(Socket s, String nombreInicial) throws IOException {
        this.socket = s;
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.nombre = nombreInicial;

        enviarSistema("Eres " + nombre + ". Solo puedes enviar " + LIMITE + " mensajes.");
        enviarSistema("Comandos: BLOCK <usuario>, UNBLOCK <usuario>, BLOCKS, privado: @usuario mensaje.");
    }

    @Override
    public void run() {
        try {
            while (true) {
                String mensaje = entrada.readUTF();
                if (mensaje == null) break;

                // Comandos
                if (mensaje.equalsIgnoreCase(CMD_BLOCKS)) {
                    mostrarBloqueados();
                    continue;
                }
                if (mensaje.toUpperCase().startsWith(CMD_BLOCK)) {
                    bloquearUsuario(mensaje.substring(CMD_BLOCK.length()).trim());
                    continue;
                }
                if (mensaje.toUpperCase().startsWith(CMD_UNBLOCK)) {
                    desbloquearUsuario(mensaje.substring(CMD_UNBLOCK.length()).trim());
                    continue;
                }

                // Límite de mensajes
                if (mensajesEnviados >= LIMITE) {
                    enviarSistema("Ya alcanzaste el límite de " + LIMITE + " mensajes. Solo puedes observar.");
                    continue;
                }

                // Privado
                if (mensaje.startsWith(PREFIX_PRIV)) {
                    String[] partes = mensaje.split("\\s+", 2);
                    if (partes.length < 2) {
                        enviarSistema("Formato: @usuario mensaje");
                        continue;
                    }
                    String aQuien = partes[0].substring(1);
                    String texto = partes[1];

                    UnCliente destino = ServidorMulti.clientes.get(aQuien);
                    if (destino == null) {
                        enviarSistema("No existe el usuario '" + aQuien + "'.");
                        continue;
                    }

                    if (ServidorMulti.blocks.isBlocked(aQuien, this.nombre)) {
                        enviarSistema("No puedes enviar a '" + aQuien + "': te tiene bloqueado.");
                        continue;
                    }

                    destino.enviar("[privado] " + nombre + ": " + texto);
                    mensajesEnviados++;
                    continue;
                }

                // Mensaje público (no lo ven quienes bloquearon al emisor)
                for (UnCliente c : ServidorMulti.clientes.values()) {
                    if (c == this) continue;
                    if (ServidorMulti.blocks.isBlocked(c.nombre, this.nombre)) continue;
                    c.enviar(nombre + ": " + mensaje);
                }
                mensajesEnviados++;
            }
        } catch (IOException ex) {
            System.err.println("Conexión cerrada para " + nombre + ": " + ex.getMessage());
        } finally {
            try { entrada.close(); } catch (IOException ignored) {}
            try { salida.close(); } catch (IOException ignored) {}
            try { socket.close(); } catch (IOException ignored) {}
            ServidorMulti.clientes.remove(this.nombre);
            broadcastSistema("El usuario '" + nombre + "' se ha desconectado.");
        }
    }

    // ---------------- BLOQUEAR / DESBLOQUEAR ----------------
    private void mostrarBloqueados() {
        Set<String> lista = ServidorMulti.blocks.blockedList(this.nombre);
        if (lista.isEmpty()) {
            enviarSistema("No tienes usuarios bloqueados.");
        } else {
            enviarSistema("Usuarios bloqueados: " + String.join(", ", lista));
        }
    }

    private void bloquearUsuario(String objetivo) {
        if (objetivo.isEmpty()) {
            enviarSistema("Uso: BLOCK <usuario>");
            return;
        }
        if (objetivo.equals(this.nombre)) {
            enviarSistema("No puedes bloquearte a ti mismo.");
            return;
        }
        if (!ServidorMulti.clientes.containsKey(objetivo)) {
            enviarSistema("El usuario '" + objetivo + "' no existe.");
            return;
        }
        boolean ok = ServidorMulti.blocks.block(this.nombre, objetivo);
        if (!ok) {
            enviarSistema("El usuario '" + objetivo + "' ya está bloqueado.");
        } else {
            enviarSistema("Has bloqueado a '" + objetivo + "'.");
        }
    }

    private void desbloquearUsuario(String objetivo) {
        if (objetivo.isEmpty()) {
            enviarSistema("Uso: UNBLOCK <usuario>");
            return;
        }
        boolean ok = ServidorMulti.blocks.unblock(this.nombre, objetivo);
        if (!ok) {
            enviarSistema("El usuario '" + objetivo + "' no estaba bloqueado.");
        } else {
            enviarSistema("Has desbloqueado a '" + objetivo + "'.");
        }
    }

    // ---------------- UTILIDADES ----------------
    void enviar(String msg) {
        try {
            salida.writeUTF(msg);
            salida.flush();
        } catch (IOException e) {
            System.err.println("No se pudo enviar a " + nombre + ": " + e.getMessage());
        }
    }

    void enviarSistema(String msg) {
        enviar(TAG_SYS + msg);
    }

    void broadcastSistema(String msg) {
        for (UnCliente c : ServidorMulti.clientes.values()) {
            c.enviar(TAG_SYS + msg);
        }
    }
}
