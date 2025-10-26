package ServidorMulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

public class ServidorMulti {

    // Lista de clientes conectados
    static final ConcurrentHashMap<String, UnCliente> clientes = new ConcurrentHashMap<>();

    // Gestor de partidas del gato
    static final GestorJuegos gestorJuegos = new GestorJuegos();

    // Base de datos SQLite
    static Database db;

    // Contador de nombres temporales
    private static int contador = 1;

    public static void main(String[] args) {
        int puerto = 8080;

        try {
            db = new Database("chat.db");
            System.out.println("Base de datos inicializada correctamente.");
        } catch (SQLException e) {
            System.err.println("Error al iniciar base de datos: " + e.getMessage());
            return;
        }

        try (ServerSocket servidor = new ServerSocket(puerto)) {
            System.out.println("Servidor iniciado en el puerto " + puerto);

            while (true) {
                Socket socket = servidor.accept();

                String nombreTemp = "usuario" + contador++;
                UnCliente nuevoCliente = new UnCliente(socket, nombreTemp);

                clientes.put(nombreTemp, nuevoCliente);
                new Thread(nuevoCliente).start();

                System.out.println("Cliente conectado: " + nombreTemp);
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }
}
