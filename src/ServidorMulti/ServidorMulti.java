package ServidorMulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

public class ServidorMulti {
    static ConcurrentHashMap<String, UnCliente> clientes = new ConcurrentHashMap<>();
    static GestorJuegos gestorJuegos = new GestorJuegos();
    static GestorGrupos gestorGrupos = new GestorGrupos();
    static Database db;
    static int contador = 1;

    public static void main(String[] args) {
        int puerto = 8080;
        try {
            db = new Database("chat.db");
        } catch (SQLException e) {
            System.err.println("Error al iniciar base de datos: " + e.getMessage());
            return;
        }

        try (ServerSocket servidor = new ServerSocket(puerto)) {
            System.out.println("Servidor iniciado en puerto " + puerto);
            while (true) {
                Socket s = servidor.accept();
                String nombre = "usuario" + contador++;
                UnCliente cli = new UnCliente(s, nombre);
                clientes.put(nombre, cli);
                gestorGrupos.unirA("Todos", nombre);
                new Thread(cli).start();
                System.out.println("Conectado: " + nombre);
            }
        } catch (IOException e) {
            System.err.println("Error en servidor: " + e.getMessage());
        }
    }
}
