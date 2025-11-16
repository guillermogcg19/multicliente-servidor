package ServidorMulti;

import java.util.Random;

public class Juego {
    private final String jugadorX;
    private final String jugadorO;
    private char[] tablero = {'1','2','3','4','5','6','7','8','9'};
    private String turno;
    private boolean terminado = false;

    public Juego(String a, String b) {
        if (new Random().nextBoolean()) {
            jugadorX = a;
            jugadorO = b;
        } else {
            jugadorX = b;
            jugadorO = a;
        }
        turno = jugadorX; // X empieza
    }

    public String getTurno() { return turno; }
    public boolean estaTerminado() { return terminado; }
    public String getOponente(String j) { return j.equals(jugadorX) ? jugadorO : jugadorX; }
    public String getJugadorX(){ return jugadorX; }
    public String getJugadorO(){ return jugadorO; }

    public String mostrarTablero() {
        return tablero[0]+" | "+tablero[1]+" | "+tablero[2]+"\n"+
               tablero[3]+" | "+tablero[4]+" | "+tablero[5]+"\n"+
               tablero[6]+" | "+tablero[7]+" | "+tablero[8];
    }

    public String realizarMovimiento(String jugador, int pos) {
        if (terminado) return "La partida ya termino.";
        if (!jugador.equals(turno)) return "No es tu turno.";
        if (pos < 1 || pos > 9) return "Posicion fuera de rango (1-9).";
        if (tablero[pos-1]=='X' || tablero[pos-1]=='O') return "Esa posicion ya esta ocupada.";

        char simbolo = jugador.equals(jugadorX) ? 'X' : 'O';
        tablero[pos - 1] = simbolo;

        if (gana(simbolo)) {
            terminado = true;
            ServidorMulti.db.actualizarResultadoVictoria(jugador, getOponente(jugador));
            return "Ganaste!\n" + mostrarTablero();
        }

        if (lleno()) {
            terminado = true;
            ServidorMulti.db.actualizarResultadoEmpate(jugadorX, jugadorO);
            return "Empate.\n" + mostrarTablero();
        }

        turno = getOponente(jugador);
        return mostrarTablero() + "\nTurno de " + turno;
    }

    public void rendirse(String jugador) {
        if (terminado) return;
        terminado = true;
        String ganador = getOponente(jugador);
        ServidorMulti.db.actualizarResultadoVictoria(ganador, jugador);
    }

    private boolean lleno() {
        for (char c : tablero) {
            if (c!='X' && c!='O') return false;
        }
        return true;
    }

    private boolean gana(char s) {
        int[][] w = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for (int[] c : w) if (tablero[c[0]]==s && tablero[c[1]]==s && tablero[c[2]]==s)
            return true;
        return false;
    }
}
