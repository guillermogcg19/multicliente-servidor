package servidormulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class ServidorMulti {


    static final ConcurrentHashMap<String, UnCliente> clientes = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        int puerto = 8080;
        int contador = 1;

        try (ServerSocket servidorSocket = new ServerSocket(puerto)) {
            System.out.println("Servidor iniciado en el puerto " + puerto);

            while (true) {
                Socket socket = servidorSocket.accept();

                String nombreTemporal = "user" + contador++;
                UnCliente uncliente = new UnCliente(socket, nombreTemporal);


                clientes.put(nombreTemporal, uncliente);

                Thread hilo = new Thread(uncliente, "cli-" + nombreTemporal);
                hilo.start();

                System.out.println("Se conectó: " + nombreTemporal);
            }
        } catch (IOException e) {
            System.out.println("Error en servidor: " + e.getMessage());
        }
    }
}
