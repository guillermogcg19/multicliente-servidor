package ServidorMulti;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GestorJuegos {
    private final Map<String, SalaJuego> salas = new ConcurrentHashMap<>();

    public synchronized void crearSala(String a, String b) {
        if (existeSala(a, b)) return;
        Juego j = new Juego(a, b);
        SalaJuego s = new SalaJuego(a, b, j);
        salas.put(a, s);
        salas.put(b, s);
    }

    public synchronized boolean existeSala(String a, String b) {
        SalaJuego s = salas.get(a);
        return s != null && s.contiene(b);
    }

    public synchronized SalaJuego obtenerSala(String jugador) {
        return salas.get(jugador);
    }

    public synchronized void eliminarSala(String a, String b) {
        salas.remove(a);
        salas.remove(b);
    }
}
