package fantasta.asta;

import java.util.*;

/**
 * Proiezione di un partecipante ricostruita dal log. Nulla qui viene mutato "a mano":
 * l'unico stato accumulato e' la rosa, che cresce di una voce per ogni evento di
 * acquisto. I crediti residui non sono un campo ma una formula sola, valida sia per
 * l'asta iniziale sia per la riparazione:
 *
 *     crediti residui = crediti totali - somma dei prezzi pagati
 *
 * Le assegnazioni pre-esistenti della riparazione (ASSEGNAZIONE_INIZIALE) e le
 * aggiudicazioni della serata (LOTTO_AGGIUDICATO) entrano nella rosa allo stesso modo,
 * quindi la formula non ha casi speciali.
 */
public class Partecipante {

    private final String nome;
    private final String codice;
    private final int creditiTotali;
    private final Map<Ruolo, List<VoceRosa>> rosa;

    public Partecipante(String nome, String codice, int creditiTotali) {
        this.nome = nome;
        this.codice = codice;
        this.creditiTotali = creditiTotali;
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

    public int getCreditiTotali() {
        return creditiTotali;
    }

    /** Rosa per ruolo, sempre con tutte e quattro le chiavi (P, D, C, A), anche vuote. */
    public Map<Ruolo, List<VoceRosa>> getRosa() {
        return rosa;
    }

    /** Somma dei prezzi pagati su tutta la rosa. */
    public int getCreditiSpesi() {
        int spesi = 0;
        for (List<VoceRosa> voci : rosa.values()) {
            for (VoceRosa v : voci) {
                spesi += v.prezzo();
            }
        }
        return spesi;
    }

    /** Crediti residui: proiezione, mai un campo persistito. */
    public int getCrediti() {
        return creditiTotali - getCreditiSpesi();
    }

    /** Registra un acquisto nella rosa. Unico punto da cui la proiezione cresce. */
    public void acquista(Ruolo ruolo, int idCalciatore, int prezzo) {
        rosa.get(ruolo).add(new VoceRosa(idCalciatore, prezzo));
    }
}
