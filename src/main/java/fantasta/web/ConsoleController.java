package fantasta.web;

import fantasta.asta.Asta;
import fantasta.asta.TipoAsta;
import fantasta.eventi.AstaCreata;
import fantasta.eventi.AssegnazioneIniziale;
import fantasta.listone.Calciatore;
import fantasta.listone.ImportListone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

@RestController
public class ConsoleController {

    private static final Logger log = LoggerFactory.getLogger(ConsoleController.class);

    private final Asta asta;
    private final SseController sseController;

    public ConsoleController(Asta asta, SseController sseController) {
        this.asta = asta;
        this.sseController = sseController;
    }

    @PostMapping("/api/console/analizza-listone")
    public ResponseEntity<?> analizzaListone(@RequestParam("file") MultipartFile file) {
        if (asta.isAttiva()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("errore", "Un'asta e' gia' in corso"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("errore", "Il file e' vuoto"));
        }

        String nomeOriginale = file.getOriginalFilename();
        if (nomeOriginale == null || !nomeOriginale.endsWith(".xlsx")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("errore", "Il file deve essere in formato xlsx"));
        }

        Path dir = Path.of(asta.getDirectoryDati());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("Impossibile creare la directory dati {}: {}", dir, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("errore", "Impossibile creare la directory dati"));
        }

        String nomeFile = "listone-" + LocalDate.now() + "-" + nomeOriginale;
        Path destinazione = dir.resolve(nomeFile);

        try {
            file.transferTo(destinazione.toFile());
            log.info("File listone salvato: {}", destinazione);
        } catch (IOException e) {
            log.error("Salvataggio file listone fallito su {}: {}", destinazione, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("errore", "Salvataggio del file fallito: " + e.getMessage()));
        }

        ImportListone analisi;
        try {
            analisi = ImportListone.leggiCompleto(destinazione);
        } catch (ImportListone.FormatoListoneException e) {
            log.warn("Formato listone non valido: {}", e.getMessage());
            try { Files.deleteIfExists(destinazione); } catch (IOException ignored) {}
            return ResponseEntity.badRequest()
                    .body(Map.of("errore", e.getMessage()));
        } catch (Exception e) {
            log.error("Errore nell'analisi del listone: {}", e.getMessage());
            try { Files.deleteIfExists(destinazione); } catch (IOException ignored) {}
            return ResponseEntity.internalServerError()
                    .body(Map.of("errore", "Errore nell'analisi del file: " + e.getMessage()));
        }

        asta.setUltimaAnalisi(analisi);
        asta.setNomeFileListoneSalvato(nomeFile);

        Map<String, Object> risposta = new LinkedHashMap<>();
        risposta.put("tipoAsta", analisi.isRiparazione() ? "RIPARAZIONE" : "INIZIALE");
        risposta.put("calciatori", analisi.getCalciatori().size());
        risposta.put("fuoriLista", analisi.contaFuoriLista());

        if (analisi.isRiparazione()) {
            risposta.put("partecipanti", new ArrayList<>(analisi.getNomiPartecipanti()));
        }

        log.info("Listone analizzato: {} calciatori, tipo={}, file={}",
                analisi.getCalciatori().size(),
                analisi.isRiparazione() ? "RIPARAZIONE" : "INIZIALE",
                nomeFile);

        return ResponseEntity.ok(risposta);
    }

    @PostMapping("/api/console/crea-asta")
    public ResponseEntity<?> creaAsta(@RequestBody Map<String, Object> body) {
        if (asta.isAttiva()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("errore", "Un'asta e' gia' in corso"));
        }

        ImportListone analisi = asta.getUltimaAnalisi();
        String nomeFile = asta.getNomeFileListoneSalvato();
        if (analisi == null || nomeFile == null) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(Map.of("errore", "Nessun file analizzato. Chiamare prima analizza-listone"));
        }

        String nomeAsta = (String) body.get("nomeAsta");
        if (nomeAsta == null || nomeAsta.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("errore", "Il campo nomeAsta e' obbligatorio"));
        }

        Number durataRaw = (Number) body.get("durataCountdown");
        if (durataRaw == null || durataRaw.intValue() < 1) {
            return ResponseEntity.badRequest()
                    .body(Map.of("errore", "Il campo durataCountdown deve essere un intero >= 1"));
        }
        int durataCountdown = durataRaw.intValue();

        Boolean banditorePartecipa = (Boolean) body.getOrDefault("banditorePartecipa", false);

        TipoAsta tipoAsta = analisi.isRiparazione() ? TipoAsta.RIPARAZIONE : TipoAsta.INIZIALE;

        List<AstaCreata.PartecipanteEvento> partecipantiEvento = new ArrayList<>();
        List<AssegnazioneIniziale> assegnazioniIniziali = new ArrayList<>();

        if (tipoAsta == TipoAsta.INIZIALE) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> listaPartecipanti = (List<Map<String, Object>>) body.get("partecipanti");
            if (listaPartecipanti == null || listaPartecipanti.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("errore", "La lista partecipanti e' obbligatoria per l'asta iniziale"));
            }

            for (Map<String, Object> p : listaPartecipanti) {
                String nome = (String) p.get("nome");
                Number creditiRaw = (Number) p.get("crediti");
                if (nome == null || nome.isBlank()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("errore", "Ogni partecipante deve avere un nome"));
                }
                if (creditiRaw == null || creditiRaw.intValue() < 1) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("errore", "I crediti di " + nome + " devono essere un intero >= 1"));
                }
                String codice = asta.generaCodice();
                partecipantiEvento.add(new AstaCreata.PartecipanteEvento(nome, codice, creditiRaw.intValue()));
            }
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> creditiResidui = (Map<String, Object>) body.get("creditiResidui");
            if (creditiResidui == null || creditiResidui.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("errore", "Il campo creditiResidui e' obbligatorio per l'asta di riparazione"));
            }

            Map<String, String> codiciPerNome = new LinkedHashMap<>();
            for (String nomePartecipante : analisi.getNomiPartecipanti()) {
                Object creditiResiduiRaw = creditiResidui.get(nomePartecipante);
                if (creditiResiduiRaw == null) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("errore", "Crediti residui mancanti per " + nomePartecipante));
                }
                int residui = ((Number) creditiResiduiRaw).intValue();
                int sommaCosti = analisi.calcolaSommaCosti(nomePartecipante);
                int creditiTotali = residui + sommaCosti;
                String codice = asta.generaCodice();
                codiciPerNome.put(nomePartecipante, codice);
                partecipantiEvento.add(new AstaCreata.PartecipanteEvento(nomePartecipante, codice, creditiTotali));
            }

            long seqBase = 1;
            for (Map.Entry<String, List<ImportListone.Assegnazione>> entry : analisi.getAssegnazioniPerPartecipante().entrySet()) {
                String codice = codiciPerNome.get(entry.getKey());
                for (ImportListone.Assegnazione a : entry.getValue()) {
                    assegnazioniIniziali.add(new AssegnazioneIniziale(0, a.idCalciatore(), codice, a.costo()));
                }
            }
        }

        asta.creaAsta(nomeAsta, tipoAsta, durataCountdown, banditorePartecipa, nomeFile,
                analisi.getCalciatori(), partecipantiEvento, assegnazioniIniziali);

        asta.setUltimaAnalisi(null);
        asta.setNomeFileListoneSalvato(null);

        sseController.broadcast();

        List<Map<String, String>> rispostaPartecipanti = partecipantiEvento.stream()
                .map(p -> Map.of("nome", p.getNome(), "codice", p.getCodice()))
                .toList();

        log.info("Asta '{}' creata con {} partecipanti", nomeAsta, partecipantiEvento.size());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("partecipanti", rispostaPartecipanti));
    }
}
