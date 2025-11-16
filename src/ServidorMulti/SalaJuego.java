package ServidorMulti;

public class SalaJuego {

    private final String jugadorA;
    private final String jugadorB;
    private final Juego juego;

    public SalaJuego(String jugadorA, String jugadorB, Juego juego) {
        this.jugadorA = jugadorA;
        this.jugadorB = jugadorB;
        this.juego = juego;
    }

    public boolean contiene(String jugador) {
        return jugadorA.equals(jugador) || jugadorB.equals(jugador);
    }

    public String getOponente(String jugador) {
        return jugadorA.equals(jugador) ? jugadorB : jugadorA;
    }

    public Juego getJuego() {
        return juego;
    }
}
