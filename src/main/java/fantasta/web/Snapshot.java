package fantasta.web;

import fantasta.asta.Ruolo;
import fantasta.asta.StatoLotto;
import fantasta.asta.VoceRosa;

import java.util.List;
import java.util.Map;

/**
 * @param creditiInCircolazione somma, su tutti i partecipanti, dei crediti residui piu'
 *        i prezzi gia' impegnati nella rosa. E' un invariante della lega: un'aggiudicazione
 *        sposta crediti dal residuo alla rosa e il totale non si muove. Serve proprio come
 *        spia: se cambia, dei crediti sono stati creati o distrutti.
 * @param annullabile la prossima aggiudicazione annullabile, o {@code null} se non ce n'e'
 *        nessuna. Il client <em>disegna</em> la disponibilita' dell'azione da questo campo,
 *        non la deduce da rose e lotti: la regola di quali aggiudicazioni siano annullabili
 *        sta solo sul server.
 */
public record Snapshot(
        long sequenza,
        SnapshotLotto lotto,
        List<SnapshotPartecipante> partecipanti,
        List<Integer> calciatoriAssegnati,
        int creditiInCircolazione,
        SnapshotAnnullabile annullabile
) {
    public record SnapshotLotto(
            int idLotto,
            int idCalciatore,
            StatoLotto stato,
            Integer offertaCorrente,
            String offerenteCorrente,
            int secondiResidui
    ) {
    }

    /**
     * Crediti e rosa sono proiezioni ricalcolate dal log a ogni snapshot: {@code crediti}
     * e' sempre {@code creditiTotali} meno la somma dei prezzi in rosa. La rosa porta gli
     * id dei calciatori, non i nomi: quelli li risolvono i client dal listone che hanno
     * gia' in locale.
     */
    public record SnapshotPartecipante(
            String nome,
            String codice,
            int creditiTotali,
            int crediti,
            Map<Ruolo, List<VoceRosa>> rosa
    ) {
    }

    /**
     * L'aggiudicazione che il banditore puo' annullare adesso, cioe' la cima della pila
     * degli annullabili incrociata con la proiezione corrente. {@code codicePartecipante}
     * e {@code importo} sono chi ha il calciatore in rosa <em>adesso</em> e a che prezzo,
     * non il vincitore e l'importo del lotto di allora: fra i due momenti puo' esserci
     * stata una rettifica, e sono questi i valori che l'annullamento rimette a posto.
     * {@code idLotto} torna indietro nel POST come guardia contro il doppio click.
     */
    public record SnapshotAnnullabile(
            int idLotto,
            int idCalciatore,
            String codicePartecipante,
            int importo
    ) {
    }
}
