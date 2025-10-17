package servidormulti;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryBlockStore implements BlockStore {
    private final ConcurrentHashMap<String, Set<String>> blocks = new ConcurrentHashMap<>();

    @Override
    public boolean block(String bloqueador, String bloqueado) {
        blocks.putIfAbsent(bloqueador, ConcurrentHashMap.newKeySet());
        return blocks.get(bloqueador).add(bloqueado);
    }

    @Override
    public boolean unblock(String bloqueador, String bloqueado) {
        Set<String> lista = blocks.get(bloqueador);
        if (lista == null) return false;
        return lista.remove(bloqueado);
    }

    @Override
    public boolean isBlocked(String bloqueador, String candidato) {
        Set<String> lista = blocks.get(bloqueador);
        return lista != null && lista.contains(candidato);
    }

    @Override
    public Set<String> blockedList(String bloqueador) {
        blocks.putIfAbsent(bloqueador, ConcurrentHashMap.newKeySet());
        return Set.copyOf(blocks.get(bloqueador));
    }
}
    