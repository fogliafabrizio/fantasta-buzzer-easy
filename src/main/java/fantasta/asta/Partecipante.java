package fantasta.asta;

import java.util.*;

public class Partecipante {

    private final String nome;
    private final String codice;
    private int crediti;
    private final Map<Ruolo, List<Integer>> rosa;

    public Partecipante(String nome, String codice, int crediti) {
        this.nome = nome;
        this.codice = codice;
        this.crediti = crediti;
        this.rosa = new EnumMap<>(Ruolo.class);
        for (Ruolo r : Ruolo.values()) {
            this.rosa.put(r, new ArrayList<>());
        }
    }

    public String getNome() {
        return nome;
    }

    public String getCodice() {
        return codice;
    }

    public int getCrediti() {
        return crediti;
    }

    public Map<Ruolo, List<Integer>> getRosa() {
        return rosa;
    }

    public void scalaCrediti(int importo) {
        this.crediti -= importo;
    }

    public void aggiungiCalciatore(Ruolo ruolo, int idCalciatore) {
        rosa.get(ruolo).add(idCalciatore);
    }
}
