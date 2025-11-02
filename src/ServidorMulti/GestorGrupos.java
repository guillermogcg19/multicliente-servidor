package ServidorMulti;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GestorGrupos {
    private final Map<String, Set<String>> grupos = new ConcurrentHashMap<>();

    public GestorGrupos() {
        grupos.put("Todos", ConcurrentHashMap.newKeySet());
    }

    public synchronized boolean crearGrupo(String nombre) {
        if (grupos.containsKey(nombre)) return false;
        grupos.put(nombre, ConcurrentHashMap.newKeySet());
        return true;
    }

    public synchronized boolean eliminarGrupo(String nombre) {
        if (nombre.equals("Todos")) return false;
        return grupos.remove(nombre) != null;
    }

    public synchronized boolean unirA(String grupo, String usuario) {
        if (!grupos.containsKey(grupo)) return false;
        grupos.get(grupo).add(usuario);
        return true;
    }

    public synchronized boolean salirDe(String grupo, String usuario) {
        if (!grupos.containsKey(grupo)) return false;
        grupos.get(grupo).remove(usuario);
        return true;
    }

    public synchronized Set<String> obtenerUsuarios(String grupo) {
        return grupos.getOrDefault(grupo, Collections.emptySet());
    }

    public boolean existe(String grupo) {
        return grupos.containsKey(grupo);
    }

    public Set<String> listaGrupos() {
        return grupos.keySet();
    }
}
