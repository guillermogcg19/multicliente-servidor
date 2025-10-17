package servidormulti;

import java.util.Set;

public interface BlockStore {
    boolean block(String bloqueador, String bloqueado);
    boolean unblock(String bloqueador, String bloqueado);
    boolean isBlocked(String bloqueador, String candidato);
    Set<String> blockedList(String bloqueador);
}
