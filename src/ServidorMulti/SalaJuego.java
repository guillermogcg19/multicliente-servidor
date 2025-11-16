package ServidorMulti;

public class SalaJuego {
    private final String jugadorA;
    private final String jugadorB;
    private final Juego juego;

    public SalaJuego(String a, String b, Juego juego) {
        this.jugadorA = a;
        this.jugadorB = b;
        this.juego = juego;
    }

    public String getJugadorA() { return jugadorA; }
    public String getJugadorB() { return jugadorB; }
    public Juego getJuego()     { return juego; }

    public boolean contiene(String nombre) {
        return nombre.equals(jugadorA) || nombre.equals(jugadorB);
    }

    public String getOponente(String nombre) {
        return nombre.equals(jugadorA) ? jugadorB : jugadorA;
    }
}
