package ServidorMulti;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GestorJuegos {

    private final Map<String, SalaJuego> salas = new ConcurrentHashMap<>();

    public void crearSala(String jugadorA, String jugadorB) {
        Juego juego = new Juego(jugadorA, jugadorB);
        SalaJuego sala = new SalaJuego(jugadorA, jugadorB, juego);
        salas.put(jugadorA, sala);
        salas.put(jugadorB, sala);
    }

    public SalaJuego obtenerSala(String jugador) {
        return salas.get(jugador);
    }

    public boolean existeSala(String jugadorA, String jugadorB) {
        SalaJuego sala = salas.get(jugadorA);
        return sala != null && sala.contiene(jugadorB);
    }

    public void eliminarSala(String jugadorA, String jugadorB) {
        salas.remove(jugadorA);
        salas.remove(jugadorB);
    }
}
