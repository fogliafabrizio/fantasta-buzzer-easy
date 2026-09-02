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

    /**
     * Cerca nei quattro ruoli la voce di rosa di un calciatore e la restituisce, oppure
     * null se il calciatore non e' in questa rosa. Un calciatore non puo' comparire due
     * volte in una rosa ne' in due rose diverse, quindi la voce trovata e' al piu' una.
     */
    public VoceRosa trovaVoce(int idCalciatore) {
        for (List<VoceRosa> voci : rosa.values()) {
            for (VoceRosa v : voci) {
                if (v.idCalciatore() == idCalciatore) {
                    return v;
                }
            }
        }
        return null;
    }

    /**
     * Toglie dalla rosa la voce del calciatore e restituisce il prezzo che portava, oppure
     * null se il calciatore non era in rosa. E' l'inverso di {@link #acquista}: i crediti
     * risalgono da soli, perche' sono una formula sulla rosa e non un contatore a parte.
     */
    public Integer rimuovi(int idCalciatore) {
        for (List<VoceRosa> voci : rosa.values()) {
            Iterator<VoceRosa> it = voci.iterator();
            while (it.hasNext()) {
                VoceRosa v = it.next();
                if (v.idCalciatore() == idCalciatore) {
                    it.remove();
                    return v.prezzo();
                }
            }
        }
        return null;
    }
}
