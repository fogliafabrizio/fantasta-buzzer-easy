package fantasta.web;

import fantasta.asta.Ruolo;
import fantasta.asta.StatoLotto;

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

    public record SnapshotPartecipante(
            String nome,
            String codice,
            int crediti,
            Map<Ruolo, List<Integer>> rosa
    ) {
    }
}
