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

        Esito esito = asta.registraOfferta(idLotto.intValue(), codice, importo);
        if (!esito.accettata()) {
            log.info("Offerta rifiutata: lotto {}, codice {}, importo {} -> {}",
                    idLotto.intValue(), codice, importoRaw, esito.motivo());
            return ResponseEntity.status(esito.statusHttp())
                    .body(Map.of("motivo", esito.motivo()));
        }

        sseController.broadcast();
        return ResponseEntity.ok(Map.of("stato", "accettata"));
    }
}
