package ServidorMulti;

public class Juego {
    private final String jugadorA;
    private final String jugadorB;
    private String turno;
    private final char[] tablero = new char[9];
    private boolean iniciado = false;
    private boolean terminado = false;
    private String ganador = null;

    public Juego(String a, String b) {
        this.jugadorA = a;
        this.jugadorB = b;
        this.turno = Math.random() < 0.5 ? a : b;
        for (int i = 0; i < 9; i++) tablero[i] = ' ';
    }

    public void iniciar() { iniciado = true; }

    public String getJugadorA() { return jugadorA; }
    public String getJugadorB() { return jugadorB; }
    public String getTurno() { return turno; }
    public boolean estaTerminado() { return terminado; }

    public String getOponente(String jugador) {
        return jugador.equals(jugadorA) ? jugadorB : jugadorA;
    }

    public String realizarMovimiento(String jugador, int pos) {
        if (!iniciado) return "[gato] El juego no ha iniciado.";
        if (terminado) return "[gato] El juego ya ha terminado.";
        if (!jugador.equals(turno)) return "[gato] No es tu turno.";
        if (pos < 1 || pos > 9) return "[gato] Posicion invalida. Usa numeros del 1 al 9.";
        if (tablero[pos - 1] != ' ') return "[gato] Esa casilla ya esta ocupada.";

        char simbolo = jugador.equals(jugadorA) ? 'X' : 'O';
        tablero[pos - 1] = simbolo;

        if (verificarGanador(simbolo)) {
            terminado = true;
            ganador = jugador;
            return "[gato] Gano " + jugador + mostrarTablero();
        }

        if (tableroLleno()) {
            terminado = true;
            ganador = null;
            return "[gato] Empate" + mostrarTablero();
        }

        turno = getOponente(jugador);
        return "[gato] Movimiento realizado" + mostrarTablero() + "\nTurno de: " + turno;
    }

    public void rendirse(String jugador) {
        if (terminado) return;
        terminado = true;
        ganador = getOponente(jugador);
    }

    private boolean tableroLleno() {
        for (char c : tablero) if (c == ' ') return false;
        return true;
    }

    private boolean verificarGanador(char s) {
        int[][] w = {
            {0,1,2},{3,4,5},{6,7,8},
            {0,3,6},{1,4,7},{2,5,8},
            {0,4,8},{2,4,6}
        };
        for (int[] c : w)
            if (tablero[c[0]] == s && tablero[c[1]] == s && tablero[c[2]] == s)
                return true;
        return false;
    }

    public String mostrarTablero() {
        StringBuilder sb = new StringBuilder("\n");
        for (int i = 0; i < 9; i++) {
            char c = tablero[i];
            if (c == ' ') sb.append(i + 1);
            else sb.append(c);
            if ((i + 1) % 3 == 0) sb.append("\n");
            else sb.append(" | ");
        }
        return sb.toString();
    }
}
