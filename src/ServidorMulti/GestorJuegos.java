package ServidorMulti;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class GestorJuegos {
    private final ConcurrentHashMap<String, Juego> juegos = new ConcurrentHashMap<>();

    private String clave(String a, String b) {
        return (a.compareTo(b) < 0) ? a + "|" + b : b + "|" + a;
    }

    public synchronized void crear(String a, String b) {
        juegos.put(clave(a, b), new Juego(a, b));
    }

    public synchronized boolean existe(String a, String b) {
        return juegos.containsKey(clave(a, b));
    }

    public synchronized Juego obtener(String a, String b) {
        return juegos.get(clave(a, b));
    }

    public synchronized void eliminar(String a, String b) {
        juegos.remove(clave(a, b));
    }

    public synchronized List<Juego> todos() {
        return new ArrayList<>(juegos.values());
    }
}
