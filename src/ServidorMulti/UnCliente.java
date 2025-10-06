package servidormulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class UnCliente implements Runnable {
    private final Socket socket;
    final DataOutputStream salida;
    final DataInputStream entrada;


    private String nombre;

    UnCliente(Socket s, String nombreInicial) throws IOException {
        this.socket = s;
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.nombre = nombreInicial;
        enviarSistema("Bienvenido. Tu nombre temporal es '" + nombre + "'. Envía 'NICK <nuevoNombre>' para cambiarlo.");
    }

    @Override
    public void run() {
        try {
            while (true) {
                String mensaje = entrada.readUTF();
                if (mensaje == null) break;


                if (mensaje.startsWith("NICK ")) {
                    String nuevo = mensaje.substring(5).trim();
                    if (nuevo.isEmpty()) {
                        enviarSistema("Nombre no válido.");
                        continue;
                    }
                    if (ServidorMulti.clientes.containsKey(nuevo)) {
                        enviarSistema("Ese nombre ya está en uso. Elige otro.");
                        continue;
                    }
                    // actualizar registro en el mapa
                    ServidorMulti.clientes.remove(this.nombre);
                    String anterior = this.nombre;
                    this.nombre = nuevo;
                    ServidorMulti.clientes.put(this.nombre, this);
                    enviarSistema("Nombre actualizado a '" + this.nombre + "'.");
                    broadcastSistema("El usuario '" + anterior + "' ahora es '" + this.nombre + "'.");
                    continue;
                }

                // Mensaje privado: @dest texto
                if (mensaje.startsWith("@")) {
                    String[] partes = mensaje.split("\\s+", 2);
                    if (partes.length < 2) {
                        enviarSistema("Formato: @destinatario mensaje");
                        continue;
                    }
                    String aQuien = partes[0].substring(1);
                    String texto = partes[1];

                    UnCliente destino = ServidorMulti.clientes.get(aQuien);
                    if (destino == null) {
                        enviarSistema("No existe el usuario '" + aQuien + "'.");
                        continue;
                    }
                    destino.enviar("[privado] " + nombre + ": " + texto);
                  
                    if (destino != this) {
                        enviar("[privado a " + aQuien + "] " + nombre + ": " + texto);
                    }
                    continue;
                }

           
                broadcast(nombre + ": " + mensaje);
            }
        } catch (IOException ex) {
  
        } finally {
           
            try { entrada.close(); } catch (IOException ignored) {}
            try { salida.close(); } catch (IOException ignored) {}
            try { socket.close(); } catch (IOException ignored) {}
            ServidorMulti.clientes.remove(this.nombre);
            broadcastSistema("El usuario '" + nombre + "' se ha desconectado.");
        }
    }


    void enviar(String msg) {
        try { salida.writeUTF(msg); salida.flush(); } catch (IOException ignored) {}
    }

    void enviarSistema(String msg) {
        enviar("[sistema] " + msg);
    }

    void broadcast(String msg) {
        for (UnCliente c : ServidorMulti.clientes.values()) {
            c.enviar(msg);
        }
    }

    void broadcastSistema(String msg) {
        broadcast("[sistema] " + msg);
    }
}
