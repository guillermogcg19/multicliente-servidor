package ServidorMulti;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GestorJuegos {
    private final Map<String, SalaJuego> salas = new ConcurrentHashMap<>();

    public void crearSala(String a, String b) {
        Juego j = new Juego(a, b);
        SalaJuego s = new SalaJuego(a, b, j);
        salas.put(a, s);
        salas.put(b, s);
    }

    public SalaJuego obtenerSala(String jugador) { return salas.get(jugador); }

    public boolean existeSala(String a, String b) {
        SalaJuego s = salas.get(a);
        return s != null && s.contiene(b);
    }

    public void eliminarSala(String a, String b) {
        salas.remove(a);
        salas.remove(b);
    }
}
