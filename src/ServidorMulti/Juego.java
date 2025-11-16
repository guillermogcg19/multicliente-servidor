package ServidorMulti;

import java.util.Random;

public class Juego {
    private final String jugadorA;
    private final String jugadorB;
    private final char[] tablero = {'1','2','3','4','5','6','7','8','9'};
    private String turno;
    private boolean terminado = false;

    public Juego(String a, String b) {
        this.jugadorA = a;
        this.jugadorB = b;
        this.turno = new Random().nextBoolean() ? a : b;
    }

    public String getJugadorA() { return jugadorA; }
    public String getJugadorB() { return jugadorB; }
    public String getTurno()    { return turno; }
    public boolean estaTerminado() { return terminado; }

    public String getOponente(String j) {
        return j.equals(jugadorA) ? jugadorB : jugadorA;
    }

    public String mostrarTablero() {
        return tablero[0] + " | " + tablero[1] + " | " + tablero[2] + "\n" +
               tablero[3] + " | " + tablero[4] + " | " + tablero[5] + "\n" +
               tablero[6] + " | " + tablero[7] + " | " + tablero[8];
    }

    public synchronized String realizarMovimiento(String jugador, int pos) {
        if (terminado) return "El juego ya termino.";
        if (!jugador.equals(turno)) return "No es tu turno.";
        if (pos < 1 || pos > 9) return "Posicion invalida.";
        if (tablero[pos - 1] == 'X' || tablero[pos - 1] == 'O') return "Posicion ocupada.";

        char simbolo = jugador.equals(jugadorA) ? 'X' : 'O';
        tablero[pos - 1] = simbolo;

        if (gana(simbolo)) {
            terminado = true;
            ServidorMulti.db.actualizarResultadoVictoria(jugador, getOponente(jugador));
            return "Gano " + jugador + "\n" + mostrarTablero();
        }

        if (lleno()) {
            terminado = true;
            ServidorMulti.db.actualizarResultadoEmpate(jugadorA, jugadorB);
            return "Empate.\n" + mostrarTablero();
        }

        turno = getOponente(jugador);
        return "Movimiento realizado\n" + mostrarTablero() + "\nTurno: " + turno;
    }

    public synchronized void rendirse(String jugador) {
        if (terminado) return;
        terminado = true;
        String ganador = getOponente(jugador);
        ServidorMulti.db.actualizarResultadoVictoria(ganador, jugador);
    }

    private boolean lleno() {
        for (char c : tablero) {
            if (c != 'X' && c != 'O') return false;
        }
        return true;
    }

    private boolean gana(char s) {
        int[][] w = {
            {0,1,2},{3,4,5},{6,7,8},
            {0,3,6},{1,4,7},{2,5,8},
            {0,4,8},{2,4,6}
        };
        for (int[] c : w) {
            if (tablero[c[0]] == s && tablero[c[1]] == s && tablero[c[2]] == s) {
                return true;
            }
        }
        return false;
    }
}
