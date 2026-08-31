package fantasta.listone;

import fantasta.asta.Ruolo;

public record Calciatore(int id, String nome, Ruolo ruolo, String squadra, int quotazione, boolean fuoriLista) {
}
