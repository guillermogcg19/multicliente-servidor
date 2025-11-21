package ServidorMulti;

public class SalaJuego {

    private final String jugadorA;
    private final String jugadorB;
    private final Juego juego;
    
private boolean pendiente = true;

public boolean estaPendiente() {
    return pendiente;
}

public void iniciarPartida() {
    pendiente = false;
}

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
