package fantasta.web;

import fantasta.asta.Ruolo;
import fantasta.asta.StatoLotto;
import fantasta.asta.VoceRosa;

import java.util.List;
import java.util.Map;

public record Snapshot(
        long sequenza,
        SnapshotLotto lotto,
        List<SnapshotPartecipante> partecipanti,
        List<Integer> calciatoriAssegnati
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
