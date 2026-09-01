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
 */
public record Snapshot(
        long sequenza,
        SnapshotLotto lotto,
        List<SnapshotPartecipante> partecipanti,
        List<Integer> calciatoriAssegnati,
        int creditiInCircolazione
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
}
