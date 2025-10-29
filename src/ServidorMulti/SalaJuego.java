package ServidorMulti;

public class SalaJuego {
    private final String jugadorA;
    private final String jugadorB;
    private final Juego juego;

    public SalaJuego(String a, String b, Juego j) {
        this.jugadorA = a;
        this.jugadorB = b;
        this.juego = j;
    }

    public boolean contiene(String nombre) { return jugadorA.equals(nombre) || jugadorB.equals(nombre); }
    public String getOponente(String nombre) { return jugadorA.equals(nombre) ? jugadorB : jugadorA; }
    public Juego getJuego() { return juego; }
}
