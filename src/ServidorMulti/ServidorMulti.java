package servidormulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class ServidorMulti {

    static ConcurrentHashMap<String, UnCliente> clientes = new ConcurrentHashMap<>();
    static int contadorUsuarios = 1;

    // Almacén de bloqueos (puede cambiarse a base de datos)
    static BlockStore blocks = new InMemoryBlockStore();

    public static void main(String[] args) {
        int puerto = 8080;

        try (ServerSocket servidorSocket = new ServerSocket(puerto)) {
            System.out.println("Servidor iniciado en el puerto " + puerto);

            while (true) {
                Socket socket = servidorSocket.accept();

                String nombreTemporal = "usuario" + contadorUsuarios++;
                UnCliente uncliente = new UnCliente(socket, nombreTemporal);
                Thread hilo = new Thread(uncliente, "cli-" + nombreTemporal);

                clientes.put(nombreTemporal, uncliente);
                hilo.start();

                System.out.println("Se conectó: " + nombreTemporal);
            }
        } catch (IOException e) {
            System.err.println("Error en servidor: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}
