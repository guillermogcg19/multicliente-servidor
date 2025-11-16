package ServidorMulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;

public class UnCliente implements Runnable {

    private static final String TAG_SYS  = "[sistema] ";
    private static final String TAG_GAME = "[gato] ";
    private static final int LIMITE = 3;

    private final Socket socket;
    private final DataInputStream entrada;
    private final DataOutputStream salida;
    private final Database db;

    private String nombre;
    private boolean autenticado = false;
    private int mensajes = 0;
    private String grupoActual = "Todos";

    public UnCliente(Socket s, String nombreInicial) throws IOException {
        this.socket = s;
        this.entrada = new DataInputStream(s.getInputStream());
        this.salida = new DataOutputStream(s.getOutputStream());
        this.db = ServidorMulti.db;
        this.nombre = nombreInicial;

        db.unirseAGrupo("Todos", nombre);

        enviar(TAG_SYS + "Eres " + nombre + ". Solo puedes enviar " + LIMITE + " mensajes sin registrarte.");
        enviar(TAG_SYS + "Comandos disponibles:");
        enviar("REGISTER <usuario> <pass>");
        enviar("LOGIN <usuario> <pass>");
        enviar("CREARGRUPO <nombre>");
        enviar("ENTRAR <nombre>");
        enviar("SALIRGRUPO");
        enviar("GRUPOS");
        enviar("JUGAR <usuario>");
        enviar("ACEPTAR <usuario>");
        enviar("MOVER <1-9>");
        enviar("TABLERO");
        enviar("RENDIRSE");
        enviar("PARTIDAS");
        enviar("RANKING o RANKING <jugador1> <jugador2>");
    }

    @Override
    public void run() {
        try {
            while (true) {
                String msg = entrada.readUTF();
                if (msg == null) break;
                procesarMensaje(msg.trim());
            }
        } catch (IOException e) {
            System.err.println("Conexion cerrada: " + e.getMessage());
        } finally {
            desconectar();
        }
    }

    private void procesarMensaje(String msg) {
        String m = msg.toUpperCase();

        // registro / login
        if (m.startsWith("REGISTER ")) { registrar(msg); return; }
        if (m.startsWith("LOGIN "))    { login(msg); return; }

        // grupos
        if (m.startsWith("CREARGRUPO ")) { crearGrupo(msg); return; }
        if (m.startsWith("ENTRAR "))     { entrarGrupo(msg); return; }
        if (m.equals("SALIRGRUPO"))      { salirGrupo(); return; }
        if (m.equals("GRUPOS"))          { listarGrupos(); return; }

        // juego
        if (m.startsWith("JUGAR "))   { jugar(msg); return; }
        if (m.startsWith("ACEPTAR ")) { aceptar(msg); return; }
        if (m.startsWith("MOVER "))   { mover(msg); return; }
        if (m.equals("TABLERO"))      { mostrarTablero(); return; }
        if (m.equals("RENDIRSE"))     { rendirse(); return; }
        if (m.equals("PARTIDAS"))     { listarPartida(); return; }

        // ranking
        if (m.startsWith("RANKING"))  { mostrarRanking(msg); return; }

        // chat privado mientras hay partida
        SalaJuego sala = ServidorMulti.gestorJuegos.obtenerSala(nombre);
        if (sala != null) {
            String oponente = sala.getOponente(nombre);
            UnCliente cli = ServidorMulti.clientes.get(oponente);
            if (cli != null) {
                cli.enviar("[privado][" + nombre + "]: " + msg);
            }
            return;
        }

        // limite de mensajes si no esta logueado
        if (!autenticado && mensajes >= LIMITE) {
            enviar(TAG_SYS + "Has alcanzado el limite de mensajes. Registrate o inicia sesion.");
            return;
        }

        mensajes++;
        db.guardarMensaje(grupoActual, nombre, msg);
        for (UnCliente cli : ServidorMulti.clientes.values()) {
            if (cli == this) continue;
            if (!cli.grupoActual.equalsIgnoreCase(grupoActual)) continue;
            cli.enviar("[" + grupoActual + "] " + nombre + ": " + msg);
        }
    }

    // ===== registro y login =====

    private void registrar(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 3) {
            enviar(TAG_SYS + "Uso: REGISTER <usuario> <pass>");
            return;
        }
        autenticado = true;
        cambiarNombre(p[1]);
        db.unirseAGrupo("Todos", nombre);
        enviar(TAG_SYS + "Registro exitoso.");
    }

    private void login(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 3) {
            enviar(TAG_SYS + "Uso: LOGIN <usuario> <pass>");
            return;
        }
        if (ServidorMulti.clientes.containsKey(p[1])) {
            enviar(TAG_SYS + "Ese usuario ya esta conectado.");
            return;
        }
        autenticado = true;
        cambiarNombre(p[1]);
        db.unirseAGrupo("Todos", nombre);
        enviar(TAG_SYS + "Inicio de sesion correcto.");
    }

    // ===== grupos =====

    private void crearGrupo(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) {
            enviar(TAG_SYS + "Uso: CREARGRUPO <nombre>");
            return;
        }
        String g = p[1];
        if (db.crearGrupo(g)) {
            enviar(TAG_SYS + "Grupo creado: " + g);
        } else {
            enviar(TAG_SYS + "No se pudo crear el grupo.");
        }
    }

    private void entrarGrupo(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) {
            enviar(TAG_SYS + "Uso: ENTRAR <nombre>");
            return;
        }
        grupoActual = p[1];
        db.unirseAGrupo(grupoActual, nombre);
        List<String> nuevos = db.mensajesNoLeidos(grupoActual, nombre);
        enviar(TAG_SYS + "Entraste a " + grupoActual + ". Mensajes no vistos:");
        for (String m : nuevos) {
            enviar(m);
        }
    }

    private void salirGrupo() {
        if (grupoActual.equalsIgnoreCase("Todos")) {
            enviar(TAG_SYS + "No puedes salir del grupo Todos.");
            return;
        }
        if (db.salirDeGrupo(grupoActual, nombre)) {
            enviar(TAG_SYS + "Saliste de " + grupoActual + ". Ahora estas en Todos.");
            grupoActual = "Todos";
        } else {
            enviar(TAG_SYS + "No se pudo salir del grupo.");
        }
    }

    private void listarGrupos() {
        List<String> grupos = db.gruposDeUsuario(nombre);
        if (grupos.isEmpty()) {
            enviar(TAG_SYS + "No perteneces a ningun grupo.");
        } else {
            enviar(TAG_SYS + "Grupos: " + String.join(", ", grupos));
        }
    }

    // ===== juego del gato con salas =====

    private void jugar(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) {
            enviar(TAG_SYS + "Uso: JUGAR <usuario>");
            return;
        }
        String otro = p[1];
        if (otro.equals(nombre)) {
            enviar(TAG_SYS + "No puedes jugar contigo mismo.");
            return;
        }
        UnCliente cli = ServidorMulti.clientes.get(otro);
        if (cli == null) {
            enviar(TAG_SYS + "Usuario no conectado.");
            return;
        }
        if (ServidorMulti.gestorJuegos.existeSala(nombre, otro)) {
            enviar(TAG_SYS + "Ya hay una partida con ese usuario.");
            return;
        }

        ServidorMulti.gestorJuegos.crearSala(nombre, otro);
        cli.enviar(TAG_GAME + nombre + " te invito a jugar. Escribe: ACEPTAR " + nombre);
        enviar(TAG_GAME + "Invitacion enviada a " + otro);
    }

    private void aceptar(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) {
            enviar(TAG_SYS + "Uso: ACEPTAR <usuario>");
            return;
        }
        String otro = p[1];
        if (!ServidorMulti.gestorJuegos.existeSala(nombre, otro)) {
            enviar(TAG_SYS + "No hay invitacion.");
            return;
        }

        SalaJuego sala = ServidorMulti.gestorJuegos.obtenerSala(nombre);
        if (sala == null || !sala.contiene(otro)) {
            enviar(TAG_SYS + "No hay invitacion.");
            return;
        }

        Juego j = sala.getJuego();
        UnCliente cli = ServidorMulti.clientes.get(otro);

        enviar(TAG_GAME + "Partida iniciada. Empieza: " + j.getTurno());
        if (cli != null) {
            cli.enviar(TAG_GAME + "Partida iniciada. Empieza: " + j.getTurno());
        }
        enviar(j.mostrarTablero());
        if (cli != null) {
            cli.enviar(j.mostrarTablero());
        }
    }

    private void mover(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length < 2) {
            enviar(TAG_SYS + "Uso: MOVER <1-9>");
            return;
        }
        int pos;
        try {
            pos = Integer.parseInt(p[1]);
        } catch (NumberFormatException e) {
            enviar(TAG_SYS + "Posicion invalida.");
            return;
        }

        SalaJuego sala = ServidorMulti.gestorJuegos.obtenerSala(nombre);
        if (sala == null) {
            enviar(TAG_SYS + "No tienes partida activa.");
            return;
        }

        Juego j = sala.getJuego();
        String res = j.realizarMovimiento(nombre, pos);
        String otro = sala.getOponente(nombre);
        UnCliente cli = ServidorMulti.clientes.get(otro);

        enviar(TAG_GAME + res);
        if (cli != null) cli.enviar(TAG_GAME + res);

        if (j.estaTerminado()) {
            ServidorMulti.gestorJuegos.eliminarSala(nombre, otro);
        }
    }

    private void mostrarTablero() {
        SalaJuego sala = ServidorMulti.gestorJuegos.obtenerSala(nombre);
        if (sala == null) {
            enviar(TAG_SYS + "No hay partida activa.");
            return;
        }
        enviar(TAG_GAME + sala.getJuego().mostrarTablero());
    }

    private void rendirse() {
        SalaJuego sala = ServidorMulti.gestorJuegos.obtenerSala(nombre);
        if (sala == null) {
            enviar(TAG_SYS + "No tienes partida activa.");
            return;
        }
        Juego j = sala.getJuego();
        j.rendirse(nombre);

        String otro = sala.getOponente(nombre);
        UnCliente cli = ServidorMulti.clientes.get(otro);
        if (cli != null) {
            cli.enviar(TAG_GAME + "Tu oponente se rindio. Ganaste.");
        }
        enviar(TAG_GAME + "Te rendiste.");
        ServidorMulti.gestorJuegos.eliminarSala(nombre, otro);
    }

    private void listarPartida() {
        SalaJuego sala = ServidorMulti.gestorJuegos.obtenerSala(nombre);
        if (sala == null) {
            enviar(TAG_GAME + "No tienes partidas activas.");
            return;
        }
        Juego j = sala.getJuego();
        String otro = sala.getOponente(nombre);
        String estado = j.estaTerminado() ? "Finalizada" : "En curso";
        enviar(TAG_GAME + "Contra " + otro + " - " + estado + " - Turno: " + j.getTurno());
    }

    // ===== ranking =====

    private void mostrarRanking(String msg) {
        String[] p = msg.split("\\s+");
        if (p.length == 1) {
            ServidorMulti.db.mostrarRankingGeneral(this);
        } else if (p.length == 3) {
            ServidorMulti.db.mostrarComparacion(this, p[1], p[2]);
        } else {
            enviar(TAG_SYS + "Uso: RANKING o RANKING <jugador1> <jugador2>");
        }
    }

    // ===== utilidades =====

    private void cambiarNombre(String nuevo) {
        ServidorMulti.clientes.remove(nombre);
        nombre = nuevo;
        ServidorMulti.clientes.put(nombre, this);
    }

    private void desconectar() {
        SalaJuego sala = ServidorMulti.gestorJuegos.obtenerSala(nombre);
        if (sala != null) {
            Juego j = sala.getJuego();
            j.rendirse(nombre);
            String otro = sala.getOponente(nombre);
            UnCliente cli = ServidorMulti.clientes.get(otro);
            if (cli != null) {
                cli.enviar(TAG_GAME + "El jugador " + nombre + " se desconecto. Ganaste por abandono.");
            }
            ServidorMulti.gestorJuegos.eliminarSala(nombre, otro);
        }

        ServidorMulti.clientes.remove(nombre);
        try {
            socket.close();
        } catch (IOException ignore) {
        }
    }

    void enviar(String msg) {
        try {
            salida.writeUTF(msg);
        } catch (IOException ignore) {
        }
    }
}
