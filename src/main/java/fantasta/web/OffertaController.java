package fantasta.web;

import fantasta.asta.Asta;
import fantasta.asta.Esito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Ricezione delle offerte dai telefoni. Il client invia sempre l'importo assoluto
 * gia' calcolato (i pulsanti +1/+2/+5/+10 sono risolti dal telefono). Ogni offerta
 * porta l'idLotto: la validazione nel dominio la rifiuta se non corrisponde al lotto
 * aperto. Il motivo del rifiuto torna al telefono nel campo "motivo".
 * <p>
 * I soli pulsanti rapidi portano anche "offertaBase", cioe' l'offerta corrente su cui
 * il pulsante era stato disegnato: e' il campo su cui il dominio verifica che nel
 * frattempo l'offerta non sia cambiata. Il campo e' opzionale, e un client che non lo
 * invia si comporta esattamente come prima.
 */
@RestController
public class OffertaController {

    private static final Logger log = LoggerFactory.getLogger(OffertaController.class);

    private final Asta asta;
    private final SseController sseController;

    public OffertaController(Asta asta, SseController sseController) {
        this.asta = asta;
        this.sseController = sseController;
    }

    @PostMapping("/api/offerta")
    public ResponseEntity<?> offri(@RequestBody Map<String, Object> body) {
        Object idLottoRaw = body.get("idLotto");
        Object codiceRaw = body.get("codicePartecipante");
        Object importoRaw = body.get("importo");
        Object offertaBaseRaw = body.get("offertaBase");

        if (!(idLottoRaw instanceof Number idLotto)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("motivo", "richiesta non valida: idLotto mancante"));
        }
        if (!(codiceRaw instanceof String codice) || codice.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("motivo", "richiesta non valida: codice partecipante mancante"));
        }

        // Importo lasciato grezzo: null se assente o non numerico, cosi' la
        // validazione nel dominio lo rifiuta con "importo non valido".
        Number importo = (importoRaw instanceof Number n) ? n : null;

        // offertaBase e' opzionale e lasciata grezza allo stesso modo: assente o non
        // numerica significa importo digitato liberamente, quindi nessuna guardia. E'
        // il dominio a decidere cosa farne, il controller non interpreta.
        Integer offertaBase = (offertaBaseRaw instanceof Number b) ? b.intValue() : null;

        Esito esito = asta.registraOfferta(idLotto.intValue(), codice, importo, offertaBase);
        if (!esito.accettata()) {
            log.info("Offerta rifiutata: lotto {}, codice {}, importo {}, offerta base {} -> {}",
                    idLotto.intValue(), codice, importoRaw, offertaBaseRaw, esito.motivo());
            return ResponseEntity.status(esito.statusHttp())
                    .body(Map.of("motivo", esito.motivo()));
        }

        sseController.broadcast();
        return ResponseEntity.ok(Map.of("stato", "accettata"));
    }
}
